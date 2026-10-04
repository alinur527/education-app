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
class ExpansionIntegrationTests {

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
      () -> "expansion-integration-key-only-01234567890123456789"
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

  Map<String, Object> pack(String namespace, String batch, List<?> rows) {
    return Map.of(
      "schema",
      "education-content-pack/v1",
      "namespace",
      namespace,
      "packVersion",
      "1",
      "batchKey",
      batch,
      "rows",
      rows
    );
  }

  Map<String, Object> row(
    String key,
    String kind,
    String parent,
    Map<String, Object> p
  ) {
    var row = new LinkedHashMap<String, Object>();
    row.put("externalKey", key);
    row.put("kind", kind);
    row.put("payload", p);
    if (parent != null) row.put("parentExternalKey", parent);
    return row;
  }

  JsonNode apply(Account actor, Object pack) throws Exception {
    var preview = call("POST", "/api/cms/content-packs", actor, pack, 200);
    assertEquals(
      "VALID",
      preview.path("status").asText(),
      preview.toPrettyString()
    );
    return call(
      "POST",
      "/api/cms/content-packs/" + preview.path("id").asText() + "/confirm",
      actor,
      null,
      200
    );
  }

  @Test
  void packAcrossBatchesIsIdempotentAndPreservesTeacherChanges()
    throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT"),
      teacher = account("TEACHER");
    String ns = "pack-" + UUID.randomUUID();
    var rows = List.of(
      row("subject", "SUBJECT", null, payload("Pack subject")),
      row("topic", "TOPIC", "subject", payload("Pack topic"))
    );
    call("POST", "/api/cms/content-packs", student, pack(ns, "one", rows), 403);
    call("POST", "/api/cms/content-packs", teacher, pack(ns, "one", rows), 403);
    var first = apply(admin, pack(ns, "one", rows));
    assertEquals(2, first.path("result").path("created").asInt());
    var repeated = apply(admin, pack(ns, "two", rows));
    assertEquals(2, repeated.path("result").path("unchanged").asInt());
    String sid = first
      .path("result")
      .path("items")
      .get(0)
      .path("contentId")
      .asText();
    assertEquals(
      sid,
      repeated.path("result").path("items").get(0).path("contentId").asText()
    );
    var subject = publish(
      admin,
      call("GET", "/api/cms/content/" + sid, admin, null, 200)
    );
    var edited = call(
      "PUT",
      "/api/cms/content/" + sid,
      admin,
      Map.of(
        "kind",
        "SUBJECT",
        "payload",
        payload("Teacher changed"),
        "version",
        subject.path("version").asLong()
      ),
      200
    );
    var incoming = List.of(
      row("subject", "SUBJECT", null, payload("Pack changed"))
    );
    var conflict = apply(admin, pack(ns, "three", incoming));
    assertEquals(1, conflict.path("result").path("conflicts").asInt());
    var retained = call("GET", "/api/cms/content/" + sid, admin, null, 200);
    assertEquals(edited.path("version"), retained.path("version"));
    assertEquals("Teacher changed", retained.path("titleRu").asText());
    assertEquals(
      "Pack subject",
      call("GET", "/api/content/" + sid, student, null, 200)
        .path("content")
        .path("titleRu")
        .asText()
    );
    var invalid = call(
      "POST",
      "/api/cms/content-packs",
      admin,
      pack(
        ns,
        "bad",
        List.of(
          row("badsubject", "SUBJECT", null, payload("Must rollback")),
          row("badquestion", "QUESTION", "badsubject", question())
        )
      ),
      200
    );
    assertEquals("INVALID", invalid.path("status").asText());
    call(
      "POST",
      "/api/cms/content-packs/" + invalid.path("id").asText() + "/confirm",
      admin,
      null,
      409
    );
    assertEquals(
      0,
      db.queryForObject(
        "SELECT count(*) FROM content_records WHERE title_ru='Must rollback'",
        Integer.class
      )
    );
    call(
      "POST",
      "/api/cms/content-packs",
      admin,
      pack(ns, "one", incoming),
      409
    );
  }

  @Test
  void typedSnapshotContextPartialScoreAndErrorReview() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Typed subject"))
    );
    String sid = subject.path("id").asText();
    var topic = publish(
      admin,
      create(admin, "TOPIC", sid, payload("Typed topic"))
    );
    String tid = topic.path("id").asText();
    var passage = payload("Passage");
    passage.put("contentRu", "Original passage");
    passage.put("contentKz", "Original passage KZ");
    passage.put("answerEvidence", "Private answer key: A and B");
    passage.put("correctOptionId", "A");
    passage.put("reviewChecks", Map.of("answerChecked", true));
    passage.put(
      "blocks",
      List.of(
        Map.of(
          "type",
          "TEXT",
          "textRu",
          "Public block",
          "textKz",
          "Жария блок",
          "answerEvidence",
          "Private nested key"
        )
      )
    );
    var context = publish(admin, create(admin, "CONTEXT", tid, passage));
    String cid = context.path("id").asText();
    var publicContext = call(
      "GET",
      "/api/content/" + cid,
      student,
      null,
      200
    ).path("content");
    assertFalse(publicContext.has("answerEvidence"));
    assertFalse(publicContext.has("correctOptionId"));
    assertFalse(publicContext.has("reviewChecks"));
    assertFalse(publicContext.path("blocks").get(0).has("answerEvidence"));
    var q = question();
    q.remove("correctOptionId");
    q.put("questionType", "MULTIPLE_SELECT");
    q.put("correctOptionIds", List.of("A", "B"));
    q.put("contextId", cid);
    q.put("contextVersion", context.path("publishedVersion").asLong());
    var question = publish(admin, create(admin, "QUESTION", tid, q));
    String qid = question.path("id").asText();
    passage.put("contentRu", "New passage");
    context = publish(
      admin,
      call(
        "PUT",
        "/api/cms/content/" + cid,
        admin,
        Map.of(
          "kind",
          "CONTEXT",
          "parentId",
          tid,
          "payload",
          passage,
          "version",
          context.path("version").asLong()
        ),
        200
      )
    );
    String session = call(
      "POST",
      "/api/tests/start",
      student,
      Map.of("topicId", tid),
      200
    )
      .path("sessionId")
      .asText();
    var visible = call(
      "GET",
      "/api/tests/" + session + "/questions/0",
      student,
      null,
      200
    );
    assertFalse(visible.path("assessment").has("correctOptionIds"));
    assertFalse(visible.path("context").has("answerEvidence"));
    assertFalse(visible.path("context").has("correctOptionId"));
    assertFalse(visible.path("context").has("reviewChecks"));
    assertFalse(
      visible.path("context").path("blocks").get(0).has("answerEvidence")
    );
    assertTrue(
      db.queryForObject(
        "SELECT context_snapshot::text LIKE '%Private answer key%' FROM test_sessions WHERE id=?",
        Boolean.class,
        UUID.fromString(session)
      ),
      "Raw immutable snapshot retains editorial source data internally"
    );
    assertEquals(
      "Original passage",
      visible.path("context").path("contentRu").asText()
    );
    call(
      "POST",
      "/api/tests/" + session + "/answers",
      student,
      Map.of(
        "questionId",
        qid,
        "answer",
        Map.of("selectedOptionIds", List.of("X"))
      ),
      400
    );
    var answer = Map.of(
      "questionId",
      qid,
      "answer",
      Map.of("selectedOptionIds", List.of("A"))
    );
    call("POST", "/api/tests/" + session + "/answers", student, answer, 200);
    call("POST", "/api/tests/" + session + "/answers", student, answer, 200);
    var score = call(
      "POST",
      "/api/tests/" + session + "/finish",
      student,
      null,
      200
    );
    assertEquals(1, score.path("earnedPoints").asInt());
    assertEquals(2, score.path("maxPoints").asInt());
    assertEquals(50, score.path("score").asInt());
    q.put("correctOptionIds", List.of("A"));
    q.put("contextVersion", context.path("publishedVersion").asLong());
    publish(
      admin,
      call(
        "PUT",
        "/api/cms/content/" + qid,
        admin,
        Map.of(
          "kind",
          "QUESTION",
          "parentId",
          tid,
          "payload",
          q,
          "version",
          question.path("version").asLong()
        ),
        200
      )
    );
    var frozen = call(
      "GET",
      "/api/tests/" + session + "/results",
      student,
      null,
      200
    );
    assertFalse(
      frozen.path("answers").get(0).path("context").has("answerEvidence")
    );
    assertEquals(
      2,
      frozen
        .path("answers")
        .get(0)
        .path("assessment")
        .path("correctOptionIds")
        .size()
    );
    String review = call(
      "POST",
      "/api/learning/errors/practice",
      student,
      Map.of("topicId", tid),
      200
    )
      .path("sessionId")
      .asText();
    assertEquals(
      "Original passage",
      call("GET", "/api/tests/" + review + "/questions/0", student, null, 200)
        .path("context")
        .path("contentRu")
        .asText()
    );
    call(
      "POST",
      "/api/tests/" + review + "/answers",
      student,
      Map.of(
        "questionId",
        qid,
        "answer",
        Map.of("selectedOptionIds", List.of("B", "A"))
      ),
      200
    );
    assertEquals(
      100,
      call("POST", "/api/tests/" + review + "/finish", student, null, 200)
        .path("score")
        .asInt()
    );
    call(
      "POST",
      "/api/cms/content/" + cid + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", context.path("version").asLong()),
      200
    );
    call("POST", "/api/tests/start", student, Map.of("topicId", tid), 409);
    assertEquals(
      "Original passage",
      call("GET", "/api/tests/" + session + "/results", student, null, 200)
        .path("answers")
        .get(0)
        .path("context")
        .path("contentRu")
        .asText()
    );
  }

  @Test
  void mixedAndTimedPracticeUseServerBankAndDeadline() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Mixed subject"))
    );
    String sid = subject.path("id").asText();
    var topic = publish(
      admin,
      create(admin, "TOPIC", sid, payload("Mixed topic"))
    );
    String tid = topic.path("id").asText();
    var q = publish(admin, create(admin, "QUESTION", tid, question()));
    String session = call(
      "POST",
      "/api/practice/sessions",
      student,
      Map.of("mode", "MIXED_PRACTICE", "subjectIds", List.of(sid), "count", 1),
      200
    )
      .path("sessionId")
      .asText();
    var state = call("GET", "/api/tests/" + session, student, null, 200);
    assertTrue(state.path("subjectId").isNull());
    assertEquals("MIXED_PRACTICE", state.path("practiceMode").asText());
    call(
      "POST",
      "/api/tests/" + session + "/answers",
      student,
      Map.of("questionId", q.path("id").asText(), "selectedOptionId", "A"),
      200
    );
    assertEquals(
      100,
      call("POST", "/api/tests/" + session + "/finish", student, null, 200)
        .path("score")
        .asInt()
    );
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM completed_question_activity WHERE session_id=? AND topic_id=? AND subject_id=?",
        Integer.class,
        UUID.fromString(session),
        UUID.fromString(tid),
        UUID.fromString(sid)
      )
    );
    call(
      "POST",
      "/api/practice/sessions",
      student,
      Map.of("mode", "MIXED_PRACTICE", "subjectIds", List.of(sid), "count", 50),
      409
    );
    assertFalse(
      call("GET", "/api/practice/exam-bank?profilePair=0", student, null, 200)
        .path("officialReady")
        .asBoolean()
    );
    call(
      "POST",
      "/api/practice/sessions",
      student,
      Map.of("mode", "MOCK_ENT", "profilePair", 0, "count", 50),
      409
    );
    String timed = call(
      "POST",
      "/api/practice/sessions",
      student,
      Map.of(
        "mode",
        "SHORTENED_ENT",
        "profilePair",
        0,
        "count",
        1,
        "minutes",
        1
      ),
      200
    )
      .path("sessionId")
      .asText();
    var visible = call(
      "GET",
      "/api/tests/" + timed + "/questions/0",
      student,
      null,
      200
    );
    db.update(
      "UPDATE test_sessions SET deadline_at=now()-interval '1 second' WHERE id=?",
      UUID.fromString(timed)
    );
    call(
      "POST",
      "/api/tests/" + timed + "/answers",
      student,
      Map.of(
        "questionId",
        visible.path("questionId").asText(),
        "selectedOptionId",
        visible.path("options").get(0).path("id").asText()
      ),
      409
    );
    assertEquals(
      "COMPLETED",
      call("GET", "/api/tests/" + timed, student, null, 200)
        .path("status")
        .asText()
    );
    assertEquals(
      0,
      call("GET", "/api/tests/" + timed + "/results", student, null, 200)
        .path("earnedPoints")
        .asInt()
    );
  }

  @Test
  void withdrawnContextDoesNotBlockRemainingErrors() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Error subject"))
    );
    var topic = publish(
      admin,
      create(
        admin,
        "TOPIC",
        subject.path("id").asText(),
        payload("Error topic")
      )
    );
    String tid = topic.path("id").asText();
    var passage = payload("Withdrawn passage");
    passage.put("contentRu", "Read me");
    passage.put("contentKz", "Мәтін");
    var context = publish(admin, create(admin, "CONTEXT", tid, passage));
    var contextual = question();
    contextual.put("contextId", context.path("id").asText());
    var old = publish(admin, create(admin, "QUESTION", tid, contextual));
    var remaining = publish(admin, create(admin, "QUESTION", tid, question()));
    String session = call(
      "POST",
      "/api/tests/start",
      student,
      Map.of("topicId", tid),
      200
    )
      .path("sessionId")
      .asText();
    for (var q : List.of(old, remaining))
      call(
        "POST",
        "/api/tests/" + session + "/answers",
        student,
        Map.of("questionId", q.path("id").asText(), "selectedOptionId", "B"),
        200
      );
    call("POST", "/api/tests/" + session + "/finish", student, null, 200);
    call(
      "POST",
      "/api/cms/content/" + context.path("id").asText() + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", context.path("version").asLong()),
      200
    );
    assertEquals(
      1,
      call("GET", "/api/learning/me", student, null, 200)
        .path("errorCount")
        .asInt()
    );
    var review = call(
      "POST",
      "/api/learning/errors/practice",
      student,
      Map.of("topicId", tid),
      200
    );
    assertEquals(1, review.path("totalQuestions").asInt());
    assertEquals(
      remaining.path("id").asText(),
      call(
        "GET",
        "/api/tests/" + review.path("sessionId").asText() + "/questions/0",
        student,
        null,
        200
      )
        .path("questionId")
        .asText()
    );
    assertEquals(
      2,
      call("GET", "/api/tests/" + session + "/results", student, null, 200)
        .path("answers")
        .size()
    );
  }

  @Test
  void packConflictDecisionsAreAuditedAndDuplicateTargetsAreInvalid()
    throws Exception {
    Account admin = account("ADMIN");
    String ns = "audit-" + UUID.randomUUID();
    var original = payload("Original");
    var target = create(admin, "SUBJECT", null, original);
    String id = target.path("id").asText();
    var alias1 = new LinkedHashMap<>(row("one", "SUBJECT", null, original));
    alias1.put("existingId", id);
    var alias2 = new LinkedHashMap<>(row("two", "SUBJECT", null, original));
    alias2.put("existingId", id);
    var duplicate = call(
      "POST",
      "/api/cms/content-packs",
      admin,
      pack(ns, "duplicate", List.of(alias1, alias2)),
      200
    );
    assertEquals("INVALID", duplicate.path("status").asText());
    assertEquals(
      "DUPLICATE_CONTENT_TARGET",
      duplicate.path("preview").get(1).path("error").asText()
    );
    call(
      "POST",
      "/api/cms/content-packs/" + duplicate.path("id").asText() + "/confirm",
      admin,
      null,
      409
    );
    apply(admin, pack(ns, "adopt", List.of(alias1)));
    var local = call(
      "PUT",
      "/api/cms/content/" + id,
      admin,
      Map.of(
        "kind",
        "SUBJECT",
        "payload",
        payload("Local"),
        "version",
        target.path("version").asLong()
      ),
      200
    );
    for (String decision : List.of("KEEP_LOCAL", "USE_INCOMING_DRAFT")) {
      var batch = apply(
        admin,
        pack(
          ns,
          decision,
          List.of(row("one", "SUBJECT", null, payload("Incoming")))
        )
      );
      UUID conflict = db.queryForObject(
        "SELECT id FROM content_update_candidates WHERE batch_id=?",
        UUID.class,
        UUID.fromString(batch.path("id").asText())
      );
      call(
        "POST",
        "/api/cms/content-packs/conflicts/" + conflict + "/resolve",
        admin,
        Map.of("decision", decision, "version", local.path("version").asLong()),
        200
      );
      assertEquals(
        1,
        db.queryForObject(
          "SELECT count(*) FROM audit_events WHERE entity_id=? AND operation=? AND actor_id=?",
          Integer.class,
          conflict,
          decision,
          UUID.fromString(admin.id())
        )
      );
      assertEquals(
        1,
        db.queryForObject(
          "SELECT count(*) FROM audit_events WHERE entity_id=? AND operation=?",
          Integer.class,
          UUID.fromString(id),
          "PACK_" + decision
        )
      );
    }
    assertEquals(
      "Incoming",
      call("GET", "/api/cms/content/" + id, admin, null, 200)
        .path("titleRu")
        .asText()
    );
  }

  @Test
  void catalogFiltersKeepPagingAndTeacherOwnership() throws Exception {
    Account teacher = account("TEACHER"),
      other = account("TEACHER");
    var own = course(teacher);
    var foreign = course(other);
    String parent = own.path("id").asText();
    create(teacher, "MODULE", parent, payload("Translated"));
    var untranslated = payload("Missing KZ");
    untranslated.put("titleKz", "  ");
    var missing = create(teacher, "MODULE", parent, untranslated);
    create(other, "MODULE", foreign.path("id").asText(), untranslated);
    var filtered = call(
      "GET",
      "/api/cms/content?kind=MODULE&parentId=" +
        parent +
        "&missingTranslation=true&page=0&size=1",
      teacher,
      null,
      200
    );
    assertEquals(1, filtered.path("total").asInt());
    assertEquals(missing.path("id"), filtered.path("items").get(0).path("id"));
    assertFalse(filtered.path("items").get(0).has("payload"));
    assertEquals(
      0,
      call(
        "GET",
        "/api/cms/content?parentId=" + foreign.path("id").asText(),
        teacher,
        null,
        200
      )
        .path("total")
        .asInt()
    );
    assertEquals(
      2,
      call("GET", "/api/cms/content?parentId=" + parent, teacher, null, 200)
        .path("total")
        .asInt()
    );
  }

  @Test
  void difficultyAndTopicFiltersSelectOnlyEligibleQuestions() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Filtered practice"))
    );
    String sid = subject.path("id").asText();
    var topic = publish(
      admin,
      create(admin, "TOPIC", sid, payload("Selected topic"))
    );
    String tid = topic.path("id").asText();
    var other = publish(
      admin,
      create(admin, "TOPIC", sid, payload("Other topic"))
    );
    var easy = question();
    easy.put("difficulty", "easy");
    var hard = question();
    hard.put("difficulty", "hard");
    var expected = publish(admin, create(admin, "QUESTION", tid, easy));
    publish(admin, create(admin, "QUESTION", tid, hard));
    publish(admin, create(admin, "QUESTION", other.path("id").asText(), easy));
    var request = new LinkedHashMap<String, Object>(
      Map.of(
        "mode",
        "MIXED_PRACTICE",
        "subjectIds",
        List.of(sid),
        "topicIds",
        List.of(tid),
        "difficulty",
        "easy",
        "count",
        1
      )
    );
    String session = call(
      "POST",
      "/api/practice/sessions",
      student,
      request,
      200
    )
      .path("sessionId")
      .asText();
    assertEquals(
      expected.path("id").asText(),
      call("GET", "/api/tests/" + session + "/questions/0", student, null, 200)
        .path("questionId")
        .asText()
    );
    request.put("count", 2);
    call("POST", "/api/practice/sessions", student, request, 409);
    request.put("difficulty", "impossible");
    call("POST", "/api/practice/sessions", student, request, 400);
    var catalog = call("GET", "/api/practice", student, null, 200);
    assertTrue(catalog.has("subjects"));
    assertTrue(catalog.has("topics"));
    assertTrue(catalog.has("configuration"));
  }

  @Test
  void expiredStateReadDoesNotOverwriteAConcurrentCompletion()
    throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Concurrent subject"))
    );
    var topic = publish(
      admin,
      create(
        admin,
        "TOPIC",
        subject.path("id").asText(),
        payload("Concurrent topic")
      )
    );
    publish(
      admin,
      create(admin, "QUESTION", topic.path("id").asText(), question())
    );
    UUID session = UUID.fromString(
      call(
        "POST",
        "/api/tests/start",
        student,
        Map.of("topicId", topic.path("id").asText()),
        200
      )
        .path("sessionId")
        .asText()
    );
    db.update(
      "UPDATE test_sessions SET deadline_at=now()-interval '1 second' WHERE id=?",
      session
    );
    try (
      var writer = java.sql.DriverManager.getConnection(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      );
      var executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
    ) {
      writer.setAutoCommit(false);
      try (
        var statement = writer.prepareStatement(
          "UPDATE test_sessions SET status='COMPLETED',completed_at=timestamp '2026-01-02 03:04:05',score=0,correct_answers=0,time_taken_secs=1 WHERE id=?"
        )
      ) {
        statement.setObject(1, session);
        statement.executeUpdate();
      }
      var response = executor.submit(() ->
        call("GET", "/api/tests/" + session, student, null, 200)
      );
      boolean waiting = false;
      try {
        for (int i = 0; i < 100 && !waiting; i++) {
          waiting = db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%test_sessions%')",
            Boolean.class
          );
          if (!waiting) Thread.sleep(20);
        }
      } finally {
        writer.commit();
      }
      assertTrue(waiting, "The request must overlap the pending completion");
      assertEquals(
        "COMPLETED",
        response
          .get(10, java.util.concurrent.TimeUnit.SECONDS)
          .path("status")
          .asText()
      );
      assertEquals(
        "2026-01-02 03:04:05",
        db.queryForObject(
          "SELECT completed_at::text FROM test_sessions WHERE id=?",
          String.class,
          session
        )
      );
    }
  }

  @Test
  void materialLibraryKeepsTeacherOwnershipAndStudentIsolation()
    throws Exception {
    Account owner = account("TEACHER"),
      other = account("TEACHER"),
      editor = account("CONTENT_EDITOR"),
      student = account("STUDENT");
    var ownCourse = course(owner);
    var otherCourse = course(other);
    String marker = "Library-" + UUID.randomUUID();
    for (var entry : Map.of(owner, ownCourse, other, otherCourse).entrySet()) {
      var response = mvc
        .perform(
          multipart("/api/cms/materials")
            .file(
              new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Private teaching notes".getBytes(
                  java.nio.charset.StandardCharsets.UTF_8
                )
              )
            )
            .param("contentId", entry.getValue().path("id").asText())
            .param("titleRu", marker)
            .header("Authorization", "Bearer " + entry.getKey().token())
        )
        .andReturn()
        .getResponse();
      assertEquals(200, response.getStatus(), response.getContentAsString());
    }
    var own = call("GET", "/api/cms/materials?q=" + marker, owner, null, 200);
    assertEquals(1, own.path("total").asInt());
    assertEquals(
      ownCourse.path("id"),
      own.path("items").get(0).path("contentId")
    );
    assertFalse(own.path("items").get(0).has("storageKey"));
    assertEquals(
      2,
      call("GET", "/api/cms/materials?q=" + marker, editor, null, 200)
        .path("total")
        .asInt()
    );
    call("GET", "/api/cms/materials?q=" + marker, student, null, 403);
    db.update(
      "UPDATE users SET role='STUDENT' WHERE id=?",
      UUID.fromString(owner.id())
    );
    call("GET", "/api/cms/materials?q=" + marker, owner, null, 403);
  }

  @Test
  void publicLearningPayloadsHideEditorialRationaleWithoutRemovingLearningFields()
    throws Exception {
    Account teacher = account("TEACHER"),
      student = account("STUDENT"),
      editor = account("CONTENT_EDITOR");
    var cp = payload("Learner course");
    cp.put("visibility", "PUBLIC");
    cp.put("selfEnroll", true);
    cp.put("answerEvidence", "Private course rationale");
    var course = publish(teacher, create(teacher, "COURSE", null, cp));
    String cid = course.path("id").asText();
    var module = publish(
      teacher,
      create(teacher, "MODULE", cid, payload("Module"))
    );
    var p = payload("Lesson with public content");
    p.put("contentRu", "Visible learning text");
    p.put("contentKz", "Оқу мәтіні");
    p.put("answerEvidence", "Private answer rationale");
    p.put("correctOptionId", "A");
    p.put("reviewChecks", Map.of("answerChecked", true));
    p.put("difficultyReason", "Private moderation note");
    p.put("sourceName", "Attribution");
    p.put("sourceUrl", "https://example.org/source");
    p.put(
      "blocks",
      List.of(Map.of("type", "FORMULA", "textRu", "x^2", "textKz", "x^2"))
    );
    var lesson = publish(
      teacher,
      create(teacher, "LESSON", module.path("id").asText(), p)
    );
    var group = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "Readers", "courseId", cid),
      200
    );
    call(
      "POST",
      "/api/teacher/groups/" + group.path("id").asText() + "/members",
      teacher,
      Map.of("email", student.email()),
      200
    );
    var ap = new LinkedHashMap<>(p);
    ap.put("titleRu", "Assignment");
    ap.put("maxScore", 10);
    ap.put("dueAt", "2026-12-01T18:00:00+05:00");
    var assignment = publish(teacher, create(teacher, "ASSIGNMENT", cid, ap));
    String aid = assignment.path("id").asText();
    call(
      "POST",
      "/api/teacher/groups/" + group.path("id").asText() + "/assignments",
      teacher,
      Map.of("assignmentId", aid),
      200
    );
    var subject = publish(
      editor,
      create(editor, "SUBJECT", null, payload("Theory subject"))
    );
    var topic = publish(
      editor,
      create(
        editor,
        "TOPIC",
        subject.path("id").asText(),
        payload("Theory topic")
      )
    );
    var theory = publish(
      editor,
      create(editor, "THEORY", topic.path("id").asText(), p)
    );
    for (String path : List.of(
      "/api/content/" + lesson.path("id").asText(),
      "/api/content/" + theory.path("id").asText(),
      "/api/content/" + aid,
      "/api/assignments/" + aid
    )) {
      var content = call("GET", path, student, null, 200).path("content");
      for (String hidden : List.of(
        "answerEvidence",
        "correctOptionId",
        "reviewChecks",
        "difficultyReason"
      ))
        assertFalse(content.has(hidden), path + " leaked " + hidden);
      assertEquals("Visible learning text", content.path("contentRu").asText());
      assertEquals(
        "FORMULA",
        content.path("blocks").get(0).path("type").asText()
      );
      assertEquals("Attribution", content.path("sourceName").asText());
      if (path.endsWith(aid)) {
        assertEquals(10, content.path("maxScore").asInt());
        assertEquals(ap.get("dueAt"), content.path("dueAt").asText());
      }
    }
    var publicCourse = call(
      "GET",
      "/api/courses/" + cid,
      student,
      null,
      200
    ).path("content");
    assertFalse(publicCourse.has("answerEvidence"));
    assertTrue(publicCourse.path("selfEnroll").asBoolean());
    assertEquals("PUBLIC", publicCourse.path("visibility").asText());
    assertEquals(
      "Private answer rationale",
      call("GET", "/api/cms/content/" + aid, teacher, null, 200)
        .path("payload")
        .path("answerEvidence")
        .asText()
    );
  }

  @Test
  void offlineExportIsAnExplicitPublicTheoryAllowlist() throws Exception {
    Account admin = account("ADMIN"),
      student = account("STUDENT");
    var subject = publish(
      admin,
      create(admin, "SUBJECT", null, payload("Offline subject"))
    );
    var topic = publish(
      admin,
      create(
        admin,
        "TOPIC",
        subject.path("id").asText(),
        payload("Offline topic")
      )
    );
    var p = payload("Offline theory");
    p.put("contentRu", "Public text");
    p.put("contentKz", "Жария мәтін");
    p.put("offlineAllowed", true);
    p.put("answerEvidence", "This editorial metadata must not be exported");
    var theory = publish(
      admin,
      create(admin, "THEORY", topic.path("id").asText(), p)
    );
    String id = theory.path("id").asText();
    var exported = call(
      "GET",
      "/api/offline/theories/" + id,
      student,
      null,
      200
    );
    assertEquals("PUBLIC_THEORY_V1", exported.path("policy").asText());
    assertFalse(exported.path("content").has("answerEvidence"));
    assertFalse(exported.path("content").has("createdBy"));
    var privateCourse = course(admin);
    var module = create(
      admin,
      "MODULE",
      privateCourse.path("id").asText(),
      payload("Module")
    );
    var lesson = create(admin, "LESSON", module.path("id").asText(), p);
    call(
      "GET",
      "/api/offline/theories/" + lesson.path("id").asText(),
      admin,
      null,
      404
    );
    var draft = create(admin, "THEORY", topic.path("id").asText(), p);
    call(
      "GET",
      "/api/offline/theories/" + draft.path("id").asText(),
      student,
      null,
      404
    );
    call(
      "POST",
      "/api/cms/content/" + id + "/transition",
      admin,
      Map.of("status", "ARCHIVED", "version", theory.path("version").asLong()),
      200
    );
    call("GET", "/api/offline/theories/" + id, student, null, 404);
  }
}
