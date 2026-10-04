package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.study.StudyNotificationService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties = "app.study.notifications.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class StudyIntegrationTests {

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
      () -> "study-integration-key-only-01234567890123456789"
    );
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate db;

  @Autowired
  StudyNotificationService notifications;

  record Account(String token, String id, String email) {}

  Account account(String role) throws Exception {
    String email = "study-" + UUID.randomUUID() + "@example.org";
    var a = call(
      "POST",
      "/api/auth/register",
      null,
      Map.of(
        "email",
        email,
        "password",
        "Study-password-2026",
        "firstName",
        "Тест",
        "lastName",
        "План",
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

  JsonNode content(
    Account owner,
    String kind,
    String parent,
    String title,
    boolean publish
  ) throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("kind", kind);
    body.put("parentId", parent);
    body.put(
      "payload",
      Map.of(
        "titleRu",
        title,
        "titleKz",
        title,
        "contentRu",
        "Проверенная теория",
        "contentKz",
        "Тексерілген теория"
      )
    );
    var c = call("POST", "/api/cms/content", owner, body, 200);
    if (!publish) return c;
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

  LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Almaty"));
  }

  Map<String, Object> profile(List<String> subjects, int minutes) {
    return new LinkedHashMap<>(
      Map.of(
        "goal",
        "Реальная подготовка",
        "targetDate",
        today().plusDays(7).toString(),
        "availableDays",
        List.of(1, 2, 3, 4, 5, 6, 7),
        "minutesPerDay",
        minutes,
        "timeZone",
        "Asia/Almaty",
        "selectedSubjects",
        subjects,
        "revision",
        0
      )
    );
  }

  String calendar() {
    return "/api/study/tasks?from=" + today() + "&to=" + today().plusDays(8);
  }

  Map<String, Object> note(String target, String title) {
    return new LinkedHashMap<>(
      Map.of(
        "targetKind",
        "TOPIC",
        "targetId",
        target,
        "title",
        title,
        "body",
        "Мой личный текст",
        "bookmarked",
        true,
        "cardFront",
        "Сколько будет 2 + 2?",
        "cardBack",
        "4",
        "revision",
        0
      )
    );
  }

  @Test
  void profileValidatesTimeZoneSelectionsAndOptimisticRevision()
    throws Exception {
    var student = account("STUDENT");
    var editor = account("CONTENT_EDITOR");
    var subject = content(editor, "SUBJECT", null, "План: предмет", true);
    var draft = content(editor, "SUBJECT", null, "Черновик предмета", false);
    assertEquals(
      "Asia/Almaty",
      call("GET", "/api/study/profile", student, null, 200)
        .path("timeZone")
        .asText()
    );
    var p = profile(List.of(draft.path("id").asText()), 30);
    call("PUT", "/api/study/profile", student, p, 400);
    p = profile(List.of(subject.path("id").asText()), 30);
    p.put("timeZone", "Mars/Unknown");
    call("PUT", "/api/study/profile", student, p, 400);
    p.put("timeZone", "Asia/Almaty");
    p.put("availableDays", Arrays.asList(1, null));
    call("PUT", "/api/study/profile", student, p, 400);
    p.put("availableDays", List.of(1, 2, 3, 4, 5, 6, 7));
    assertEquals(
      1,
      call("PUT", "/api/study/profile", student, p, 200)
        .path("revision")
        .asInt()
    );
    call("PUT", "/api/study/profile", student, p, 409);
  }

  @Test
  void recomputeIsIdempotentAndPreservesMovedPinnedCompletedAndAvoidsOverlap()
    throws Exception {
    var s = account("STUDENT");
    var editor = account("CONTENT_EDITOR");
    var subject = content(editor, "SUBJECT", null, "План: темы", true);
    String sid = subject.path("id").asText();
    for (int i = 0; i < 3; i++) {
      var topic = content(editor, "TOPIC", sid, "Тема " + i, true);
      content(editor, "THEORY", topic.path("id").asText(), "Теория " + i, true);
    }
    call("PUT", "/api/study/profile", s, profile(List.of(sid), 60), 200);
    var plan = call("POST", "/api/study/plan/recompute", s, null, 200);
    assertEquals(3, plan.path("planned").asInt());
    var before = call("GET", calendar(), s, null, 200).path("items");
    call("POST", "/api/study/plan/recompute", s, null, 200);
    assertEquals(before, call("GET", calendar(), s, null, 200).path("items"));
    var first = before.get(0);
    String moved =
      OffsetDateTime.parse(first.path("scheduledAt").asText())
        .atZoneSameInstant(ZoneId.of("Asia/Almaty"))
        .toLocalDate() + "T18:10:00+05:00";
    var changed = call(
      "PATCH",
      "/api/study/tasks/" + first.path("id").asText(),
      s,
      Map.of(
        "scheduledAt",
        moved,
        "pinned",
        true,
        "revision",
        first.path("revision").asLong()
      ),
      200
    );
    var second = before.get(1);
    call(
      "PATCH",
      "/api/study/tasks/" + second.path("id").asText(),
      s,
      Map.of(
        "status",
        "COMPLETED",
        "revision",
        second.path("revision").asLong()
      ),
      200
    );
    call("POST", "/api/study/plan/recompute", s, null, 200);
    var after = call("GET", calendar(), s, null, 200).path("items");
    assertEquals(3, after.size());
    for (var t : after) {
      if (
        !t.path("id").equals(first.path("id")) &&
        !t.path("id").equals(second.path("id"))
      ) {
        var start = OffsetDateTime.parse(t.path("scheduledAt").asText());
        var pin = OffsetDateTime.parse(moved);
        assertTrue(
          !start.isBefore(pin.plusMinutes(20)) ||
            !start.plusMinutes(20).isAfter(pin)
        );
      }
      if (t.path("id").equals(first.path("id"))) assertEquals(changed, t);
      if (t.path("id").equals(second.path("id"))) assertEquals(
        "COMPLETED",
        t.path("status").asText()
      );
    }
    var outsider = account("STUDENT");
    call(
      "PATCH",
      "/api/study/tasks/" + first.path("id").asText(),
      outsider,
      Map.of("pinned", false, "revision", 2),
      404
    );
    var p = profile(List.of(sid), 10);
    p.put("revision", 1);
    p.put("targetDate", today().toString());
    call("PUT", "/api/study/profile", s, p, 200);
    assertTrue(
      call("POST", "/api/study/plan/recompute", s, null, 200)
        .path("capacityWarning")
        .asBoolean()
    );
  }

  @Test
  void privateNotesRetainTextAfterArchiveAndFlashcardReviewIsVersioned()
    throws Exception {
    var editor = account("ADMIN");
    var sub = content(editor, "SUBJECT", null, "Заметки", true);
    var topic = content(
      editor,
      "TOPIC",
      sub.path("id").asText(),
      "Тема заметки",
      true
    );
    String tid = topic.path("id").asText();
    var owner = account("STUDENT");
    var outsider = account("STUDENT");
    String id = UUID.randomUUID().toString();
    var body = note(tid, "Личный секрет");
    var saved = call("PUT", "/api/study/notes/" + id, owner, body, 200);
    call("GET", "/api/study/notes/" + id, outsider, null, 404);
    call("PUT", "/api/study/notes/" + id, outsider, body, 404);
    call(
      "DELETE",
      "/api/study/notes/" + id + "?revision=1",
      outsider,
      null,
      404
    );
    assertEquals(
      0,
      call("GET", "/api/study/notes?q=секрет", outsider, null, 200)
        .path("total")
        .asInt()
    );
    var reviewed = call(
      "POST",
      "/api/study/notes/" + id + "/review",
      owner,
      Map.of("rating", "GOOD", "revision", saved.path("revision").asLong()),
      200
    );
    assertEquals(3, reviewed.path("intervalDays").asInt());
    call(
      "POST",
      "/api/study/notes/" + id + "/review",
      owner,
      Map.of("rating", "EASY", "revision", 1),
      409
    );
    assertEquals(
      0,
      call("GET", "/api/study/notes?due=true", owner, null, 200)
        .path("total")
        .asInt()
    );
    call(
      "POST",
      "/api/cms/content/" + tid + "/transition",
      editor,
      Map.of("status", "ARCHIVED", "version", topic.path("version").asLong()),
      200
    );
    var retained = call("GET", "/api/study/notes/" + id, owner, null, 200);
    assertFalse(retained.path("available").asBoolean());
    assertEquals("Мой личный текст", retained.path("body").asText());
    body.put("revision", 2);
    body.put("body", "Дополнение после архивации");
    var updated = call("PUT", "/api/study/notes/" + id, owner, body, 200);
    assertEquals(3, updated.path("revision").asInt());
    call(
      "PUT",
      "/api/study/notes/" + UUID.randomUUID(),
      outsider,
      note(tid, "Новая"),
      404
    );
    call("DELETE", "/api/study/notes/" + id + "?revision=3", owner, null, 200);
  }

  @Test
  void notificationsRespectQuietHoursDedupeAndCurrentAccess() throws Exception {
    var s = account("STUDENT");
    var editor = account("ADMIN");
    var sub = content(editor, "SUBJECT", null, "Уведомления", true);
    var topic = content(
      editor,
      "TOPIC",
      sub.path("id").asText(),
      "Тема уведомления",
      true
    );
    content(
      editor,
      "THEORY",
      topic.path("id").asText(),
      "Теория уведомления",
      true
    );
    call(
      "PUT",
      "/api/study/profile",
      s,
      profile(List.of(sub.path("id").asText()), 30),
      200
    );
    call("POST", "/api/study/plan/recompute", s, null, 200);
    Instant daytime = OffsetDateTime.parse(
      call("GET", calendar(), s, null, 200)
        .path("items")
        .get(0)
        .path("scheduledAt")
        .asText()
    )
      .minusHours(1)
      .toInstant();
    Instant quietTime = daytime
      .atZone(ZoneId.of("Asia/Almaty"))
      .toLocalDate()
      .minusDays(1)
      .atTime(23, 0)
      .atZone(ZoneId.of("Asia/Almaty"))
      .toInstant();
    notifications.process(UUID.fromString(s.id()), quietTime);
    assertEquals(
      0,
      call("GET", "/api/study/notifications", s, null, 200)
        .path("total")
        .asInt()
    );
    notifications.process(UUID.fromString(s.id()), daytime);
    notifications.process(UUID.fromString(s.id()), daytime);
    var list = call("GET", "/api/study/notifications", s, null, 200);
    assertEquals(1, list.path("total").asInt());
    String id = list.path("items").get(0).path("id").asText();
    var outsider = account("STUDENT");
    call(
      "POST",
      "/api/study/notifications/" + id + "/open",
      outsider,
      null,
      404
    );
    call(
      "POST",
      "/api/cms/content/" + topic.path("id").asText() + "/transition",
      editor,
      Map.of("status", "ARCHIVED", "version", topic.path("version").asLong()),
      200
    );
    call("POST", "/api/study/notifications/" + id + "/open", s, null, 404);
    assertFalse(
      call("GET", "/api/study/notifications", s, null, 200)
        .path("items")
        .get(0)
        .path("available")
        .asBoolean()
    );
    call(
      "PATCH",
      "/api/study/notifications/" + id,
      s,
      Map.of("read", true),
      200
    );
    assertEquals(
      0,
      call("GET", "/api/study/notifications?unread=true", s, null, 200)
        .path("total")
        .asInt()
    );
    var pref = call(
      "GET",
      "/api/study/notifications/preferences",
      s,
      null,
      200
    );
    var p = json.convertValue(
      pref,
      new com.fasterxml.jackson.core.type.TypeReference<
        Map<String, Object>
      >() {}
    );
    p.put("quietStart", "00:00");
    p.put("quietEnd", "23:59");
    call("PUT", "/api/study/notifications/preferences", s, p, 200);
    db.update("DELETE FROM study_notifications WHERE user_id=?::uuid", s.id());
    notifications.process(UUID.fromString(s.id()), daytime);
    assertEquals(
      0,
      call("GET", "/api/study/notifications", s, null, 200)
        .path("total")
        .asInt()
    );
  }

  @Test
  void realDeadlinesAndLessonPlanRequireEnrollmentAndGroupMembership()
    throws Exception {
    var teacher = account("TEACHER");
    var s = account("STUDENT");
    var outsider = account("STUDENT");
    var course = content(teacher, "COURSE", null, "Групповой курс", true);
    String cid = course.path("id").asText();
    var module = content(teacher, "MODULE", cid, "Модуль", true);
    var lesson = content(
      teacher,
      "LESSON",
      module.path("id").asText(),
      "Урок группы",
      true
    );
    String lid = lesson.path("id").asText();
    var group = call(
      "POST",
      "/api/teacher/groups",
      teacher,
      Map.of("name", "Учебная группа", "courseId", cid),
      200
    );
    String gid = group.path("id").asText();
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/members",
      teacher,
      Map.of("email", s.email()),
      200
    );
    var assignment = content(teacher, "ASSIGNMENT", lid, "Сдать решение", true);
    String aid = assignment.path("id").asText();
    call(
      "POST",
      "/api/teacher/groups/" + gid + "/assignments",
      teacher,
      Map.of("assignmentId", aid),
      200
    );
    db.update(
      "UPDATE assignments SET due_at=? WHERE id=?::uuid",
      today()
        .plusDays(1)
        .atTime(20, 0)
        .atZone(ZoneId.of("Asia/Almaty"))
        .toOffsetDateTime(),
      aid
    );
    String deadlines =
      "/api/study/deadlines?from=" + today() + "&to=" + today().plusDays(7);
    assertEquals(1, call("GET", deadlines, s, null, 200).path("total").asInt());
    assertEquals(
      0,
      call("GET", deadlines, outsider, null, 200).path("total").asInt()
    );
    call("PUT", "/api/study/profile", s, profile(List.of(), 60), 200);
    call("POST", "/api/study/plan/recompute", s, null, 200);
    assertEquals(
      2,
      call("GET", calendar(), s, null, 200).path("total").asInt()
    );
    notifications.process(
      UUID.fromString(s.id()),
      today().atTime(12, 0).atZone(ZoneId.of("Asia/Almaty")).toInstant()
    );
    assertTrue(
      call("GET", "/api/study/notifications", s, null, 200)
        .path("total")
        .asInt() >= 1
    );
    db.update(
      "UPDATE enrollments SET status='CANCELLED' WHERE course_id=?::uuid AND user_id=?::uuid",
      cid,
      s.id()
    );
    assertEquals(0, call("GET", deadlines, s, null, 200).path("total").asInt());
    assertTrue(
      call("GET", calendar(), s, null, 200)
        .path("items")
        .findValues("available")
        .stream()
        .noneMatch(JsonNode::asBoolean)
    );
    String id = UUID.randomUUID().toString();
    var n = note(lid, "Недоступный урок");
    n.put("targetKind", "LESSON");
    call("PUT", "/api/study/notes/" + id, s, n, 404);
  }
}
