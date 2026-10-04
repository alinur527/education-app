package ent.kz.entbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.dto.*;
import ent.kz.entbackend.entity.*;
import ent.kz.entbackend.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
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

  public TestSessionService(
    ContentAccess access,
    QuestionRepository questions,
    TestSessionRepository sessions,
    TestAnswerRepository answers,
    ObjectMapper json,
    AuthService auth
  ) {
    this.access = access;
    this.questions = questions;
    this.sessions = sessions;
    this.answers = answers;
    this.json = json;
    this.auth = auth;
  }

  public StartTestResponse startTest(StartTestRequest request) {
    Topic topic = access.topic(request.topicId());
    List<Question> items = questions
      .findByTopicIdAndIsActiveTrue(topic.getId())
      .stream()
      .sorted(
        Comparator.comparing(Question::getCreatedAt).thenComparing(
          Question::getId
        )
      )
      .toList();
    if (items.isEmpty()) throw status(HttpStatus.CONFLICT);
    TestSession s = new TestSession();
    s.setUser(auth.getCurrentUser());
    s.setSubject(topic.getSubject());
    s.setTopic(topic);
    s.setStatus("IN_PROGRESS");
    s.setQuestionIds(write(items.stream().map(Question::getId).toList()));
    s.setQuestionSnapshot(write(items.stream().map(this::snapshot).toList()));
    s.setTotalQuestions(items.size());
    s.setCorrectAnswers(0);
    s.setScore(BigDecimal.ZERO);
    s.setStartedAt(LocalDateTime.now());
    s.setCreatedAt(s.getStartedAt());
    s.setUpdatedAt(s.getStartedAt());
    sessions.save(s);
    return new StartTestResponse(
      s.getId(),
      s.getTotalQuestions(),
      s.getStartedAt()
    );
  }

  @Transactional(readOnly = true)
  public SessionResponse getState(UUID id) {
    TestSession s = owned(id, false);
    UUID topicId =
      s.getTopic() != null
        ? s.getTopic().getId()
        : snapshots(s).getFirst().topicId();
    return new SessionResponse(
      id,
      topicId,
      s.getSubject().getId(),
      s.getStatus(),
      s.getTotalQuestions(),
      s.getStartedAt(),
      answers
        .findBySessionIdOrderByCreatedAtAsc(id)
        .stream()
        .map(a ->
          new SessionResponse.SavedAnswer(
            a.getQuestion().getId(),
            a.getSelectedOptionId()
          )
        )
        .toList()
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
      q.year()
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
    if (
      q
        .options()
        .stream()
        .noneMatch(o -> o.id().equals(request.selectedOptionId()))
    ) throw status(HttpStatus.BAD_REQUEST);
    Optional<TestAnswer> previous = answers.findBySessionIdAndQuestionId(
      id,
      q.id()
    );
    if (previous.isPresent()) {
      if (
        !previous.get().getSelectedOptionId().equals(request.selectedOptionId())
      ) throw status(HttpStatus.CONFLICT);
      return new SubmitAnswerResponse(q.id(), request.selectedOptionId());
    }
    TestAnswer a = new TestAnswer();
    a.setSession(s);
    a.setQuestion(questions.getReferenceById(q.id()));
    a.setSelectedOptionId(request.selectedOptionId());
    a.setIsCorrect(q.correctOptionId().equals(request.selectedOptionId()));
    a.setTimeSpentSecs(request.timeSpentSecs());
    a.setCreatedAt(LocalDateTime.now());
    answers.save(a);
    s.setUpdatedAt(LocalDateTime.now());
    return new SubmitAnswerResponse(q.id(), request.selectedOptionId());
  }

  public FinishTestResponse finishTest(UUID id) {
    TestSession s = owned(id, true);
    if (!"COMPLETED".equals(s.getStatus())) {
      inProgress(s);
      int correct = (int) answers.countBySessionIdAndIsCorrectTrue(id);
      LocalDateTime now = LocalDateTime.now();
      s.setStatus("COMPLETED");
      s.setCorrectAnswers(correct);
      s.setScore(
        BigDecimal.valueOf(correct * 100L).divide(
          BigDecimal.valueOf(s.getTotalQuestions()),
          2,
          RoundingMode.HALF_UP
        )
      );
      s.setTimeTakenSecs(
        (int) Math.min(
          Integer.MAX_VALUE,
          Math.max(0, Duration.between(s.getStartedAt(), now).getSeconds())
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
      s.getTimeTakenSecs()
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
          q.explanationKz()
        );
      })
      .toList();
    UUID topic =
      s.getTopic() != null
        ? s.getTopic().getId()
        : snapshots(s).getFirst().topicId();
    return new TestResultsResponse(
      id,
      topic,
      s.getSubject().getId(),
      s.getStatus(),
      s.getTotalQuestions(),
      s.getCorrectAnswers(),
      s.getScore(),
      s.getTimeTakenSecs(),
      review
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
      q.getYear()
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
