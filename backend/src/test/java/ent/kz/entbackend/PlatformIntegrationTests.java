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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTests {

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
      () -> "phase2-integration-key-only-01234567890123456789"
    );
    r.add(
      "app.storage.local-root",
      () ->
        System.getProperty("java.io.tmpdir") +
        "/education-material-tests-" +
        UUID.randomUUID()
    );
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  record Account(String token, String id, String email) {}

  Account account(String role) throws Exception {
    String email = "phase2-" + UUID.randomUUID() + "@example.org";
    JsonNode a = call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        email,
        "password",
        "Phase2-password-2026",
        "firstName",
        "Тест",
        "lastName",
        "Проверка",
        "language",
        "ru",
        "role",
        "ADMIN"
      ),
      200
    );
    assertEquals("STUDENT", a.path("user").path("role").asText());
    String id = a.path("user").path("id").asText();
    db.update("UPDATE users SET role=? WHERE id=?::uuid", role, id);
    return new Account(a.path("token").asText(), id, email);
  }

  JsonNode call(
    String method,
    String path,
    Account actor,
    Object body,
    int expected
  ) throws Exception {
    var req = switch (method) {
      case "POST" -> post(path);
      case "PUT" -> put(path);
      case "PATCH" -> patch(path);
      case "DELETE" -> delete(path);
      default -> get(path);
    };
    if (actor != null) req.header("Authorization", "Bearer " + actor.token());
    if (body != null) req
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsBytes(body));
    var response = mvc.perform(req).andReturn().getResponse();
    assertEquals(
      expected,
      response.getStatus(),
      method + " " + path + " " + response.getContentAsString()
    );
    return response.getContentAsByteArray().length == 0
      ? json.nullNode()
      : json.readTree(response.getContentAsByteArray());
  }

  Map<String, Object> payload(String title) {
    return new LinkedHashMap<>(
      Map.of("titleRu", title, "titleKz", title + " KZ")
    );
  }

  JsonNode create(Account a, String kind, String parent, Map<String, Object> p)
    throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("kind", kind);
    body.put("parentId", parent);
    body.put("payload", p);
    return call("POST", "/api/cms/content", a, body, 200);
  }

  JsonNode publish(Account a, JsonNode c) throws Exception {
    String url = "/api/cms/content/" + c.path("id").asText() + "/transition";
    JsonNode review = call(
      "POST",
      url,
      a,
      Map.of("status", "REVIEW", "version", c.path("version").asLong()),
      200
    );
    return call(
      "POST",
      url,
      a,
      Map.of("status", "PUBLISHED", "version", review.path("version").asLong()),
      200
    );
  }

  JsonNode course(Account a) throws Exception {
    var p = payload("Курс " + UUID.randomUUID());
    p.put("visibility", "PUBLIC");
    p.put("selfEnroll", true);
    return create(a, "COURSE", null, p);
  }

  Map<String, Object> question() {
    var p = payload("Сколько будет 2 + 2?");
    p.put(
      "options",
      List.of(
        Map.of("id", "A", "textRu", "4", "textKz", "4"),
        Map.of("id", "B", "textRu", "5", "textKz", "5")
      )
    );
    p.put("correctOptionId", "A");
    return p;
  }

  @Test
  void rolesOwnershipPublicationAndRevisions() throws Exception {
    Account admin = account("ADMIN"),
      editor = account("CONTENT_EDITOR"),
      teacher = account("TEACHER"),
      other = account("TEACHER"),
      student = account("STUDENT");
    call("GET", "/api/cms/content", student, null, 403);
    call("GET", "/api/admin/users", teacher, null, 403);
    call("GET", "/api/teacher/groups", editor, null, 403);
    JsonNode c = course(teacher);
    String id = c.path("id").asText();
    call("GET", "/api/cms/content/" + id, other, null, 403);
    call("GET", "/api/courses/" + id, student, null, 404);
    call(
      "PUT",
      "/api/cms/content/" + id,
      teacher,
      Map.of("kind", "COURSE", "payload", payload("Forged"), "version", 9),
      409
    );
    c = publish(teacher, c);
    assertEquals("PUBLISHED", c.path("status").asText());
    call("GET", "/api/courses/" + id, student, null, 200);
    JsonNode changed = call(
      "PUT",
      "/api/cms/content/" + id,
      teacher,
      Map.of(
        "kind",
        "COURSE",
        "payload",
        payload("Не опубликовано"),
        "version",
        c.path("version").asLong()
      ),
      200
    );
    assertNotEquals(
      "Не опубликовано",
      call("GET", "/api/courses/" + id, student, null, 200)
        .path("content")
        .path("titleRu")
        .asText()
    );
    call(
      "POST",
      "/api/cms/content/" + id + "/transition",
      teacher,
      Map.of("status", "ARCHIVED", "version", changed.path("version").asLong()),
      403
    );
    call(
      "POST",
      "/api/cms/content/" + id + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", changed.path("version").asLong()),
      200
    );
    call("GET", "/api/courses/" + id, student, null, 404);
    JsonNode sub = publish(
      editor,
      create(editor, "SUBJECT", null, payload("Новый предмет"))
    );
    JsonNode topic = publish(
      editor,
      create(editor, "TOPIC", sub.path("id").asText(), payload("Новая тема"))
    );
    JsonNode q = create(
      editor,
      "QUESTION",
      topic.path("id").asText(),
      question()
    );
    call(
      "POST",
      "/api/tests/start",
      student,
      Map.of("topicId", topic.path("id").asText()),
      409
    );
    publish(editor, q);
    call(
      "POST",
      "/api/tests/start",
      student,
      Map.of("topicId", topic.path("id").asText()),
      200
    );
    call("PATCH","/api/admin/users/"+editor.id(),admin,Map.of("role","TEACHER","active",true,"revision",0),200);
    assertEquals(0,call("GET","/api/cms/content",editor,null,200).path("items").size());

  }

  @Test
  void groupsEnrollmentAssignmentsAndProgress() throws Exception {
    Account t = account("TEACHER"),
      other = account("TEACHER"),
      s = account("STUDENT"),
      outsider = account("STUDENT");
    JsonNode c = publish(t, course(t));
    String cid = c.path("id").asText();
    JsonNode m = publish(t, create(t, "MODULE", cid, payload("Модуль")));
    JsonNode l = publish(
      t,
      create(t, "LESSON", m.path("id").asText(), payload("Урок"))
    );
    String lid = l.path("id").asText();
    call("GET", "/api/content/" + lid, s, null, 404);
    JsonNode g = call(
      "POST",
      "/api/teacher/groups",
      t,
      Map.of("name", "Группа А", "courseId", cid),
      200
    );
    String gid = g.path("id").asText();
    call("GET", "/api/teacher/groups/" + gid, other, null, 404);
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/members",
      t,
      Map.of("email", s.email()),
      200
    );
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/members",
      t,
      Map.of("email", s.email()),
      200
    );
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM enrollments WHERE course_id=?::uuid AND user_id=?::uuid",
        Integer.class,
        cid,
        s.id()
      )
    );
    call("GET", "/api/content/" + lid, s, null, 200);
    call("POST", "/api/lessons/" + lid + "/complete", s, null, 200);
    assertEquals(
      1,
      call("GET", "/api/courses/" + cid, s, null, 200)
        .path("completedLessons")
        .asInt()
    );
    var p = payload("Задание");
    p.put("maxScore", 10);
    JsonNode a = publish(t, create(t, "ASSIGNMENT", lid, p));
    String aid = a.path("id").asText();
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/assignments",
      t,
      Map.of("assignmentId", aid),
      200
    );
    call("GET", "/api/assignments/" + aid, outsider, null, 404);
    call(
      "POST",
      "/api/assignments/" + aid + "/submit",
      s,
      Map.of("text", "Мой ответ"),
      200
    );
    call(
      "POST",
      "/api/teacher/assignments/" + aid + "/submissions/" + s.id() + "/grade",
      t,
      Map.of("score", 8, "feedback", "Хорошая работа", "revision", 1),
      200
    );
    assertEquals(
      8,
      call("GET", "/api/assignments/" + aid, s, null, 200)
        .path("submission")
        .path("score")
        .asInt()
    );
    call(
      "POST",
      "/api/assignments/" + aid + "/submit",
      s,
      Map.of("text", "Новый ответ"),
      200
    );
    call(
      "POST",
      "/api/teacher/assignments/" + aid + "/submissions/" + s.id() + "/grade",
      t,
      Map.of("score", 4, "revision", 1),
      409
    );
    assertTrue(
      call("GET", "/api/assignments/" + aid, s, null, 200)
        .path("submission")
        .path("score")
        .isNull()
    );
    assertEquals(
      1,
      call(
        "GET",
        "/api/teacher/assignments/" + aid + "/submissions",
        t,
        null,
        200
      )
        .path("total")
        .asInt()
    );
    call(
      "DELETE",
      "/api/teacher/groups/" + gid + "/members/" + s.id(),
      t,
      null,
      200
    );
    call("GET", "/api/content/" + lid, s, null, 404);
    call("GET", "/api/assignments/" + aid, s, null, 404);
  }

  @Test
  void uploadProtectsDraftsPrivateLessonsAndRejectsTraversal()
    throws Exception {
    Account t = account("TEACHER"),
      s = account("STUDENT"),
      other = account("TEACHER");
    JsonNode c = course(t);
    String cid = c.path("id").asText();
    JsonNode m = create(t, "MODULE", cid, payload("Модуль"));
    JsonNode l = create(t, "LESSON", m.path("id").asText(), payload("Урок"));
    String lid = l.path("id").asText();
    byte[] pdf = "%PDF-1.4\nfixture".getBytes(
      java.nio.charset.StandardCharsets.UTF_8
    );
    var req = multipart("/api/cms/materials")
      .file(new MockMultipartFile("file", "lesson.pdf", "application/pdf", pdf))
      .param("contentId", lid)
      .param("titleRu", "Конспект")
      .header("Authorization", "Bearer " + t.token());
    var response = mvc.perform(req).andReturn().getResponse();
    assertEquals(200, response.getStatus(), response.getContentAsString());
    String file = json
      .readTree(response.getContentAsByteArray())
      .path("id")
      .asText();
    assertEquals(
      403,
      mvc
        .perform(
          multipart("/api/cms/materials")
            .file(
              new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                pdf
              )
            )
            .param("contentId", lid)
            .param("titleRu", "Конспект")
            .header("Authorization", "Bearer " + other.token())
        )
        .andReturn()
        .getResponse()
        .getStatus()
    );
    assertEquals(
      400,
      mvc
        .perform(
          multipart("/api/cms/materials")
            .file(
              new MockMultipartFile(
                "file",
                "../bad.pdf",
                "application/pdf",
                pdf
              )
            )
            .param("contentId", lid)
            .param("titleRu", "Конспект")
            .header("Authorization", "Bearer " + t.token())
        )
        .andReturn()
        .getResponse()
        .getStatus()
    );
    assertEquals(
      400,
      mvc
        .perform(
          multipart("/api/cms/materials")
            .file(
              new MockMultipartFile(
                "file",
                "bad.pdf",
                "application/pdf",
                "MZ executable".getBytes()
              )
            )
            .param("contentId", lid)
            .param("titleRu", "Конспект")
            .header("Authorization", "Bearer " + t.token())
        )
        .andReturn()
        .getResponse()
        .getStatus()
    );
    call("GET", "/api/materials/" + file + "/download", s, null, 404);
    publish(t, c);
    publish(t, m);
    publish(t, l);
    call("POST", "/api/courses/" + cid + "/enroll", s, null, 200);
    var downloaded = mvc
      .perform(
        get("/api/materials/" + file + "/download").header(
          "Authorization",
          "Bearer " + s.token()
        )
      )
      .andReturn()
      .getResponse();
    assertEquals(200, downloaded.getStatus());
    assertArrayEquals(pdf, downloaded.getContentAsByteArray());
    assertTrue(
      downloaded.getHeader("Content-Disposition").startsWith("attachment")
    );
    assertEquals(
      401,
      mvc
        .perform(get("/api/materials/" + file + "/download"))
        .andReturn()
        .getResponse()
        .getStatus()
    );
  }

  JsonNode preview(Account a, String name, String body) throws Exception {
    var response = mvc
      .perform(
        multipart("/api/cms/imports")
          .file(
            new MockMultipartFile(
              "file",
              name,
              "application/octet-stream",
              body.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
          )
          .header("Authorization", "Bearer " + a.token())
      )
      .andReturn()
      .getResponse();
    assertEquals(200, response.getStatus(), response.getContentAsString());
    return json.readTree(response.getContentAsByteArray());
  }

  @Test
  void importPreviewValidationAndAtomicIdempotentConfirm() throws Exception {
    Account e = account("CONTENT_EDITOR");
    String valid =
      "[{\"key\":\"subject\",\"kind\":\"SUBJECT\",\"payload\":{\"titleRu\":\"Импорт\",\"titleKz\":\"Импорт\"}},{\"key\":\"topic\",\"kind\":\"TOPIC\",\"parentKey\":\"subject\",\"payload\":{\"titleRu\":\"Тема\",\"titleKz\":\"Тақырып\"}}]";
    long before = db.queryForObject(
      "SELECT count(*) FROM content_records",
      Long.class
    );
    JsonNode p = preview(e, "valid.json", valid);
    assertEquals("VALID", p.path("status").asText());
    assertEquals(
      before,
      db.queryForObject("SELECT count(*) FROM content_records", Long.class)
    );
    String path = "/api/cms/imports/" + p.path("id").asText() + "/confirm";
    JsonNode done = call("POST", path, e, null, 200);
    assertEquals("IMPORTED", done.path("status").asText());
    call("POST", path, e, null, 200);
    assertEquals(
      before + 2,
      db.queryForObject("SELECT count(*) FROM content_records", Long.class)
    );
    var bad = question();
    bad.put("correctOptionId", "D");
    String invalid = json.writeValueAsString(
      List.of(
        Map.of(
          "key",
          "bad",
          "kind",
          "QUESTION",
          "parentId",
          done.path("result").path("topic").asText(),
          "payload",
          bad
        )
      )
    );
    JsonNode rejected = preview(e, "invalid.json", invalid);
    assertEquals("INVALID", rejected.path("status").asText());
    assertEquals(
      "CORRECT_OPTION_MISSING",
      rejected.path("errors").get(0).path("code").asText()
    );
    call(
      "POST",
      "/api/cms/imports/" + rejected.path("id").asText() + "/confirm",
      e,
      null,
      409
    );
    assertEquals(
      before + 2,
      db.queryForObject("SELECT count(*) FROM content_records", Long.class)
    );
    JsonNode csv = preview(
      e,
      "valid.csv",
      "key,kind,titleRu,titleKz\r\ns,SUBJECT,\"Русский, язык\",Тіл\r\n"
    );
    assertEquals("VALID", csv.path("status").asText());
    call(
      "POST",
      "/api/cms/imports/" + csv.path("id").asText() + "/confirm",
      e,
      null,
      200
    );
  }

  @Test
  void lessonQuizHidesKeysAndOwnsAttempts() throws Exception {
    Account t = account("TEACHER"),
      s = account("STUDENT"),
      other = account("STUDENT");
    JsonNode c = publish(t, course(t)),
      m = publish(
        t,
        create(t, "MODULE", c.path("id").asText(), payload("Модуль"))
      ),
      l = publish(
        t,
        create(t, "LESSON", m.path("id").asText(), payload("Урок"))
      );
    var p = payload("Проверка");
    p.put("questions", List.of(question()));
    JsonNode q = publish(t, create(t, "QUIZ", l.path("id").asText(), p));
    call(
      "POST",
      "/api/courses/" + c.path("id").asText() + "/enroll",
      s,
      null,
      200
    );
    JsonNode attempt = call(
      "POST",
      "/api/quizzes/" + q.path("id").asText() + "/attempts",
      s,
      null,
      200
    );
    assertFalse(attempt.toString().contains("correctOptionId"));
    String path = "/api/quiz-attempts/" + attempt.path("id").asText();
    call("GET", path, other, null, 404);
    call("POST", path + "/finish", s, Map.of("answers", List.of("X")), 400);
    JsonNode result = call(
      "POST",
      path + "/finish",
      s,
      Map.of("answers", List.of("A")),
      200
    );
    assertEquals(100, result.path("score").asInt());
    assertTrue(result.toString().contains("correctOptionId"));
    assertEquals(
      result,
      call("POST", path + "/finish", s, Map.of("answers", List.of("B")), 200)
    );
  }

  @Test
  void userAdministrationUsesRevisionAndInvalidatesExistingToken()
    throws Exception {
    Account a = account("ADMIN"),
      s = account("STUDENT");
    call(
      "PATCH",
      "/api/admin/users/" + s.id(),
      a,
      Map.of("role", "TEACHER", "active", true, "revision", 0),
      200
    );
    assertEquals(
      "TEACHER",
      call("GET", "/api/auth/me", s, null, 200).path("role").asText()
    );
    call(
      "PATCH",
      "/api/admin/users/" + s.id(),
      a,
      Map.of("role", "ADMIN", "active", true, "revision", 0),
      409
    );
    call(
      "PATCH",
      "/api/admin/users/" + s.id(),
      a,
      Map.of("role", "TEACHER", "active", false, "revision", 1),
      200
    );
    call("GET", "/api/auth/me", s, null, 401);
    call(
      "PATCH",
      "/api/admin/users/" + a.id(),
      a,
      Map.of("role", "STUDENT", "active", true, "revision", 0),
      409
    );
  }

  @Test
  void masteryUsesRealActivityAndReviewDeduplicatesErrors() throws Exception {
    Account editor = account("CONTENT_EDITOR"),
      student = account("STUDENT"),
      other = account("STUDENT");
    JsonNode subject = publish(
      editor,
      create(editor, "SUBJECT", null, payload("Прогресс"))
    );
    JsonNode topic = publish(
      editor,
      create(editor, "TOPIC", subject.path("id").asText(), payload("Дроби"))
    );
    String tid = topic.path("id").asText();
    JsonNode theory = publish(
      editor,
      create(editor, "THEORY", tid, payload("Теория дробей"))
    );
    publish(editor, create(editor, "QUESTION", tid, question()));
    for (int i = 0; i < 2; i++) {
      JsonNode s = call(
        "POST",
        "/api/tests/start",
        student,
        Map.of("topicId", tid),
        200
      );
      call(
        "POST",
        "/api/tests/" + s.path("sessionId").asText() + "/finish",
        student,
        null,
        200
      );
    }
    JsonNode before = call("GET", "/api/learning/me", student, null, 200);
    assertEquals(1, before.path("errorCount").asInt());
    assertEquals(2, before.path("questionsAnswered").asInt());
    assertEquals(
      0,
      call("GET", "/api/learning/me", other, null, 200)
        .path("errorCount")
        .asInt()
    );
    call(
      "POST",
      "/api/learning/theories/" + theory.path("id").asText() + "/read",
      student,
      null,
      200
    );
    JsonNode review = call(
      "POST",
      "/api/learning/errors/practice",
      student,
      Map.of("topicId", tid),
      200
    );
    String sid = review.path("sessionId").asText();
    JsonNode q = call(
      "GET",
      "/api/tests/" + sid + "/questions/0",
      student,
      null,
      200
    );
    assertFalse(q.toString().contains("correctOptionId"));
    call("GET", "/api/tests/" + sid + "/results", student, null, 409);
    call(
      "POST",
      "/api/tests/" + sid + "/answers",
      student,
      Map.of(
        "questionId",
        q.path("questionId").asText(),
        "selectedOptionId",
        "A"
      ),
      200
    );
    call("POST", "/api/tests/" + sid + "/finish", student, null, 200);
    call("POST", "/api/tests/" + sid + "/finish", student, null, 200);
    JsonNode after = call("GET", "/api/learning/me", student, null, 200);
    assertEquals(0, after.path("errorCount").asInt());
    assertEquals(3, after.path("testsCompleted").asInt());
    JsonNode progress = null;
    for (JsonNode item : after.path("topics"))
      if (item.path("topicId").asText().equals(tid)) progress = item;
    assertNotNull(progress);
    assertEquals(46.7, progress.path("mastery").asDouble(), 0.01);
    assertEquals(1, progress.path("theoryRead").asInt());
  }

  @Test
  void archivedContentCannotBeRestoredByOwnerEdits() throws Exception {
    Account admin = account("ADMIN"),
      teacher = account("TEACHER"),
      student = account("STUDENT");
    JsonNode c = publish(teacher, course(teacher));
    String id = c.path("id").asText(),
      path = "/api/cms/content/" + id;
    JsonNode archived = call(
      "POST",
      path + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", c.path("version").asLong()),
      200
    );
    call(
      "PUT",
      path,
      teacher,
      Map.of(
        "kind",
        "COURSE",
        "payload",
        payload("Edited"),
        "version",
        archived.path("version").asLong()
      ),
      409
    );
    call(
      "POST",
      path + "/transition",
      teacher,
      Map.of("status", "DRAFT", "version", archived.path("version").asLong()),
      403
    );
    call("GET", "/api/courses/" + id, student, null, 404);
    call(
      "POST",
      path + "/transition",
      admin,
      Map.of("status", "DRAFT", "version", archived.path("version").asLong()),
      200
    );
    call("GET", "/api/courses/" + id, student, null, 404);
  }

  @Test
  void legacyAdminEditsDoNotPublishDraftBlocks() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    JsonNode sub = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Legacy bridge"))
    );
    JsonNode topic = publish(
      admin,
      create(admin, "TOPIC", sub.path("id").asText(), payload("Bridge topic"))
    );
    var p = payload("Published theory");
    p.put(
      "blocks",
      List.of(
        Map.of("type", "TEXT", "textRu", "Published", "textKz", "Published KZ")
      )
    );
    JsonNode theory = publish(
      admin,
      create(admin, "THEORY", topic.path("id").asText(), p)
    );
    String id = theory.path("id").asText();
    p.put(
      "blocks",
      List.of(
        Map.of(
          "type",
          "TEXT",
          "textRu",
          "Secret draft",
          "textKz",
          "Secret draft KZ"
        )
      )
    );
    call(
      "PUT",
      "/api/cms/content/" + id,
      admin,
      Map.of(
        "kind",
        "THEORY",
        "parentId",
        topic.path("id").asText(),
        "payload",
        p,
        "version",
        theory.path("version").asLong()
      ),
      200
    );
    call(
      "PUT",
      "/api/admin/theories/" + id,
      admin,
      Map.of(
        "topicId",
        topic.path("id").asText(),
        "titleRu",
        "Legacy edit",
        "titleKz",
        "Legacy edit KZ",
        "contentRu",
        "Legacy text",
        "contentKz",
        "Legacy text KZ",
        "sortOrder",
        0,
        "isActive",
        true
      ),
      200
    );
    JsonNode published = call("GET", "/api/content/" + id, student, null, 200);
    assertFalse(published.toString().contains("Secret draft"));
    assertTrue(published.toString().contains("Published"));
    assertTrue(
      call("GET", "/api/cms/content/" + id, admin, null, 200)
        .path("payload")
        .toString()
        .contains("Secret draft")
    );
    call(
      "POST",
      "/api/cms/content",
      admin,
      Map.of("kind", "SUBJECT", "payload", payload("x".repeat(201))),
      400
    );
  }

  @Test
  void concurrentGroupRemovalsRevokeEnrollmentAndLessonCompletionConverges()
    throws Exception {
    Account teacher = account("TEACHER"),
      student = account("STUDENT");
    JsonNode c = publish(teacher, course(teacher));
    String cid = c.path("id").asText();
    String first = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "First", "courseId", cid),
      200
    )
      .path("id")
      .asText();
    String second = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "Second", "courseId", cid),
      200
    )
      .path("id")
      .asText();
    for (String group : List.of(first, second))
      call(
        "POST",
        "/api/teacher/groups/" + group + "/members",
        teacher,
        Map.of("email", student.email()),
        200
      );
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (String group : List.of(first, second))
        tasks.add(
          pool.submit(() -> {
            ready.countDown();
            start.await();
            call(
              "DELETE",
              "/api/teacher/groups/" + group + "/members/" + student.id(),
              teacher,
              null,
              200
            );
            return null;
          })
        );
      assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      for (var task : tasks)
        task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(
      "CANCELLED",
      db.queryForObject(
        "SELECT status FROM enrollments WHERE course_id=?::uuid AND user_id=?::uuid",
        String.class,
        cid,
        student.id()
      )
    );
    call(
      "POST",
      "/api/teacher/courses/" + cid + "/enrollments",
      teacher,
      Map.of("email", student.email(), "status", "ACTIVE"),
      200
    );
    JsonNode m = publish(
      teacher,
      create(teacher, "MODULE", cid, payload("Concurrent module"))
    );
    String a = publish(
        teacher,
        create(teacher, "LESSON", m.path("id").asText(), payload("Lesson A"))
      )
        .path("id")
        .asText(),
      b = publish(
        teacher,
        create(teacher, "LESSON", m.path("id").asText(), payload("Lesson B"))
      )
        .path("id")
        .asText();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      for (String lesson : List.of(a, b))
        futures.add(
          pool.submit(() -> {
            call(
              "POST",
              "/api/lessons/" + lesson + "/complete",
              student,
              null,
              200
            );
            return null;
          })
        );
      for (var future : futures)
        future.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(
      "COMPLETED",
      db.queryForObject(
        "SELECT status FROM enrollments WHERE course_id=?::uuid AND user_id=?::uuid",
        String.class,
        cid,
        student.id()
      )
    );
  }

  @Test
  void assignmentScaleSurvivesArchiveAndInvalidNestedQuizIsRejected()
    throws Exception {
    Account teacher = account("TEACHER"),
      admin = account("ADMIN"),
      student = account("STUDENT");
    JsonNode c = publish(teacher, course(teacher));
    String cid = c.path("id").asText();
    var p = payload("Scale assignment");
    p.put("maxScore", 100);
    JsonNode a = publish(teacher, create(teacher, "ASSIGNMENT", cid, p));
    String aid = a.path("id").asText(),
      path = "/api/cms/content/" + aid;
    String gid = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "Scale group", "courseId", cid),
      200
    )
      .path("id")
      .asText();
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/members",
      teacher,
      Map.of("email", student.email()),
      200
    );
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/assignments",
      teacher,
      Map.of("assignmentId", aid),
      200
    );
    call(
      "POST",
      "/api/assignments/" + aid + "/submit",
      student,
      Map.of("text", "Answer"),
      200
    );
    JsonNode archived = call(
      "POST",
      path + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", a.path("version").asLong()),
      200
    );
    call(
      "POST",
      "/api/teacher/assignments/" +
        aid +
        "/submissions/" +
        student.id() +
        "/grade",
      teacher,
      Map.of("score", 80, "revision", 1),
      409
    );
    JsonNode restored = call(
      "POST",
      path + "/transition",
      admin,
      Map.of("status", "DRAFT", "version", archived.path("version").asLong()),
      200
    );
    p.put("maxScore", 10);
    JsonNode changed = call(
      "PUT",
      path,
      teacher,
      Map.of(
        "kind",
        "ASSIGNMENT",
        "parentId",
        cid,
        "payload",
        p,
        "version",
        restored.path("version").asLong()
      ),
      200
    );
    JsonNode review = call(
      "POST",
      path + "/transition",
      teacher,
      Map.of("status", "REVIEW", "version", changed.path("version").asLong()),
      200
    );
    call(
      "POST",
      path + "/transition",
      teacher,
      Map.of("status", "PUBLISHED", "version", review.path("version").asLong()),
      409
    );
    var bad = question();
    bad.put("explanationRu", 123);
    var quiz = payload("Bad quiz");
    quiz.put("questions", List.of(bad));
    call(
      "POST",
      "/api/cms/content",
      teacher,
      Map.of("kind", "QUIZ", "parentId", cid, "payload", quiz),
      400
    );
  }

  @Test
  void publicCatalogDoesNotGrantCourseFiles() throws Exception {
    Account teacher = account("TEACHER"),
      student = account("STUDENT");
    JsonNode c = course(teacher);
    String cid = c.path("id").asText();
    byte[] bytes = "%PDF-1.4 course fixture".getBytes();
    var response = mvc
      .perform(
        multipart("/api/cms/materials")
          .file(
            new MockMultipartFile(
              "file",
              "course.pdf",
              "application/pdf",
              bytes
            )
          )
          .param("contentId", cid)
          .param("titleRu", "Course material")
          .header("Authorization", "Bearer " + teacher.token())
      )
      .andReturn()
      .getResponse();
    assertEquals(200, response.getStatus());
    String file = json
      .readTree(response.getContentAsByteArray())
      .path("id")
      .asText();
    publish(teacher, c);
    call("GET", "/api/courses/" + cid, student, null, 200);
    assertEquals(
      0,
      call(
        "GET",
        "/api/content/" + cid + "/materials",
        student,
        null,
        200
      ).size()
    );
    call("GET", "/api/materials/" + file + "/download", student, null, 404);
    call(
      "POST",
      "/api/teacher/courses/" + cid + "/enrollments",
      teacher,
      Map.of("email", student.email(), "status", "ACTIVE"),
      200
    );
    assertEquals(
      200,
      mvc
        .perform(
          get("/api/materials/" + file + "/download").header(
            "Authorization",
            "Bearer " + student.token()
          )
        )
        .andReturn()
        .getResponse()
        .getStatus()
    );
    call(
      "POST",
      "/api/teacher/courses/" + cid + "/enrollments",
      teacher,
      Map.of("email", student.email(), "status", "CANCELLED"),
      200
    );
    call("GET", "/api/materials/" + file + "/download", student, null, 404);
  }

  @Test
  void migrationUpgradesV15WithoutLosingUsersOrAttempts() {
    String schema = "phase2_upgrade";
    var cfg = org.flywaydb.core.Flyway.configure()
      .dataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
      .schemas(schema)
      .defaultSchema(schema)
      .locations("classpath:db/migration");
    cfg.target("15").load().migrate();
    UUID user = UUID.randomUUID(),
      admin = UUID.randomUUID();
    db.update(
      "INSERT INTO phase2_upgrade.users(id,email,password_hash,role) VALUES (?,'oldstudent@example.org','unusable','STUDENT'),(?,'oldadmin@example.org','unusable','ADMIN')",
      user,
      admin
    );
    long count = db.queryForObject(
      "SELECT count(*) FROM phase2_upgrade.subjects WHERE is_active",
      Long.class
    );
    UUID session = UUID.randomUUID();
    db.update(
      "INSERT INTO phase2_upgrade.test_sessions(id,user_id,subject_id,topic_id,status,question_ids,question_snapshot,total_questions,correct_answers,score,completed_at) SELECT ?,?,q.subject_id,q.topic_id,'COMPLETED',jsonb_build_array(q.id),jsonb_build_array(jsonb_build_object('id',q.id,'topicId',q.topic_id,'questionRu',q.question_ru,'correctOptionId',q.correct_option_id)),1,1,100,now() FROM phase2_upgrade.questions q LIMIT 1",
      session,
      user
    );
    db.update(
      "INSERT INTO phase2_upgrade.test_answers(session_id,question_id,selected_option_id,is_correct) SELECT id,(question_ids->>0)::uuid,'A',true FROM phase2_upgrade.test_sessions WHERE id=?",
      session
    );
    String snapshot = db.queryForObject(
      "SELECT question_snapshot::text FROM phase2_upgrade.test_sessions WHERE id=?",
      String.class,
      session
    );

    org.flywaydb.core.Flyway.configure()
      .dataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
      .schemas(schema)
      .defaultSchema(schema)
      .locations("classpath:db/migration")
      .load()
      .migrate();
    assertEquals(
      count,
      db.queryForObject(
        "SELECT count(*) FROM phase2_upgrade.content_records WHERE kind='SUBJECT' AND published_payload IS NOT NULL",
        Long.class
      )
    );
    assertEquals(
      "STUDENT",
      db.queryForObject(
        "SELECT role FROM phase2_upgrade.users WHERE id=?",
        String.class,
        user
      )
    );
    assertEquals(
      "ADMIN",
      db.queryForObject(
        "SELECT role FROM phase2_upgrade.users WHERE id=?",
        String.class,
        admin
      )
    );
    assertEquals(
      snapshot,
      db.queryForObject(
        "SELECT question_snapshot::text FROM phase2_upgrade.test_sessions WHERE id=?",
        String.class,
        session
      )
    );
    assertEquals(
      100,
      db.queryForObject(
        "SELECT score::int FROM phase2_upgrade.test_sessions WHERE id=?",
        Integer.class,
        session
      )
    );
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM phase2_upgrade.completed_question_activity WHERE user_id=? AND correct",
        Integer.class,
        user
      )
    );
  }
}
