package ent.kz.entbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ent.kz.entbackend.dto.*;
import ent.kz.entbackend.entity.*;
import ent.kz.entbackend.platform.assessment.Assessment;
import ent.kz.entbackend.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TestSessionService {

  private final ContentAccess access;
  private final QuestionRepository questions;
  private final TestSessionRepository sessions;
  private final TestAnswerRepository answers;
  private final ObjectMapper json;
  private final AuthService auth;
  private final JdbcTemplate db;

  public TestSessionService(
    ContentAccess access,
    QuestionRepository questions,
    TestSessionRepository sessions,
    TestAnswerRepository answers,
    ObjectMapper json,
    AuthService auth,
    JdbcTemplate db
  ) {
    this.access = access;
    this.questions = questions;
    this.sessions = sessions;
    this.answers = answers;
    this.json = json;
    this.auth = auth;
    this.db = db;
  }

  public StartTestResponse startTest(StartTestRequest request) {
    Topic topic = access.topic(request.topicId());
    List<UUID> ids = db.query(
      "SELECT id FROM eligible_practice_questions WHERE topic_id=? ORDER BY random() LIMIT 50",
      (r, n) -> r.getObject(1, UUID.class),
      topic.getId()
    );
    List<Question> items = questions
      .findAllById(ids)
      .stream()
      .sorted(
        Comparator.comparing(Question::getCreatedAt).thenComparing(
          Question::getId
        )
      )
      .toList();
    if (items.isEmpty()) throw status(HttpStatus.CONFLICT);
    return start(
      items.stream().map(this::snapshot).toList(),
      topic,
      "TOPIC_PRACTICE",
      null,
      null
    );
  }

  public StartTestResponse startReview(
    UUID topicId,
    List<QuestionSnapshot> items
  ) {
    Topic topic = access.topic(topicId);
    if (items.isEmpty() || items.size() > 50) throw status(HttpStatus.CONFLICT);
    return start(items, topic, "ERROR_REVIEW", null, null);
  }

  public StartTestResponse startSelection(
    List<UUID> questionIds,
    String mode,
    Integer minutes,
    JsonNode configuration
  ) {
    if (
      questionIds.isEmpty() ||
      questionIds.size() > 120 ||
      new HashSet<>(questionIds).size() != questionIds.size()
    ) throw status(HttpStatus.BAD_REQUEST);
    Map<UUID, Question> found = new HashMap<>();
    questions.findAllById(questionIds).forEach(q -> found.put(q.getId(), q));
    List<QuestionSnapshot> items = new ArrayList<>();
    for (UUID id : questionIds) {
      Question q = found.get(id);
      if (q == null || !Boolean.TRUE.equals(q.getIsActive())) throw status(
        HttpStatus.CONFLICT
      );
      access.topic(q.getTopic().getId());
      items.add(snapshot(q));
    }
    return start(items, null, mode, minutes, configuration);
  }

  private StartTestResponse start(
    List<QuestionSnapshot> items,
    Topic topic,
    String mode,
    Integer minutes,
    JsonNode configuration
  ) {
    UUID userId = auth.getCurrentUser().getId();
    db.queryForList(
      "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
      "practice-start:" + userId
    );
    if (
      db.queryForObject(
        "SELECT count(*) FROM test_sessions WHERE user_id=? AND created_at>now()-interval '1 minute'",
        Integer.class,
        userId
      ) >= 30
    ) throw new ent.kz.entbackend.platform.PlatformException(
      429,
      "PRACTICE_RATE_LIMIT"
    );
    TestSession s = new TestSession();
    s.setUser(auth.getCurrentUser());
    s.setTopic(topic);
    s.setSubject(topic == null ? null : topic.getSubject());
    s.setPracticeMode(mode);
    s.setStatus("IN_PROGRESS");
    s.setQuestionIds(write(items.stream().map(QuestionSnapshot::id).toList()));
    s.setQuestionSnapshot(write(items));
    ObjectNode contexts = json.createObjectNode();
    for (QuestionSnapshot q : items) {
      String key = effective(q).path("contextKey").asText("");
      if (!key.isEmpty() && !contexts.has(key)) {
        if (
          !Boolean.TRUE.equals(
            db.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM content_records WHERE id::text=? AND kind='CONTEXT' AND published_payload IS NOT NULL AND status<>'ARCHIVED')",
              Boolean.class,
              key.split(":")[0]
            )
          )
        ) throw new ent.kz.entbackend.platform.PlatformException(
          409,
          "CONTEXT_WITHDRAWN"
        );
        String[] parts = key.split(":");
        String body = db.queryForObject(
          "SELECT payload::text FROM context_versions WHERE content_id=? AND version=?",
          String.class,
          UUID.fromString(parts[0]),
          Long.parseLong(parts[1])
        );
        contexts.set(key, read(body, new TypeReference<JsonNode>() {}));
      }
    }
    s.setContextSnapshot(write(contexts));
    s.setSnapshotVersion(2);
    s.setTotalQuestions(items.size());
    s.setCorrectAnswers(0);
    s.setEarnedPoints(0);
    s.setMaxPoints(
      items
        .stream()
        .mapToInt(q -> Assessment.maximum(effective(q)))
        .sum()
    );
    s.setScore(BigDecimal.ZERO);
    s.setStartedAt(LocalDateTime.now());
    s.setCreatedAt(s.getStartedAt());
    s.setUpdatedAt(s.getStartedAt());
    if (minutes != null) s.setDeadlineAt(
      OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutes)
    );
    if (configuration != null) s.setExamConfiguration(write(configuration));
    sessions.save(s);
    return new StartTestResponse(
      s.getId(),
      s.getTotalQuestions(),
      s.getStartedAt()
    );
  }

  public SessionResponse getState(UUID id) {
    TestSession s = owned(id, true);
    if ("IN_PROGRESS".equals(s.getStatus()) && expired(s)) finishTest(id);
    UUID topicId = s.getTopic() != null ? s.getTopic().getId() : null;
    return new SessionResponse(
      id,
      topicId,
      s.getSubject() == null ? null : s.getSubject().getId(),
      s.getStatus(),
      s.getTotalQuestions(),
      s.getStartedAt(),
      answers
        .findBySessionIdOrderByCreatedAtAsc(id)
        .stream()
        .map(a ->
          new SessionResponse.SavedAnswer(
            a.getQuestion().getId(),
            a.getSelectedOptionId(),
            answer(a)
          )
        )
        .toList(),
      s.getPracticeMode(),
      s.getDeadlineAt(),
      s.getMaxPoints(),
      snapshots(s).stream().map(QuestionSnapshot::id).toList()
    );
  }

  @Transactional(readOnly = true)
  public TestQuestionResponse getQuestionByIndex(UUID id, Integer index) {
    TestSession s = owned(id, false);
    List<QuestionSnapshot> items = snapshots(s);
    if (index == null || index < 0 || index >= items.size()) throw status(
      HttpStatus.BAD_REQUEST
    );
    QuestionSnapshot q = items.get(index);
    return new TestQuestionResponse(
      id,
      index,
      items.size(),
      q.id(),
      q.topicId(),
      q.topicRu(),
      q.topicKz(),
      q.questionRu(),
      q.questionKz(),
      q.options(),
      q.difficulty(),
      q.year(),
      Assessment.publicView(effective(q)),
      context(s, q)
    );
  }

  public SubmitAnswerResponse submitAnswer(
    UUID id,
    SubmitAnswerRequest request
  ) {
    TestSession s = owned(id, true);
    inProgress(s);
    QuestionSnapshot q = snapshots(s)
      .stream()
      .filter(v -> v.id().equals(request.questionId()))
      .findFirst()
      .orElseThrow(() -> status(HttpStatus.BAD_REQUEST));
    if (expired(s)) throw status(HttpStatus.CONFLICT);
    if (
      request.answer() != null && request.selectedOptionId() != null
    ) throw status(HttpStatus.BAD_REQUEST);
    JsonNode supplied =
      request.answer() != null
        ? request.answer()
        : json
            .createObjectNode()
            .put("selectedOptionId", request.selectedOptionId());
    Assessment.Grade grade = Assessment.grade(effective(q), supplied);
    Optional<TestAnswer> previous = answers.findBySessionIdAndQuestionId(
      id,
      q.id()
    );
    if (previous.isPresent()) {
      if (!answer(previous.get()).equals(grade.answer())) throw status(
        HttpStatus.CONFLICT
      );
      return new SubmitAnswerResponse(
        q.id(),
        request.selectedOptionId(),
        grade.answer()
      );
    }
    TestAnswer a = new TestAnswer();
    a.setSession(s);
    a.setQuestion(questions.getReferenceById(q.id()));
    a.setSelectedOptionId(request.selectedOptionId());
    a.setAnswerPayload(write(grade.answer()));
    a.setEarnedPoints(grade.earned());
    a.setMaxPoints(grade.maximum());
    a.setIsCorrect(grade.correct());
    a.setTimeSpentSecs(request.timeSpentSecs());
    a.setCreatedAt(LocalDateTime.now());
    answers.save(a);
    s.setUpdatedAt(LocalDateTime.now());
    return new SubmitAnswerResponse(
      q.id(),
      request.selectedOptionId(),
      grade.answer()
    );
  }

  public FinishTestResponse finishTest(UUID id) {
    TestSession s = owned(id, true);
    if (!"COMPLETED".equals(s.getStatus())) {
      inProgress(s);
      int correct = (int) answers.countBySessionIdAndIsCorrectTrue(id);
      LocalDateTime now = LocalDateTime.now();
      s.setStatus("COMPLETED");
      s.setCorrectAnswers(correct);
      int earned = answers
        .findBySessionIdOrderByCreatedAtAsc(id)
        .stream()
        .mapToInt(a ->
          a.getEarnedPoints() != null
            ? a.getEarnedPoints()
            : Boolean.TRUE.equals(a.getIsCorrect())
              ? 1
              : 0
        )
        .sum();
      int maximum =
        s.getMaxPoints() != null ? s.getMaxPoints() : s.getTotalQuestions();
      s.setEarnedPoints(earned);
      s.setMaxPoints(maximum);
      s.setScore(
        BigDecimal.valueOf(earned * 100L).divide(
          BigDecimal.valueOf(maximum),
          2,
          RoundingMode.HALF_UP
        )
      );
      s.setTimeTakenSecs(
        (int) Math.min(
          Integer.MAX_VALUE,
          Math.max(
            0,
            Duration.between(
              s.getStartedAt(),
              s.getDeadlineAt() != null &&
                OffsetDateTime.now().isAfter(s.getDeadlineAt())
                ? s
                    .getDeadlineAt()
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
                : now
            ).getSeconds()
          )
        )
      );
      s.setCompletedAt(now);
      s.setUpdatedAt(now);
    }
    return new FinishTestResponse(
      id,
      s.getCorrectAnswers(),
      s.getTotalQuestions(),
      s.getScore(),
      s.getTimeTakenSecs(),
      s.getEarnedPoints(),
      s.getMaxPoints()
    );
  }

  @Transactional(readOnly = true)
  public TestResultsResponse getResults(UUID id) {
    TestSession s = owned(id, false);
    if (!"COMPLETED".equals(s.getStatus())) throw status(HttpStatus.CONFLICT);
    Map<UUID, TestAnswer> saved = new HashMap<>();
    answers
      .findBySessionIdOrderByCreatedAtAsc(id)
      .forEach(a -> saved.put(a.getQuestion().getId(), a));
    List<TestAnswerResultResponse> review = snapshots(s)
      .stream()
      .map(q -> {
        TestAnswer a = saved.get(q.id());
        return new TestAnswerResultResponse(
          q.id(),
          q.questionRu(),
          q.questionKz(),
          q.options(),
          a == null ? null : a.getSelectedOptionId(),
          q.correctOptionId(),
          a != null && Boolean.TRUE.equals(a.getIsCorrect()),
          q.explanationRu(),
          q.explanationKz(),
          effective(q),
          a == null ? null : answer(a),
          context(s, q),
          a == null
            ? 0
            : a.getEarnedPoints() != null
              ? a.getEarnedPoints()
              : Boolean.TRUE.equals(a.getIsCorrect())
                ? 1
                : 0,
          Assessment.maximum(effective(q)),
          q.topicId(),
          q.subjectId() == null && s.getSubject() != null
            ? s.getSubject().getId()
            : q.subjectId()
        );
      })
      .toList();
    UUID topic = s.getTopic() != null ? s.getTopic().getId() : null;
    return new TestResultsResponse(
      id,
      topic,
      s.getSubject() == null ? null : s.getSubject().getId(),
      s.getStatus(),
      s.getTotalQuestions(),
      s.getCorrectAnswers(),
      s.getScore(),
      s.getTimeTakenSecs(),
      review,
      s.getEarnedPoints(),
      s.getMaxPoints(),
      s.getPracticeMode()
    );
  }

  private TestSession owned(UUID id, boolean lock) {
    UUID user = auth.getCurrentUser().getId();
    return (
      lock
        ? sessions.findOwnedForUpdate(id, user)
        : sessions.findByIdAndUserId(id, user)
    ).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
  }

  private void inProgress(TestSession s) {
    if (!"IN_PROGRESS".equals(s.getStatus())) throw status(HttpStatus.CONFLICT);
  }

  private QuestionSnapshot snapshot(Question q) {
    return new QuestionSnapshot(
      q.getId(),
      q.getTopic() == null ? null : q.getTopic().getId(),
      q.getTopicRu(),
      q.getTopicKz(),
      q.getQuestionRu(),
      q.getQuestionKz(),
      read(
        q.getOptions(),
        new TypeReference<List<QuestionOptionResponse>>() {}
      ),
      q.getCorrectOptionId(),
      q.getExplanationRu(),
      q.getExplanationKz(),
      q.getDifficulty(),
      q.getYear(),
      q.getSubject().getId(),
      q.getAssessment() == null
        ? null
        : read(q.getAssessment(), new TypeReference<JsonNode>() {})
    );
  }

  private boolean expired(TestSession s) {
    return (
      s.getDeadlineAt() != null &&
      !OffsetDateTime.now().isBefore(s.getDeadlineAt())
    );
  }

  private JsonNode effective(QuestionSnapshot q) {
    if (
      q.assessment() != null && !q.assessment().isNull()
    ) return q.assessment();
    ObjectNode data = json.createObjectNode();
    data.set("options", json.valueToTree(q.options()));
    data.put("correctOptionId", q.correctOptionId());
    data.put("questionType", "SINGLE_CHOICE");
    data.put("scoringPolicy", "LEGACY_SINGLE_V1");
    data.put("maxPoints", 1);
    return data;
  }

  private JsonNode answer(TestAnswer a) {
    return a.getAnswerPayload() == null
      ? json.createObjectNode().put("selectedOptionId", a.getSelectedOptionId())
      : read(a.getAnswerPayload(), new TypeReference<JsonNode>() {});
  }

  private JsonNode context(TestSession s, QuestionSnapshot q) {
    String key = effective(q).path("contextKey").asText("");
    return key.isEmpty()
      ? null
      : ent.kz.entbackend.platform.assessment.ContextPublicView.of(
          read(s.getContextSnapshot(), new TypeReference<JsonNode>() {}).get(
            key
          )
        );
  }

  private List<QuestionSnapshot> snapshots(TestSession s) {
    if (s.getQuestionSnapshot() != null) return read(
      s.getQuestionSnapshot(),
      new TypeReference<List<QuestionSnapshot>>() {}
    );
    // Compatibility for attempts created by the old backend; new attempts always have snapshots.
    List<UUID> ids = read(
      s.getQuestionIds(),
      new TypeReference<List<UUID>>() {}
    );
    return ids
      .stream()
      .map(id ->
        snapshot(
          questions.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND))
        )
      )
      .toList();
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored content", e);
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode content", e);
    }
  }

  private ResponseStatusException status(HttpStatus status) {
    return new ResponseStatusException(status);
  }
}
