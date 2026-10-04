package ent.kz.entbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.dto.QuestionAdminRequest;
import ent.kz.entbackend.dto.QuestionAdminResponse;
import ent.kz.entbackend.dto.QuestionOptionRequest;
import ent.kz.entbackend.dto.QuestionOptionResponse;
import ent.kz.entbackend.entity.Question;
import ent.kz.entbackend.entity.Topic;
import ent.kz.entbackend.repository.QuestionRepository;
import ent.kz.entbackend.repository.TopicRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminQuestionService {

  private final QuestionRepository questionRepository;
  private final TopicRepository topicRepository;
  private final ObjectMapper objectMapper;

  public AdminQuestionService(
    QuestionRepository questionRepository,
    TopicRepository topicRepository,
    ObjectMapper objectMapper
  ) {
    this.questionRepository = questionRepository;
    this.topicRepository = topicRepository;
    this.objectMapper = objectMapper;
  }

  public List<QuestionAdminResponse> getQuestionsByTopic(UUID topicId) {
    ensureTopicExists(topicId);
    return questionRepository
      .findByTopicId(topicId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  public QuestionAdminResponse createQuestion(QuestionAdminRequest request) {
    Topic topic = topicRepository
      .findById(request.topicId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );
    validateOptions(request.options(), request.correctOptionId());

    LocalDateTime now = LocalDateTime.now();
    Question question = new Question();
    question.setTopic(topic);
    question.setSubject(topic.getSubject());
    question.setTopicRu(topic.getTitleRu());
    question.setTopicKz(topic.getTitleKz());
    applyRequest(question, request);
    question.setCreatedAt(now);
    question.setUpdatedAt(now);

    return toResponse(questionRepository.save(question));
  }

  public QuestionAdminResponse updateQuestion(
    UUID id,
    QuestionAdminRequest request
  ) {
    Question question = questionRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found")
      );
    Topic topic = topicRepository
      .findById(request.topicId())
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found")
      );
    if (
      question.getAssessment() != null
    ) throw new ent.kz.entbackend.platform.PlatformException(
      409,
      "USE_CMS_FOR_TYPED_QUESTION"
    );
    validateOptions(request.options(), request.correctOptionId());

    question.setTopic(topic);
    question.setSubject(topic.getSubject());
    question.setTopicRu(topic.getTitleRu());
    question.setTopicKz(topic.getTitleKz());
    applyRequest(question, request);
    question.setUpdatedAt(LocalDateTime.now());

    return toResponse(questionRepository.save(question));
  }

  public void softDeleteQuestion(UUID id) {
    Question question = questionRepository
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found")
      );

    question.setIsActive(false);
    question.setUpdatedAt(LocalDateTime.now());
    questionRepository.save(question);
  }

  private void ensureTopicExists(UUID topicId) {
    if (!topicRepository.existsById(topicId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Topic not found"
      );
    }
  }

  private void validateOptions(
    List<QuestionOptionRequest> options,
    String correctOptionId
  ) {
    Set<String> optionIds = options
      .stream()
      .map(QuestionOptionRequest::id)
      .collect(Collectors.toSet());

    if (optionIds.size() != options.size()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Question options must have unique ids"
      );
    }

    if (!optionIds.contains(correctOptionId)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "correctOptionId must match one of options[].id"
      );
    }
  }

  private void applyRequest(Question question, QuestionAdminRequest request) {
    question.setQuestionRu(request.questionRu());
    question.setQuestionKz(request.questionKz());
    question.setOptions(writeOptions(request.options()));
    question.setCorrectOptionId(request.correctOptionId());
    question.setExplanationRu(request.explanationRu());
    question.setExplanationKz(request.explanationKz());
    question.setDifficulty(request.difficulty().toLowerCase());
    question.setYear(request.year());
    question.setIsActive(request.isActive());
  }

  private String writeOptions(List<QuestionOptionRequest> options) {
    List<QuestionOptionResponse> payload = options
      .stream()
      .map(option ->
        new QuestionOptionResponse(
          option.id(),
          option.textRu(),
          option.textKz()
        )
      )
      .toList();
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to serialize options",
        exception
      );
    }
  }

  private QuestionAdminResponse toResponse(Question question) {
    return new QuestionAdminResponse(
      question.getId(),
      question.getTopic() != null ? question.getTopic().getId() : null,
      question.getQuestionRu(),
      question.getQuestionKz(),
      parseOptions(question),
      question.getCorrectOptionId(),
      question.getExplanationRu(),
      question.getExplanationKz(),
      question.getDifficulty(),
      question.getYear(),
      question.getIsActive()
    );
  }

  private List<QuestionOptionResponse> parseOptions(Question question) {
    try {
      return objectMapper.readValue(
        question.getOptions(),
        new TypeReference<List<QuestionOptionResponse>>() {}
      );
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Invalid options JSON for question " + question.getId(),
        exception
      );
    }
  }
}
