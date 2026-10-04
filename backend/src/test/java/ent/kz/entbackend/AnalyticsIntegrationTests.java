package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AnalyticsIntegrationTests {

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
      () -> "analytics-integration-key-only-01234567890123456789"
    );
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  @MockitoBean(name = "analyticsClock")
  Clock clock;

  static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

  @BeforeEach
  void fixedClock() {
    when(clock.instant()).thenReturn(NOW);
  }

  record Account(UUID id, String token) {}

  Account account(String role) throws Exception {
    var a = call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        "analytics-" + UUID.randomUUID() + "@example.org",
        "password",
        "Analytics-test-2026",
        "firstName",
        "Тест",
        "lastName",
        "Ученик",
        "language",
        "ru"
      ),
      200
    );
    UUID id = UUID.fromString(a.path("user").path("id").asText());
    db.update("UPDATE users SET role=? WHERE id=?", role, id);
    return new Account(id, a.path("token").asText());
  }

  JsonNode call(
    String method,
    String path,
    Account a,
    Object body,
    int expected
  ) throws Exception {
    var req = method.equals("POST") ? post(path) : get(path);
    if (a != null) req.header("Authorization", "Bearer " + a.token);
    if (body != null) req
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsBytes(body));
    var res = mvc.perform(req).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), res.getContentAsString());
    return res.getContentAsByteArray().length == 0
      ? json.nullNode()
      : json.readTree(res.getContentAsByteArray());
  }

  List<UUID> questions() {
    var ids = db.queryForList(
      "SELECT id FROM questions ORDER BY id LIMIT 10",
      UUID.class
    );
    while (ids.size() < 10) {
      UUID id = UUID.randomUUID();
      db.update(
        "INSERT INTO questions(id,subject_id,topic_id,question_ru,question_kz,options,correct_option_id) VALUES (?,?,?,'Analytics fixture','Тест','[]','A')",
        id,
        subject(),
        topic()
      );
      ids.add(id);
    }
    return db.queryForList(
      "SELECT id FROM questions ORDER BY id LIMIT 10",
      UUID.class
    );
  }

  UUID topic() {
    return db.queryForObject(
      "SELECT topic_id FROM questions WHERE topic_id IS NOT NULL ORDER BY id LIMIT 1",
      UUID.class
    );
  }

  UUID subject() {
    return db.queryForObject(
      "SELECT subject_id FROM topics WHERE id=?",
      UUID.class,
      topic()
    );
  }

  UUID session(
    Account a,
    String instant,
    int[] earned,
    int[] max,
    boolean[] fullyCorrect,
    int seconds
  ) throws Exception {
    var ids = questions();
    UUID id = UUID.randomUUID();
    var snapshots = json.createArrayNode();
    for (int i = 0; i < earned.length; i++) snapshots
      .addObject()
      .put("id", ids.get(i).toString())
      .put("topicId", topic().toString())
      .put("subjectId", subject().toString())
      .putObject("assessment")
      .put("maxPoints", max[i]);
    LocalDateTime at = LocalDateTime.ofInstant(
      Instant.parse(instant),
      ZoneOffset.UTC
    );
    db.update(
      "INSERT INTO test_sessions(id,user_id,subject_id,topic_id,status,question_ids,question_snapshot,total_questions,correct_answers,score,time_taken_secs,started_at,completed_at) VALUES (?,?,?,?,'COMPLETED','[]',?::jsonb,?,?,0,?,?,?)",
      id,
      a.id,
      subject(),
      topic(),
      snapshots.toString(),
      earned.length,
      0,
      seconds,
      at.minusSeconds(seconds),
      at
    );
    for (int i = 0; i < earned.length; i++) if (earned[i] >= 0) db.update(
      "INSERT INTO test_answers(session_id,question_id,is_correct,earned_points,max_points) VALUES (?,?,?,?,?)",
      id,
      ids.get(i),
      fullyCorrect[i],
      earned[i],
      max[i]
    );
    return id;
  }

  JsonNode analytics(Account a, String period) throws Exception {
    return call(
      "GET",
      "/api/statistics/me/analytics?period=" + period,
      a,
      null,
      200
    );
  }

  @Test
  void emptyUserHasNullAccuracyFullBucketsAndNoComparison() throws Exception {
    var a = account("STUDENT");
    var result = analytics(a, "7d");
    assertEquals("Asia/Almaty", result.path("timeZone").asText());
    assertEquals(7, result.path("daily").size());
    assertEquals(84, result.path("heatmap").size());
    assertTrue(result.path("summary").path("accuracyPercent").isNull());
    assertTrue(result.path("summary").path("pointsPercent").isNull());
    assertFalse(result.path("comparison").path("available").asBoolean());
    assertTrue(result.path("daily").get(0).path("accuracy").isNull());
    assertEquals(1, analytics(a, "all").path("daily").size());
  }

  @Test
  void timezoneBoundaryPreviousWindowPartialAndUnansweredAreCorrect()
    throws Exception {
    var a = account("STUDENT");
    session(
      a,
      "2026-10-08T18:59:59Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      30
    ); // Almaty Oct 8: previous
    session(
      a,
      "2026-10-08T19:00:00Z",
      new int[] { 1, 1, -1 },
      new int[] { 1, 2, 1 },
      new boolean[] { true, false, false },
      90
    ); // Oct 9 current
    session(
      a,
      "2026-10-15T09:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      10
    ); // future excluded
    var result = analytics(a, "7d");
    var s = result.path("summary");
    assertEquals("2026-10-09", result.path("from").asText());
    assertEquals(3, s.path("questionsAnswered").asInt());
    assertEquals(1, s.path("fullyCorrectAnswers").asInt());
    assertEquals(33.33, s.path("accuracyPercent").asDouble());
    assertEquals(2, s.path("earnedPoints").asInt());
    assertEquals(4, s.path("maxPoints").asInt());
    assertEquals(50, s.path("pointsPercent").asDouble());
    assertEquals(90, s.path("testTimeSecs").asInt());
    assertEquals(1, s.path("activeDays").asInt());
    assertEquals(2, result.path("comparison").path("questionsDelta").asInt());
    assertEquals(
      -66.67,
      result.path("comparison").path("accuracyDelta").asDouble()
    );
    assertEquals(
      -50,
      result.path("comparison").path("pointsPercentDelta").asDouble()
    );
    assertEquals(
      3,
      result.path("daily").get(0).path("questionsAnswered").asInt()
    );
    assertTrue(result.path("daily").get(1).path("accuracy").isNull());
    assertEquals(
      33.33,
      result.path("subjects").get(0).path("accuracyPercent").asDouble()
    );
    assertEquals(
      4,
      analytics(a, "30d").path("summary").path("questionsAnswered").asInt()
    );
    assertEquals(
      4,
      analytics(a, "all").path("summary").path("questionsAnswered").asInt()
    );
  }

  @Test
  void userTimezoneUsesDstMidnightsAndIndependentClock() throws Exception {
    var a = account("STUDENT");
    db.update(
      "INSERT INTO study_profiles(user_id,target_date,available_days,time_zone,minutes_per_day) VALUES (?,'2027-01-01','[1]','America/New_York',60)",
      a.id
    );
    when(clock.instant()).thenReturn(Instant.parse("2026-11-02T12:00:00Z"));
    session(
      a,
      "2026-11-01T03:59:59Z",
      new int[] { 0 },
      new int[] { 1 },
      new boolean[] { false },
      1
    );
    session(
      a,
      "2026-11-01T04:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      1
    );
    session(
      a,
      "2026-11-02T04:59:59Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      1
    );
    var r = analytics(a, "7d");
    var days = r.path("daily");
    assertEquals("2026-11-01", days.get(5).path("date").asText());
    assertEquals(2, days.get(5).path("questionsAnswered").asInt());
    assertEquals(0, days.get(6).path("questionsAnswered").asInt());
    assertEquals(2, r.path("summary").path("currentStreak").asInt());
  }

  @Test
  void acceptedFixedOffsetsMatchJavaDateBoundaries() throws Exception {
    for (String zone : List.of("+05:00", "UTC+05:30", "-04:00")) {
      var a = account("STUDENT");
      db.update(
        "INSERT INTO study_profiles(user_id,target_date,available_days,time_zone,minutes_per_day) VALUES (?,'2027-01-01','[1]',?,60)",
        a.id,
        zone
      );
      ZoneId z = ZoneId.of(zone);
      Instant start = NOW.atZone(z)
        .toLocalDate()
        .minusDays(6)
        .atStartOfDay(z)
        .toInstant();
      session(
        a,
        start.toString(),
        new int[] { 1 },
        new int[] { 1 },
        new boolean[] { true },
        1
      );
      var r = analytics(a, "7d");
      assertEquals(1, r.path("daily").get(0).path("questionsAnswered").asInt());
      assertEquals(1, r.path("summary").path("questionsAnswered").asInt());
      assertEquals(1, r.path("summary").path("activeDays").asInt());
    }
  }

  @Test
  void streakResolvedErrorsAndPaginatedHistoryDoNotLeakOtherUsers()
    throws Exception {
    Account a = account("STUDENT"),
      other = account("STUDENT");
    session(
      a,
      "2026-10-13T08:00:00Z",
      new int[] { 0 },
      new int[] { 1 },
      new boolean[] { false },
      5
    );
    session(
      a,
      "2026-10-14T08:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      5
    );
    session(
      a,
      "2026-10-15T08:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      5
    );
    session(
      other,
      "2026-10-15T08:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      500
    );
    var s = analytics(a, "7d").path("summary");
    assertEquals(3, s.path("activeDays").asInt());
    assertEquals(3, s.path("currentStreak").asInt());
    assertEquals(1, s.path("errorsResolved").asInt());
    assertEquals(15, s.path("testTimeSecs").asInt());
    var h = call(
      "GET",
      "/api/statistics/me/activity?kind=TEST&size=1&page=1",
      a,
      null,
      200
    );
    assertEquals(3, h.path("total").asInt());
    assertEquals(1, h.path("items").size());
    assertEquals(
      "2026-10-14T08:00:00Z",
      h.path("items").get(0).path("occurredAt").asText()
    );
    assertEquals(
      1,
      call(
        "GET",
        "/api/statistics/me/activity?kind=ERROR_RESOLVED",
        a,
        null,
        200
      )
        .path("total")
        .asInt()
    );
  }

  @Test
  void strongTopicRequiresDistinctQuestionEvidence() throws Exception {
    var a = account("STUDENT");
    for (int i = 0; i < 3; i++) session(
      a,
      "2026-10-14T0" + i + ":00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      1
    );
    assertEquals(0, analytics(a, "7d").path("strongTopics").size());
    var ten = new int[10];
    Arrays.fill(ten, 1);
    var correct = new boolean[10];
    Arrays.fill(correct, true);
    session(a, "2026-10-15T07:00:00Z", ten, ten, correct, 1);
    assertEquals(1, analytics(a, "7d").path("strongTopics").size());
  }

  @Test
  void completionDatesSurviveTaskMovesAndTheoryReReads() throws Exception {
    var a = account("STUDENT");
    UUID task = UUID.randomUUID(),
      theory = db.queryForObject(
        "SELECT id FROM theories WHERE is_active LIMIT 1",
        UUID.class
      );
    db.update(
      "INSERT INTO study_tasks(id,user_id,source_key,kind,target_kind,target_id,title_ru,title_kz,reason,duration_minutes,status,completed_at) VALUES (?,?,'analytics','THEORY','TOPIC',?,'Тема','Тақырып','WEAK',15,'COMPLETED',?)",
      task,
      a.id,
      topic(),
      java.sql.Timestamp.from(NOW.minusSeconds(86400))
    );
    db.update(
      "UPDATE study_tasks SET updated_at=now(),scheduled_at=now() WHERE id=?",
      task
    );
    var before = db.queryForObject(
      "SELECT completed_at FROM study_tasks WHERE id=?",
      java.sql.Timestamp.class,
      task
    );
    assertEquals(NOW.minusSeconds(86400), before.toInstant());
    db.update(
      "INSERT INTO user_theory_progress(user_id,theory_id,is_read,read_at) VALUES (?,?,true,'2026-10-14T08:00:00')",
      a.id,
      theory
    );
    call("POST", "/api/learning/theories/" + theory + "/read", a, null, 200);
    assertEquals(
      1,
      analytics(a, "7d").path("summary").path("theoriesRead").asInt()
    );
    assertEquals(
      1,
      analytics(a, "7d").path("summary").path("plannerTasksCompleted").asInt()
    );
    db.update("UPDATE study_tasks SET status='PLANNED' WHERE id=?", task);
    assertNull(
      db.queryForObject(
        "SELECT completed_at FROM study_tasks WHERE id=?",
        java.sql.Timestamp.class,
        task
      )
    );
  }

  @Test
  void validationAndAuthenticationAreEnforced() throws Exception {
    var a = account("STUDENT");
    call("GET", "/api/statistics/me/analytics", null, null, 401);
    call("GET", "/api/statistics/me/activity", null, null, 401);
    call("GET", "/api/statistics/me/analytics?period=bad", a, null, 400);
    call("GET", "/api/statistics/me/activity?size=100", a, null, 400);
    call("GET", "/api/statistics/me/activity?kind=USER", a, null, 400);
    call("GET", "/api/teacher/analytics", a, null, 403);
    call("GET", "/api/teacher/analytics", account("CONTENT_EDITOR"), null, 403);
    call(
      "GET",
      "/api/teacher/analytics?period=all",
      account("ADMIN"),
      null,
      400
    );
    assertTrue(
      call("GET", "/api/statistics/me", a, null, 200).has("recentAttempts")
    );
  }

  @Test
  void teacherWithoutRosterSeesNoGlobalActivity() throws Exception {
    Account student = account("STUDENT"),
      teacher = account("TEACHER"),
      admin = account("ADMIN");
    session(
      student,
      "2026-10-15T08:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      20
    );
    var t = call("GET", "/api/teacher/analytics", teacher, null, 200);
    assertEquals(0, t.path("questionsAnswered").asInt());
    assertEquals(0, t.path("studentsInScope").asInt());
    assertFalse(t.path("global").asBoolean());
    assertEquals(0, t.path("coverage").size());
    assertTrue(
      call("GET", "/api/teacher/analytics", admin, null, 200)
        .path("questionsAnswered")
        .asInt() > 0
    );
  }

  UUID content(String kind, UUID parent, Account owner) {
    UUID id = UUID.randomUUID();
    String payload = "{\"titleRu\":\"Analytics fixture\",\"titleKz\":\"Тест\"}";
    db.update(
      "INSERT INTO content_records(id,kind,parent_id,owner_id,title_ru,title_kz,payload,published_payload,status,created_at) VALUES (?,?,?,?,'Analytics fixture','Тест',?::jsonb,?::jsonb,'PUBLISHED','2026-10-01T00:00:00Z')",
      id,
      kind,
      parent,
      owner.id,
      payload,
      payload
    );
    return id;
  }

  @Test
  void teacherSeesOnlyOwnedRosterCoursesAndAssignmentPairs() throws Exception {
    Account teacher = account("TEACHER"),
      foreignTeacher = account("TEACHER"),
      student = account("STUDENT"),
      foreignStudent = account("STUDENT");
    UUID course = content("COURSE", null, teacher),
      otherCourse = content("COURSE", null, foreignTeacher);
    db.update(
      "INSERT INTO courses(id,teacher_id,title_ru) VALUES (?,?,'My course'),(?,?,'Other course')",
      course,
      teacher.id,
      otherCourse,
      foreignTeacher.id
    );
    db.update(
      "INSERT INTO enrollments(course_id,user_id,created_at) VALUES (?,?,'2026-10-14T00:00:00Z'),(?,?,'2026-10-14T00:00:00Z')",
      course,
      student.id,
      otherCourse,
      foreignStudent.id
    );
    UUID group = UUID.randomUUID(),
      otherGroup = UUID.randomUUID();
    db.update(
      "INSERT INTO learning_groups(id,name,teacher_id,course_id) VALUES (?,'Mine',?,?),(?,'Other',?,?)",
      group,
      teacher.id,
      course,
      otherGroup,
      foreignTeacher.id,
      otherCourse
    );
    db.update(
      "INSERT INTO group_members(group_id,user_id) VALUES (?,?),(?,?)",
      group,
      student.id,
      otherGroup,
      foreignStudent.id
    );
    UUID assignment = content("ASSIGNMENT", course, teacher),
      foreignAssignment = content("ASSIGNMENT", otherCourse, foreignTeacher);
    db.update(
      "INSERT INTO assignments(id,course_id,teacher_id) VALUES (?,?,?),(?,?,?)",
      assignment,
      course,
      teacher.id,
      foreignAssignment,
      otherCourse,
      foreignTeacher.id
    );
    db.update(
      "INSERT INTO assignment_groups(assignment_id,group_id) VALUES (?,?),(?,?)",
      assignment,
      group,
      foreignAssignment,
      otherGroup
    );
    db.update(
      "INSERT INTO submission_revisions(assignment_id,user_id,content_revision,submitted_at) VALUES (?,?,1,'2026-10-14T07:00:00Z'),(?,?,2,'2026-10-14T08:00:00Z'),(?,?,1,'2026-10-14T07:00:00Z')",
      assignment,
      student.id,
      assignment,
      student.id,
      foreignAssignment,
      foreignStudent.id
    );
    UUID module = content("MODULE", course, teacher),
      lesson = content("LESSON", module, teacher);
    db.update(
      "INSERT INTO course_modules(id,course_id,title_ru) VALUES (?,?,'Module')",
      module,
      course
    );
    db.update(
      "INSERT INTO lessons(id,module_id,title_ru) VALUES (?,?,'Lesson')",
      lesson,
      module
    );
    db.update(
      "INSERT INTO lesson_progress(user_id,lesson_id,completed_at) VALUES (?,?,'2026-10-14T09:00:00Z')",
      student.id,
      lesson
    );
    session(
      student,
      "2026-10-14T08:00:00Z",
      new int[] { 1 },
      new int[] { 1 },
      new boolean[] { true },
      10
    );
    session(
      foreignStudent,
      "2026-10-14T08:00:00Z",
      new int[] { 1, 1, 1 },
      new int[] { 1, 1, 1 },
      new boolean[] { true, true, true },
      10
    );
    var t = call("GET", "/api/teacher/analytics", teacher, null, 200);
    assertEquals(1, t.path("studentsInScope").asInt());
    assertEquals(1, t.path("activeStudents").asInt());
    assertEquals(1, t.path("questionsAnswered").asInt());
    assertEquals(1, t.path("courseEnrollments").asInt());
    assertEquals(1, t.path("assignmentRecipients").asInt());
    assertEquals(1, t.path("assignmentSubmitters").asInt());
    assertEquals(100, t.path("assignmentSubmissionRate").asInt());
    var s = analytics(student, "7d").path("summary");
    assertEquals(2, s.path("assignmentsSubmitted").asInt());
    assertEquals(1, s.path("lessonsCompleted").asInt());
    assertEquals(
      2,
      call(
        "GET",
        "/api/statistics/me/activity?kind=ASSIGNMENT",
        student,
        null,
        200
      )
        .path("total")
        .asInt()
    );
  }

  @Test
  void sharedStudentPrivatePlanAndForeignCourseEventsAreNotTeacherActivity()
    throws Exception {
    Account a = account("TEACHER"),
      b = account("TEACHER"),
      student = account("STUDENT");
    UUID ca = content("COURSE", null, a),
      cb = content("COURSE", null, b);
    db.update(
      "INSERT INTO courses(id,teacher_id,title_ru) VALUES (?,?,'A'),(?,?,'B')",
      ca,
      a.id,
      cb,
      b.id
    );
    db.update(
      "INSERT INTO enrollments(course_id,user_id) VALUES (?,?),(?,?)",
      ca,
      student.id,
      cb,
      student.id
    );
    UUID assignment = content("ASSIGNMENT", cb, b);
    db.update(
      "INSERT INTO assignments(id,course_id,teacher_id) VALUES (?,?,?)",
      assignment,
      cb,
      b.id
    );
    db.update(
      "INSERT INTO submission_revisions(assignment_id,user_id,content_revision,submitted_at) VALUES (?,?,1,'2026-10-14T07:00:00Z')",
      assignment,
      student.id
    );
    db.update(
      "INSERT INTO study_tasks(id,user_id,source_key,kind,target_kind,target_id,title_ru,title_kz,reason,duration_minutes,status,completed_at) VALUES (?,?,'private','THEORY','TOPIC',?,'Private','Private','WEAK',10,'COMPLETED','2026-10-14T08:00:00Z')",
      UUID.randomUUID(),
      student.id,
      topic()
    );
    assertEquals(
      0,
      call("GET", "/api/teacher/analytics", a, null, 200)
        .path("activeStudents")
        .asInt()
    );
    assertEquals(
      1,
      call("GET", "/api/teacher/analytics", b, null, 200)
        .path("activeStudents")
        .asInt()
    );
  }

  @Test
  void futureAttemptDoesNotCreateAWeakTopic() throws Exception {
    var a = account("STUDENT");
    session(
      a,
      "2026-10-16T08:00:00Z",
      new int[] { 0 },
      new int[] { 1 },
      new boolean[] { false },
      1
    );
    var r = analytics(a, "7d");
    assertEquals(0, r.path("weakTopics").size());
    assertEquals(0, r.path("summary").path("questionsAnswered").asInt());
    assertFalse(r.path("hasPracticeHistory").asBoolean());
  }

  @Test
  void v22UpgradePreservesExistingResultsAndBackfillsOnlyKnownCompletionDate() {
    String schema = "analytics_upgrade";
    var config = org.flywaydb.core.Flyway.configure()
      .dataSource(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
      )
      .schemas(schema)
      .defaultSchema(schema)
      .locations("classpath:db/migration");
    config.target("22").load().migrate();
    UUID user = UUID.randomUUID(),
      task = UUID.randomUUID();
    db.update(
      "INSERT INTO analytics_upgrade.users(id,email,password_hash,role) VALUES (?,'upgrade-analytics@example.org','unusable','STUDENT')",
      user
    );
    db.update(
      "INSERT INTO analytics_upgrade.study_tasks(id,user_id,source_key,kind,target_kind,target_id,title_ru,title_kz,reason,duration_minutes,status,updated_at) VALUES (?,?,'old-task','THEORY','TOPIC',?,'Old','Old','WEAK',10,'COMPLETED','2026-10-12T06:00:00Z')",
      task,
      user,
      UUID.randomUUID()
    );
    UUID question = db.queryForObject(
        "SELECT id FROM analytics_upgrade.questions LIMIT 1",
        UUID.class
      ),
      subject = db.queryForObject(
        "SELECT subject_id FROM analytics_upgrade.questions WHERE id=?",
        UUID.class,
        question
      ),
      session = UUID.randomUUID();
    db.update(
      "INSERT INTO analytics_upgrade.test_sessions(id,user_id,subject_id,question_ids,question_snapshot,total_questions,correct_answers,status,completed_at,score) VALUES (?,?,?,'[]',jsonb_build_array(jsonb_build_object('id',?::text,'subjectId',?::text)),1,0,'COMPLETED','2026-10-12T06:00:00',0)",
      session,
      user,
      subject,
      question,
      subject
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
      Instant.parse("2026-10-12T06:00:00Z"),
      db
        .queryForObject(
          "SELECT completed_at FROM analytics_upgrade.study_tasks WHERE id=?",
          java.sql.Timestamp.class,
          task
        )
        .toInstant()
    );
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM analytics_upgrade.completed_question_activity WHERE user_id=?",
        Integer.class,
        user
      )
    );
    assertEquals(
      2,
      db.queryForObject(
        "SELECT count(*) FROM analytics_upgrade.statistics_activity_events WHERE user_id=?",
        Integer.class,
        user
      )
    );
    assertEquals(
      "STUDENT",
      db.queryForObject(
        "SELECT role FROM analytics_upgrade.users WHERE id=?",
        String.class,
        user
      )
    );
  }
}
