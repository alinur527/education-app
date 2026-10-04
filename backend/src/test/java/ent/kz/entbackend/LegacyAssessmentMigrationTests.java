package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Populate V18 before Spring applies new migrations; exercise real HTTP compatibility afterward. */
@SpringBootTest(
  properties = {
    "app.study.notifications.enabled=false",
    "app.submission-files.worker-enabled=false",
  }
)
@AutoConfigureMockMvc
@Testcontainers
class LegacyAssessmentMigrationTests {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
    "postgres:17-alpine"
  );

  static final UUID user = UUID.randomUUID(),
    completed = UUID.randomUUID(),
    resume = UUID.randomUUID(),
    unanswered = UUID.randomUUID();
  static UUID firstQuestion;
  static String originalSnapshot;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) throws Exception {
    Flyway.configure()
      .dataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
      .locations("classpath:db/migration")
      .target("18")
      .load()
      .migrate();
    JdbcTemplate db = new JdbcTemplate(
      new DriverManagerDataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
    );
    ObjectMapper json = new ObjectMapper();
    db.update(
      "INSERT INTO users(id,email,password_hash,first_name,last_name,role) VALUES (?,'legacy-assessment@example.org',?,'Legacy','Student','STUDENT')",
      user,
      new BCryptPasswordEncoder().encode("Legacy-password-2026")
    );
    UUID topic = db.queryForObject(
      "SELECT topic_id FROM questions WHERE topic_id IS NOT NULL GROUP BY topic_id HAVING count(*)>=2 LIMIT 1",
      UUID.class
    );
    var questions = db.queryForList(
      "SELECT id,subject_id FROM questions WHERE topic_id=? ORDER BY id LIMIT 2",
      topic
    );
    firstQuestion = (UUID) questions.getFirst().get("id");
    var snapshots = new ArrayList<Map<String, Object>>();
    for (var q : questions) {
      snapshots.add(
        Map.of(
          "id",
          q.get("id"),
          "topicId",
          topic,
          "topicRu",
          "Старая тема",
          "topicKz",
          "Ескі тақырып",
          "questionRu",
          "Замороженный вопрос",
          "questionKz",
          "Сақталған сұрақ",
          "options",
          List.of(
            Map.of("id", "А", "textRu", "Да", "textKz", "Иә"),
            Map.of("id", "Б", "textRu", "Нет", "textKz", "Жоқ")
          ),
          "correctOptionId",
          "А"
        )
      );
    }
    UUID subject = (UUID) questions.getFirst().get("subject_id");
    for (UUID id : List.of(completed, resume, unanswered)) {
      boolean done = id.equals(completed);
      var chosen = done ? snapshots : List.of(snapshots.getFirst());
      db.update(
        "INSERT INTO test_sessions(id,user_id,subject_id,topic_id,status,question_ids,question_snapshot,total_questions,correct_answers,score,completed_at,time_taken_secs) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,CASE WHEN ? THEN timestamp '2026-01-01 12:00:00' ELSE NULL END,?)",
        id,
        user,
        subject,
        topic,
        done ? "COMPLETED" : "IN_PROGRESS",
        json.writeValueAsString(
          chosen
            .stream()
            .map(q -> q.get("id"))
            .toList()
        ),
        json.writeValueAsString(chosen),
        chosen.size(),
        done ? 1 : 0,
        done ? 50 : null,
        done,
        done ? 120 : null
      );
    }
    db.update(
      "INSERT INTO test_answers(session_id,question_id,selected_option_id,is_correct) VALUES (?,?,'А',true),(?,?,'Б',false)",
      completed,
      firstQuestion,
      resume,
      firstQuestion
    );
    originalSnapshot = db.queryForObject(
      "SELECT question_snapshot::text FROM test_sessions WHERE id=?",
      String.class,
      completed
    );
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add(
      "app.jwt.secret",
      () -> "legacy-assessment-test-signing-key-01234567890123456789"
    );
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  JsonNode call(
    String method,
    String path,
    String token,
    Object body,
    int status
  ) throws Exception {
    var request = method.equals("POST") ? post(path) : get(path);
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsBytes(body));
    var response = mvc.perform(request).andReturn().getResponse();
    assertEquals(status, response.getStatus(), response.getContentAsString());
    return json.readTree(response.getContentAsByteArray());
  }

  String token() throws Exception {
    return call(
      "POST",
      "/api/auth/login",
      null,
      Map.of(
        "email",
        "legacy-assessment@example.org",
        "password",
        "Legacy-password-2026"
      ),
      200
    )
      .path("token")
      .asText();
  }

  @Test
  void completedLegacyScoreSnapshotAndNullableFieldsRemainCompatible()
    throws Exception {
    String token = token();
    var result = call(
      "GET",
      "/api/tests/" + completed + "/results",
      token,
      null,
      200
    );
    assertEquals(50, result.path("score").asInt());
    assertEquals(1, result.path("correctAnswers").asInt());
    assertTrue(result.path("earnedPoints").isNull());
    assertTrue(result.path("maxPoints").isNull());
    assertEquals(
      "А",
      result.path("answers").get(0).path("correctOptionId").asText()
    );
    assertEquals(1, result.path("answers").get(0).path("earnedPoints").asInt());
    assertEquals(0, result.path("answers").get(1).path("earnedPoints").asInt());
    db.update(
      "UPDATE questions SET question_ru='Changed live',correct_option_id='B' WHERE id=?",
      firstQuestion
    );
    call("POST", "/api/tests/" + completed + "/finish", token, null, 200);
    assertEquals(
      result,
      call("GET", "/api/tests/" + completed + "/results", token, null, 200)
    );
    assertEquals(
      originalSnapshot,
      db.queryForObject(
        "SELECT question_snapshot::text FROM test_sessions WHERE id=?",
        String.class,
        completed
      )
    );
    assertEquals(
      50,
      db.queryForObject(
        "SELECT (100*sum(earned_points)/sum(max_points))::integer FROM completed_question_activity WHERE session_id=?",
        Integer.class,
        completed
      )
    );
    assertEquals(
      "2026-01-01 12:00:00",
      db.queryForObject(
        "SELECT completed_at::text FROM test_sessions WHERE id=?",
        String.class,
        completed
      )
    );
  }

  @Test
  void legacySavedAnswerRetriesWithoutDuplicatingOrChangingScore()
    throws Exception {
    String token = token();
    var state = call("GET", "/api/tests/" + resume, token, null, 200);
    assertEquals(
      "Б",
      state.path("answers").get(0).path("selectedOptionId").asText()
    );
    call(
      "POST",
      "/api/tests/" + resume + "/answers",
      token,
      Map.of("questionId", firstQuestion, "selectedOptionId", "Б"),
      200
    );
    call(
      "POST",
      "/api/tests/" + resume + "/answers",
      token,
      Map.of("questionId", firstQuestion, "selectedOptionId", "А"),
      409
    );
    var finish = call(
      "POST",
      "/api/tests/" + resume + "/finish",
      token,
      null,
      200
    );
    assertEquals(0, finish.path("earnedPoints").asInt());
    assertEquals(1, finish.path("maxPoints").asInt());
    assertEquals(0, finish.path("score").asInt());
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM test_answers WHERE session_id=?",
        Integer.class,
        resume
      )
    );
  }

  @Test
  void unansweredLegacySnapshotAcceptsCyrillicIdsAfterMigration()
    throws Exception {
    String token = token();
    var question = call(
      "GET",
      "/api/tests/" + unanswered + "/questions/0",
      token,
      null,
      200
    );
    assertEquals("А", question.path("options").get(0).path("id").asText());
    assertFalse(question.path("assessment").has("correctOptionId"));
    call(
      "POST",
      "/api/tests/" + unanswered + "/answers",
      token,
      Map.of("questionId", firstQuestion, "selectedOptionId", "А"),
      200
    );
    var finish = call(
      "POST",
      "/api/tests/" + unanswered + "/finish",
      token,
      null,
      200
    );
    assertEquals(1, finish.path("earnedPoints").asInt());
    assertEquals(1, finish.path("maxPoints").asInt());
    assertEquals(100, finish.path("score").asInt());
  }
}
