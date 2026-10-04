package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.materials.*;
import ent.kz.entbackend.platform.submissions.SubmissionFileScanning;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
  properties = {
    "app.study.notifications.enabled=false",
    "app.submission-files.worker-enabled=false",
    "app.scan.engine=clamav",
  }
)
@AutoConfigureMockMvc
@Testcontainers
class SubmissionFilesIntegrationTests {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
    "postgres:17-alpine"
  );

  @Container
  static GenericContainer<?> clamav = new GenericContainer<>(
    "clamav/clamav:1.4"
  )
    .withExposedPorts(3310)
    .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
    .waitingFor(
      Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(5))
    );

  static String storageRoot =
    System.getProperty("java.io.tmpdir") +
    "/education-submission-test-" +
    UUID.randomUUID();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add(
      "app.jwt.secret",
      () -> "submission-integration-key-only-01234567890123456789"
    );
    r.add("app.storage.local-root", () -> storageRoot);
    r.add("app.scan.host", clamav::getHost);
    r.add("app.scan.port", () -> clamav.getMappedPort(3310));
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  @Autowired
  SubmissionFileScanning scanning;

  @Autowired
  MalwareScanner scanner;

  record Account(String token, String id, String email) {}

  record Fixture(
    Account teacher,
    Account student,
    String assignment,
    String group
  ) {}

  Account account(String role) throws Exception {
    String email = "files-" + UUID.randomUUID() + "@example.org";
    var a = call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        email,
        "password",
        "Files-password-2026",
        "firstName",
        "Тест",
        "lastName",
        "Файлы",
        "language",
        "ru"
      ),
      200
    );
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

  JsonNode content(Account owner, String kind, String parent) throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("kind", kind);
    body.put("parentId", parent);
    body.put(
      "payload",
      Map.of(
        "titleRu",
        "Материал " + UUID.randomUUID(),
        "titleKz",
        "Материал",
        "maxScore",
        10
      )
    );
    var c = call("POST", "/api/cms/content", owner, body, 200);
    var review = call(
      "POST",
      "/api/cms/content/" + c.path("id").asText() + "/transition",
      owner,
      Map.of("status", "REVIEW", "version", c.path("version").asLong()),
      200
    );
    return call(
      "POST",
      "/api/cms/content/" + c.path("id").asText() + "/transition",
      owner,
      Map.of("status", "PUBLISHED", "version", review.path("version").asLong()),
      200
    );
  }

  Fixture fixture() throws Exception {
    var teacher = account("TEACHER");
    var student = account("STUDENT");
    String course = content(teacher, "COURSE", null).path("id").asText();
    String assignment = content(teacher, "ASSIGNMENT", course)
      .path("id")
      .asText();
    String group = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "Группа файлов", "courseId", course),
      200
    )
      .path("id")
      .asText();
    call(
      "POST",
      "/api/teacher/groups/" + group + "/members",
      teacher,
      Map.of("email", student.email()),
      200
    );
    call(
      "POST",
      "/api/teacher/groups/" + group + "/assignments",
      teacher,
      Map.of("assignmentId", assignment),
      200
    );
    return new Fixture(teacher, student, assignment, group);
  }

  JsonNode upload(
    Fixture f,
    Account who,
    UUID key,
    String name,
    String mime,
    byte[] bytes,
    int expected
  ) throws Exception {
    var response = mvc
      .perform(
        multipart("/api/assignments/" + f.assignment() + "/files")
          .file(new MockMultipartFile("file", name, mime, bytes))
          .param("requestKey", key.toString())
          .header("Authorization", "Bearer " + who.token())
      )
      .andReturn()
      .getResponse();
    assertEquals(expected, response.getStatus(), response.getContentAsString());
    return json.readTree(response.getContentAsByteArray());
  }

  JsonNode upload(Fixture f) throws Exception {
    return upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "answer.txt",
      "text/plain",
      "Checked answer".getBytes(StandardCharsets.UTF_8),
      200
    );
  }

  Map<String, Object> answer(
    String text,
    List<String> files,
    UUID key,
    long revision
  ) {
    return Map.of(
      "text",
      text,
      "fileIds",
      files,
      "requestKey",
      key,
      "revision",
      revision
    );
  }

  int download(String id, Account user) throws Exception {
    return mvc
      .perform(
        get("/api/submission-files/" + id + "/download").header(
          "Authorization",
          "Bearer " + user.token()
        )
      )
      .andReturn()
      .getResponse()
      .getStatus();
  }

  @Test
  void realClamAvDetectsEicarAndQuarantineCannotBeSubmittedOrDownloaded()
    throws Exception {
    var f = fixture();
    var clean = upload(f);
    assertEquals("CLEAN", clean.path("scanStatus").asText());
    assertEquals(200, download(clean.path("id").asText(), f.student()));
    String eicar =
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}" +
      "$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
    var infected = upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "test.txt",
      "text/plain",
      eicar.getBytes(StandardCharsets.US_ASCII),
      200
    );
    assertEquals("INFECTED", infected.path("scanStatus").asText());
    assertEquals(409, download(infected.path("id").asText(), f.student()));
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer("", List.of(infected.path("id").asText()), UUID.randomUUID(), 0),
      409
    );
    String id = clean.path("id").asText();
    db.update(
      "UPDATE submission_files SET scan_status='UNSCANNED' WHERE id=?::uuid",
      id
    );
    assertEquals(409, download(id, f.student()));
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer("", List.of(id), UUID.randomUUID(), 0),
      409
    );
    assertEquals(
      MalwareScanner.Status.SCAN_FAILED,
      new ClamAvScanner("127.0.0.1", 1, 250)
        .scan(new byte[] { 1 }, "text/plain")
        .status()
    );
  }

  @Test
  void filesArePrivateUntilSubmissionAndOnlyCurrentAssignedTeacherCanRead()
    throws Exception {
    var f = fixture();
    var other = account("STUDENT");
    var editor = account("CONTENT_EDITOR");
    var teacher = account("TEACHER");
    var admin = account("ADMIN");
    var file = upload(f);
    call(
      "POST",
      "/api/teacher/groups/" + f.group() + "/members",
      f.teacher(),
      Map.of("email", other.email()),
      200
    );
    var foreign = upload(
      f,
      other,
      UUID.randomUUID(),
      "classmate.txt",
      "text/plain",
      "Private peer answer".getBytes(StandardCharsets.UTF_8),
      200
    );
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer(
        "Attempt",
        List.of(foreign.path("id").asText()),
        UUID.randomUUID(),
        0
      ),
      404
    );
    String id = file.path("id").asText();
    assertEquals(404, download(id, f.teacher()));
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer("", List.of(id), UUID.randomUUID(), 0),
      200
    );
    assertEquals(200, download(id, f.teacher()));
    assertEquals(200, download(id, admin));
    assertEquals(404, download(id, other));
    assertEquals(404, download(id, editor));
    assertEquals(404, download(id, teacher));
    call(
      "GET",
      "/api/assignments/" +
        f.assignment() +
        "/submission-history?userId=" +
        f.student().id(),
      other,
      null,
      404
    );
    call(
      "GET",
      "/api/assignments/" +
        f.assignment() +
        "/submission-history?userId=" +
        f.student().id(),
      editor,
      null,
      404
    );
    db.update(
      "UPDATE users SET role='STUDENT' WHERE id=?::uuid",
      f.teacher().id()
    );
    assertEquals(404, download(id, f.teacher()));
    db.update(
      "UPDATE users SET role='TEACHER' WHERE id=?::uuid",
      f.teacher().id()
    );
    call(
      "DELETE",
      "/api/teacher/groups/" + f.group() + "/members/" + f.student().id(),
      f.teacher(),
      null,
      200
    );
    assertEquals(404, download(id, f.teacher()));
    assertEquals(200, download(id, f.student()));
  }

  @Test
  void migrationPreservesOnlyTheRealLastLegacySubmissionAndGrade()
    throws Exception {
    var f = fixture();
    String schema = "legacy_submission_history";
    var base = org.flywaydb.core.Flyway.configure()
      .dataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
      .schemas(schema)
      .defaultSchema(schema)
      .locations("classpath:db/migration")
      .target("18")
      .load();
    base.migrate();
    String url =
      postgres.getJdbcUrl() +
      (postgres.getJdbcUrl().contains("?") ? "&" : "?") +
      "currentSchema=" +
      schema;
    var legacy = new JdbcTemplate(
      new org.springframework.jdbc.datasource.DriverManagerDataSource(
        url,
        postgres.getUsername(),
        postgres.getPassword()
      )
    );
    legacy.update(
      "INSERT INTO users SELECT * FROM public.users WHERE id IN (?::uuid,?::uuid)",
      f.teacher().id(),
      f.student().id()
    );
    legacy.update(
      "INSERT INTO content_records SELECT * FROM public.content_records WHERE id IN (?::uuid,(SELECT course_id FROM public.assignments WHERE id=?::uuid))",
      f.assignment(),
      f.assignment()
    );
    legacy.update(
      "INSERT INTO courses SELECT * FROM public.courses WHERE id=(SELECT course_id FROM public.assignments WHERE id=?::uuid)",
      f.assignment()
    );
    legacy.update(
      "INSERT INTO assignments SELECT * FROM public.assignments WHERE id=?::uuid",
      f.assignment()
    );
    legacy.update(
      "INSERT INTO assignment_submissions(assignment_id,user_id,text,score,feedback,graded_by,graded_at,revision) VALUES (?::uuid,?::uuid,'Legacy last answer',8,'Legacy grade',?::uuid,now(),7)",
      f.assignment(),
      f.student().id(),
      f.teacher().id()
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
    var current = legacy.queryForMap(
      "SELECT * FROM assignment_submissions WHERE assignment_id=?::uuid",
      f.assignment()
    );
    assertEquals(7L, ((Number) current.get("revision")).longValue());
    assertEquals(1L, ((Number) current.get("content_revision")).longValue());
    assertEquals(8, current.get("score"));
    assertEquals(
      1,
      legacy.queryForObject(
        "SELECT count(*) FROM submission_revisions",
        Integer.class
      )
    );
    assertEquals(
      1,
      legacy.queryForObject(
        "SELECT count(*) FROM submission_grade_history",
        Integer.class
      )
    );
    assertTrue(
      legacy.queryForObject(
        "SELECT legacy_imported FROM submission_revisions",
        Boolean.class
      )
    );
    assertEquals(
      "Legacy last answer",
      legacy.queryForObject(
        "SELECT text FROM submission_revisions",
        String.class
      )
    );
    assertEquals(
      "Legacy grade",
      legacy.queryForObject(
        "SELECT feedback FROM submission_grade_history",
        String.class
      )
    );
  }

  @Test
  void immutableSubmissionAndGradeHistorySurviveResubmissionAndIdempotentRetries()
    throws Exception {
    var f = fixture();
    String file = upload(f).path("id").asText();
    UUID firstKey = UUID.randomUUID();
    var first = answer("Первый ответ", List.of(file), firstKey, 0);
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      first,
      200
    );
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      first,
      200
    );
    call(
      "POST",
      "/api/teacher/assignments/" +
        f.assignment() +
        "/submissions/" +
        f.student().id() +
        "/grade",
      f.teacher(),
      Map.of("score", 8, "feedback", "Первая оценка", "revision", 1),
      200
    );
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer("Второй ответ", List.of(), UUID.randomUUID(), 2),
      200
    );
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      first,
      200
    );
    call(
      "POST",
      "/api/assignments/" + f.assignment() + "/submit",
      f.student(),
      answer("Изменено", List.of(file), firstKey, 0),
      409
    );
    var current = call(
      "GET",
      "/api/assignments/" + f.assignment(),
      f.student(),
      null,
      200
    ).path("submission");
    assertEquals(2, current.path("contentRevision").asInt());
    assertEquals(3, current.path("revision").asInt());
    assertTrue(current.path("score").isNull());
    assertEquals("Второй ответ", current.path("text").asText());
    var history = call(
      "GET",
      "/api/assignments/" + f.assignment() + "/submission-history",
      f.student(),
      null,
      200
    );
    assertEquals(2, history.path("total").asInt());
    var old = history.path("items").get(1);
    assertEquals("Первый ответ", old.path("text").asText());
    assertEquals(file, old.path("files").get(0).path("id").asText());
    assertEquals(8, old.path("grades").get(0).path("score").asInt());
    assertEquals(10, old.path("grades").get(0).path("maxScore").asInt());
    call("DELETE", "/api/submission-files/" + file, f.student(), null, 409);
    call(
      "POST",
      "/api/teacher/assignments/" +
        f.assignment() +
        "/submissions/" +
        f.student().id() +
        "/grade",
      f.teacher(),
      Map.of("score", 10, "feedback", "Устаревшая вкладка", "revision", 1),
      409
    );
  }

  @Test
  void uploadIdempotencyMimeAndQuotasCannotBeBypassedByDeletingStagedFiles()
    throws Exception {
    var f = fixture();
    UUID key = UUID.randomUUID();
    byte[] bytes = "Answer".getBytes(StandardCharsets.UTF_8);
    var first = upload(
      f,
      f.student(),
      key,
      "answer.txt",
      "text/plain",
      bytes,
      200
    );
    var retry = upload(
      f,
      f.student(),
      key,
      "answer.txt",
      "text/plain",
      bytes,
      200
    );
    assertEquals(first.path("id"), retry.path("id"));
    upload(
      f,
      f.student(),
      key,
      "answer.txt",
      "text/plain",
      "Other".getBytes(StandardCharsets.UTF_8),
      409
    );
    upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "../answer.txt",
      "text/plain",
      bytes,
      400
    );
    upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "answer.pdf",
      "application/pdf",
      bytes,
      400
    );
    upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "answer.txt",
      "application/pdf",
      bytes,
      400
    );
    call(
      "DELETE",
      "/api/submission-files/" + first.path("id").asText(),
      f.student(),
      null,
      200
    );
    assertEquals(404, download(first.path("id").asText(), f.student()));
    upload(f, f.student(), key, "answer.txt", "text/plain", bytes, 409);
    db.update(
      "INSERT INTO submission_files(id,user_id,assignment_id,request_key,storage_key,original_file_name,mime_type,size,sha256,scan_status,deleted_at) SELECT gen_random_uuid(),?::uuid,?::uuid,gen_random_uuid(),gen_random_uuid()::text,'deleted.txt','text/plain',1,repeat('a',64),'UNSCANNED',now() FROM generate_series(1,49)",
      f.student().id(),
      f.assignment()
    );
    upload(
      f,
      f.student(),
      UUID.randomUUID(),
      "limited.txt",
      "text/plain",
      bytes,
      429
    );
  }

  @Test
  void rescansUseSameStoredBytesAndIntegrityMismatchFailsClosed()
    throws Exception {
    var f = fixture();
    var file = upload(f);
    UUID id = UUID.fromString(file.path("id").asText());
    String key = db.queryForObject(
      "SELECT storage_key FROM submission_files WHERE id=?",
      String.class,
      id
    );
    byte[] original = Files.readAllBytes(Path.of(storageRoot, key));
    assertEquals(file.path("sha256").asText(), FileDigests.sha256(original));
    db.update(
      "UPDATE submission_files SET scan_status='SCAN_FAILED',scanned_at=now()-interval '1 minute' WHERE id=?",
      id
    );
    assertEquals("CLEAN", scanning.scan(id, true).get("scanStatus"));
    assertArrayEquals(original, Files.readAllBytes(Path.of(storageRoot, key)));
    Files.writeString(
      Path.of(storageRoot, key),
      "Tampered bytes",
      StandardCharsets.UTF_8
    );
    assertEquals(409, download(id.toString(), f.student()));
    db.update(
      "UPDATE submission_files SET scanned_at=now()-interval '1 minute' WHERE id=?",
      id
    );
    var rescanned = scanning.scan(id, true);
    assertEquals("SCAN_FAILED", rescanned.get("scanStatus"));
    assertEquals("HASH_MISMATCH", rescanned.get("scanMessage"));
  }
}
