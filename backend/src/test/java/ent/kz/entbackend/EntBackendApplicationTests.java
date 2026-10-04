package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EntBackendApplicationTests {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
    "postgres:17-alpine"
  );

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add(
      "app.jwt.secret",
      () -> "integration-test-signing-key-only-01234567890123456789"
    );
    r.add("app.jwt.expiration-ms", () -> 60000);
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  record Account(String token, String email, String id) {}

  Account register() throws Exception {
    String email = "test-" + UUID.randomUUID() + "@example.org";
    JsonNode n = call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        email,
        "password",
        "Test-password-2026",
        "firstName",
        "Айдана",
        "lastName",
        "Тест",
        "language",
        "ru"
      ),
      200
    );
    assertEquals("STUDENT", n.path("user").path("role").asText());
    assertFalse(n.toString().contains("passwordHash"));
    return new Account(
      n.path("token").asText(),
      email,
      n.path("user").path("id").asText()
    );
  }

  JsonNode call(
    String method,
    String path,
    String token,
    Object payload,
    int expected
  ) throws Exception {
    var req = switch (method) {
      case "POST" -> post(path);
      case "PUT" -> put(path);
      case "PATCH" -> patch(path);
      case "DELETE" -> delete(path);
      default -> get(path);
    };
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (payload != null) req
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsBytes(payload));
    var res = mvc.perform(req).andReturn().getResponse();
    assertEquals(
      expected,
      res.getStatus(),
      method + " " + path + " " + res.getContentAsString()
    );
    return res.getContentAsByteArray().length == 0
      ? json.nullNode()
      : json.readTree(res.getContentAsByteArray());
  }

  String topic() {
    return db.queryForObject(
      "select id::text from topics where title_ru='Производная'",
      String.class
    );
  }

  String start(String token) throws Exception {
    return call(
      "POST",
      "/api/tests/start",
      token,
      Map.of("topicId", topic()),
      200
    )
      .path("sessionId")
      .asText();
  }

  @Test
  void authenticationAndValidation() throws Exception {
    Account a = register();
    assertEquals(
      a.id,
      call("GET", "/api/auth/me", a.token, null, 200).path("id").asText()
    );
    call(
      "POST",
      "/api/auth/login",
      null,
      Map.of("email", a.email.toUpperCase(), "password", "Test-password-2026"),
      200
    );
    call(
      "POST",
      "/api/auth/login",
      null,
      Map.of("email", a.email, "password", "incorrect"),
      401
    );
    call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        a.email,
        "password",
        "Test-password-2026",
        "firstName",
        "A",
        "lastName",
        "B",
        "language",
        "ru"
      ),
      409
    );
    call(
      "POST",
      "/api/auth/register",
      null,
      Map.of("email", "bad", "password", "short"),
      400
    );
    call(
      "PATCH",
      "/api/auth/me/language",
      a.token,
      Map.of("language", "kz"),
      200
    );
    assertEquals(
      "kz",
      call("GET", "/api/auth/me", a.token, null, 200).path("language").asText()
    );
    call(
      "PATCH",
      "/api/auth/me/language",
      a.token,
      Map.of("language", "en"),
      400
    );
    call(
      "POST",
      "/api/auth/login",
      null,
      Map.of("email", a.email, "password", "я".repeat(40)),
      400
    );
    call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        "long@example.org",
        "password",
        "я".repeat(40),
        "firstName",
        "A",
        "lastName",
        "B",
        "language",
        "ru"
      ),
      400
    );
    call("GET", "/api/unknown", a.token, null, 404);
    db.update("update users set is_active=false where id=?::uuid", a.id);
    call("GET", "/api/auth/me", a.token, null, 401);
    call(
      "POST",
      "/api/auth/login",
      null,
      Map.of("email", a.email, "password", "Test-password-2026"),
      401
    );
  }

  @Test
  void authorizationAndJwt() throws Exception {
    Account a = register();
    for (String p : List.of(
      "/api/auth/me",
      "/api/subjects",
      "/api/statistics/me",
      "/api/admin/subjects"
    ))
      call("GET", p, null, null, 401);
    call("GET", "/api/admin/subjects", a.token, null, 403);
    call("GET", "/api/auth/me", "invalid.token", null, 401);
    call(
      "GET",
      "/api/auth/me",
      a.token.substring(0, a.token.length() - 8) + "invalid!",
      null,
      401
    );
    String expired = io.jsonwebtoken.Jwts.builder()
      .subject(a.email)
      .claim("userId", a.id)
      .expiration(new Date(0))
      .signWith(
        io.jsonwebtoken.security.Keys.hmacShaKeyFor(
          "integration-test-signing-key-only-01234567890123456789".getBytes()
        )
      )
      .compact();
    call("GET", "/api/auth/me", expired, null, 401);
    assertEquals(
      0,
      db.queryForObject(
        "select count(*) from users where role='ADMIN'",
        Integer.class
      )
    );
  }

  @Test
  void contentAndCompleteLearningFlow() throws Exception {
    Account a = register();
    JsonNode subjects = call("GET", "/api/subjects", a.token, null, 200);
    assertTrue(subjects.size() >= 3);
    String subject = db.queryForObject(
      "select subject_id::text from topics where id=?::uuid",
      String.class,
      topic()
    );
    JsonNode topics = call(
      "GET",
      "/api/topics/subject/" + subject,
      a.token,
      null,
      200
    );
    assertTrue(topics.get(0).has("questionCount"));
    assertTrue(
      call("GET", "/api/theories/topic/" + topic(), a.token, null, 200).size() >
        0
    );
    JsonNode preview = call(
      "GET",
      "/api/questions/topic/" + topic(),
      a.token,
      null,
      200
    );
    assertFalse(preview.toString().contains("correctOptionId"));
    assertFalse(preview.toString().contains("explanationRu"));
    assertEquals(
      0,
      call("GET", "/api/statistics/me", a.token, null, 200)
        .path("testsTaken")
        .asInt()
    );
    String id = start(a.token);
    call("GET", "/api/tests/" + id + "/results", a.token, null, 409);
    JsonNode state = call("GET", "/api/tests/" + id, a.token, null, 200);
    int count = state.path("totalQuestions").asInt();
    for (int i = 0; i < count; i++) {
      JsonNode q = call(
        "GET",
        "/api/tests/" + id + "/questions/" + i,
        a.token,
        null,
        200
      );
      assertFalse(q.has("correctOptionId"));
      String answer = db.queryForObject(
        "select correct_option_id from questions where id=?::uuid",
        String.class,
        q.path("questionId").asText()
      );
      var body = Map.of(
        "questionId",
        q.path("questionId").asText(),
        "selectedOptionId",
        answer,
        "timeSpentSecs",
        4
      );
      assertFalse(
        call("POST", "/api/tests/" + id + "/answers", a.token, body, 200).has(
          "isCorrect"
        )
      );
      call("POST", "/api/tests/" + id + "/answers", a.token, body, 200); // lost response retry
    }
    assertEquals(
      count,
      call("GET", "/api/tests/" + id, a.token, null, 200)
        .path("answers")
        .size()
    );
    JsonNode finish = call(
      "POST",
      "/api/tests/" + id + "/finish",
      a.token,
      null,
      200
    );
    assertEquals(100, finish.path("score").asInt());
    assertEquals(
      finish,
      call("POST", "/api/tests/" + id + "/finish", a.token, null, 200)
    );
    JsonNode result = call(
      "GET",
      "/api/tests/" + id + "/results",
      a.token,
      null,
      200
    );
    assertEquals(count, result.path("answers").size());
    assertTrue(result.path("answers").get(0).has("explanationKz"));
    JsonNode stats = call("GET", "/api/statistics/me", a.token, null, 200);
    assertEquals(1, stats.path("testsTaken").asInt());
    assertEquals(100, stats.path("averageScore").asInt());
    assertEquals(1, stats.path("subjects").size());
    assertEquals(0, stats.path("activeAttempts").size());
  }

  @Test
  void sessionOwnershipInvalidInputAndEarlyFinish() throws Exception {
    Account a = register(),
      other = register();
    String id = start(a.token);
    JsonNode q = call(
      "GET",
      "/api/tests/" + id + "/questions/0",
      a.token,
      null,
      200
    );
    var body = Map.of(
      "questionId",
      q.path("questionId").asText(),
      "selectedOptionId",
      "invalid"
    );
    call("POST", "/api/tests/" + id + "/answers", a.token, body, 400);
    call(
      "POST",
      "/api/tests/" + id + "/answers",
      a.token,
      Map.of(
        "questionId",
        q.path("questionId").asText(),
        "selectedOptionId",
        "A",
        "timeSpentSecs",
        -1
      ),
      400
    );
    call(
      "POST",
      "/api/tests/" + id + "/answers",
      a.token,
      Map.of("questionId", UUID.randomUUID(), "selectedOptionId", "A"),
      400
    );
    for (String suffix : List.of("", "/questions/0", "/results"))
      call("GET", "/api/tests/" + id + suffix, other.token, null, 404);
    call("POST", "/api/tests/" + id + "/answers", other.token, body, 404);
    call("POST", "/api/tests/" + id + "/finish", other.token, null, 404);
    call("GET", "/api/tests/" + id + "/questions/-1", a.token, null, 400);
    call("GET", "/api/tests/" + id + "/questions/100000", a.token, null, 400);
    call("GET", "/api/tests/not-a-uuid", a.token, null, 400);
    call("POST", "/api/tests/" + id + "/finish", a.token, null, 200);
    JsonNode results = call(
      "GET",
      "/api/tests/" + id + "/results",
      a.token,
      null,
      200
    );
    assertEquals(0, results.path("score").asInt());
    assertTrue(
      results.path("answers").get(0).path("selectedOptionId").isNull()
    );
    call("POST", "/api/tests/" + id + "/answers", a.token, body, 409);
    assertEquals(
      0,
      call("GET", "/api/statistics/me", other.token, null, 200)
        .path("testsTaken")
        .asInt()
    );
  }

  @Test
  void adminCrudSoftDeleteAndStableSnapshots() throws Exception {
    Account a = register();
    db.update("update users set role='ADMIN' where id=?::uuid", a.id);
    try {
      var sb = Map.of(
        "nameRu",
        "Проверка",
        "nameKz",
        "Тексеру",
        "questionCount",
        99,
        "durationMinutes",
        10,
        "isActive",
        true
      );
      String subject = call("POST", "/api/admin/subjects", a.token, sb, 200)
        .path("id")
        .asText();
      call("GET", "/api/admin/subjects", a.token, null, 200);
      call("PUT", "/api/admin/subjects/" + subject, a.token, sb, 200);
      var tb = Map.of(
        "subjectId",
        subject,
        "titleRu",
        "Тема",
        "titleKz",
        "Тақырып",
        "sortOrder",
        1,
        "isActive",
        true
      );
      String topic = call("POST", "/api/admin/topics", a.token, tb, 200)
        .path("id")
        .asText();
      call("PUT", "/api/admin/topics/" + topic, a.token, tb, 200);
      call("GET", "/api/admin/topics/subject/" + subject, a.token, null, 200);
      var theory = Map.of(
        "topicId",
        topic,
        "titleRu",
        "Материал",
        "titleKz",
        "Материал",
        "contentRu",
        "Текст",
        "contentKz",
        "Мәтін",
        "sortOrder",
        0,
        "isActive",
        true
      );
      String theoryId = call(
        "POST",
        "/api/admin/theories",
        a.token,
        theory,
        200
      )
        .path("id")
        .asText();
      call("PUT", "/api/admin/theories/" + theoryId, a.token, theory, 200);
      call("GET", "/api/admin/theories/topic/" + topic, a.token, null, 200);
      Map<String, Object> question = new HashMap<>(
        Map.of(
          "topicId",
          topic,
          "questionRu",
          "Сколько?",
          "questionKz",
          "Қанша?",
          "options",
          List.of(
            Map.of("id", "A", "textRu", "1", "textKz", "1"),
            Map.of("id", "B", "textRu", "2", "textKz", "2")
          ),
          "correctOptionId",
          "A",
          "difficulty",
          "easy",
          "isActive",
          true
        )
      );
      String questionId = call(
        "POST",
        "/api/admin/questions",
        a.token,
        question,
        200
      )
        .path("id")
        .asText();
      String session = call(
        "POST",
        "/api/tests/start",
        a.token,
        Map.of("topicId", topic),
        200
      )
        .path("sessionId")
        .asText();
      question.put("correctOptionId", "B");
      question.put("questionRu", "Изменено");
      call("PUT", "/api/admin/questions/" + questionId, a.token, question, 200);
      call("GET", "/api/admin/questions/topic/" + topic, a.token, null, 200);
      call(
        "POST",
        "/api/tests/" + session + "/answers",
        a.token,
        Map.of("questionId", questionId, "selectedOptionId", "A"),
        200
      );
      assertEquals(
        100,
        call("POST", "/api/tests/" + session + "/finish", a.token, null, 200)
          .path("score")
          .asInt()
      );
      assertEquals(
        "Сколько?",
        call("GET", "/api/tests/" + session + "/results", a.token, null, 200)
          .path("answers")
          .get(0)
          .path("questionRu")
          .asText()
      );
      String secondSubject = call(
        "POST",
        "/api/admin/subjects",
        a.token,
        sb,
        200
      )
        .path("id")
        .asText();
      call(
        "PUT",
        "/api/admin/topics/" + topic,
        a.token,
        Map.of(
          "subjectId",
          secondSubject,
          "titleRu",
          "Новая тема",
          "titleKz",
          "Жаңа тақырып",
          "sortOrder",
          1,
          "isActive",
          true
        ),
        200
      );
      assertEquals(
        secondSubject,
        db.queryForObject(
          "select subject_id::text from questions where id=?::uuid",
          String.class,
          questionId
        )
      );
      assertEquals(
        secondSubject,
        db.queryForObject(
          "select subject_id::text from theories where id=?::uuid",
          String.class,
          theoryId
        )
      );
      call(
        "DELETE",
        "/api/admin/subjects/" + secondSubject,
        a.token,
        null,
        200
      );
      for (var item : Map.of(
        "questions",
        questionId,
        "theories",
        theoryId,
        "topics",
        topic,
        "subjects",
        subject
      ).entrySet())
        call(
          "DELETE",
          "/api/admin/" + item.getKey() + "/" + item.getValue(),
          a.token,
          null,
          200
        );
      call("POST", "/api/tests/start", a.token, Map.of("topicId", topic), 404);
      call("GET", "/api/theories/topic/" + topic, a.token, null, 404);
    } finally {
      db.update("update users set role='STUDENT' where id=?::uuid", a.id);
    }
  }

  @Test
  void corsOrigins() throws Exception {
    var allowed = mvc
      .perform(
        options("/api/subjects")
          .header("Origin", "http://localhost:5173")
          .header("Access-Control-Request-Method", "GET")
      )
      .andReturn()
      .getResponse();
    assertEquals(
      "http://localhost:5173",
      allowed.getHeader("Access-Control-Allow-Origin")
    );
    var denied = mvc
      .perform(
        options("/api/subjects")
          .header("Origin", "https://untrusted.example")
          .header("Access-Control-Request-Method", "GET")
      )
      .andReturn()
      .getResponse();
    assertEquals(403, denied.getStatus());
  }

  @Test
  void concurrentAnswerRetriesSaveExactlyOnce() throws Exception {
    Account a = register();
    String id = start(a.token);
    JsonNode q = call("GET", "/api/tests/" + id + "/questions/0", a.token, null, 200);
    var body = Map.of("questionId", q.path("questionId").asText(), "selectedOptionId", q.path("options").get(0).path("id").asText());
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var ready = new java.util.concurrent.CountDownLatch(4);
      var go = new java.util.concurrent.CountDownLatch(1);
      var tasks = new ArrayList<java.util.concurrent.Future<JsonNode>>();
      for (int i = 0; i < 4; i++) tasks.add(executor.submit(() -> {
        ready.countDown(); go.await();
        return call("POST", "/api/tests/" + id + "/answers", a.token, body, 200);
      }));
      assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); go.countDown();
      for (var task : tasks) assertEquals(q.path("questionId"), task.get(15, java.util.concurrent.TimeUnit.SECONDS).path("questionId"));
    }
    assertEquals(1, db.queryForObject("select count(*) from test_answers where session_id=?::uuid", Integer.class, id));
    call("POST", "/api/tests/" + id + "/finish", a.token, null, 200);
  }

  @Test
  void upgradeFreezesExistingAttemptsWithoutChangingHistoricalMigrations() {
    String schema = "upgrade_test";
    var config = org.flywaydb.core.Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
    config.target("12").load().migrate();
    UUID user = UUID.randomUUID(), session = UUID.randomUUID();
    String question = db.queryForObject("select id::text from upgrade_test.questions limit 1", String.class);
    String subject = db.queryForObject("select subject_id::text from upgrade_test.questions where id=?::uuid", String.class, question);
    db.update("insert into upgrade_test.users(id,email,password_hash,language) values (?,'upgrade@example.org','unusable','ru')", user);
    db.update("insert into upgrade_test.test_sessions(id,user_id,subject_id,question_ids,total_questions,correct_answers,status) values (?,?,?::uuid,jsonb_build_array(?::text),1,0,'COMPLETED')", session, user, subject, question);
    org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
    String frozen = db.queryForObject("select question_snapshot->0->>'questionRu' from upgrade_test.test_sessions where id=?", String.class, session);
    assertNotNull(frozen);
    db.update("update upgrade_test.questions set question_ru='changed after upgrade' where id=?::uuid", question);
    assertEquals(frozen, db.queryForObject("select question_snapshot->0->>'questionRu' from upgrade_test.test_sessions where id=?", String.class, session));
    assertEquals(1, db.queryForObject("select count(*) from upgrade_test.users", Integer.class));
    assertEquals(0, db.queryForObject("select count(*) from upgrade_test.users where role='ADMIN'", Integer.class));
  }
}
