package ent.kz.entbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.dto.QuestionOptionResponse;
import ent.kz.entbackend.dto.QuestionResponse;
import ent.kz.entbackend.entity.Question;
import ent.kz.entbackend.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {

  @org.springframework.beans.factory.annotation.Autowired
  private ContentAccess access;

  private final QuestionRepository questionRepository;
  private final ObjectMapper objectMapper;

  public QuestionService(
    QuestionRepository questionRepository,
    ObjectMapper objectMapper
  ) {
    this.questionRepository = questionRepository;
    this.objectMapper = objectMapper;
  }

  public List<QuestionResponse> getActiveQuestionsByTopicId(UUID topicId) {
    access.topic(topicId);
    return questionRepository
      .findByTopicIdAndIsActiveTrue(topicId)
      .stream()
      .map(this::toResponse)
      .toList();
  }

  private QuestionResponse toResponse(Question question) {
    return new QuestionResponse(
      question.getId(),
      question.getTopic().getId(),
      question.getTopicRu(),
      question.getTopicKz(),
      question.getQuestionRu(),
      question.getQuestionKz(),
      parseOptions(question),
      question.getDifficulty(),
      question.getYear()
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
