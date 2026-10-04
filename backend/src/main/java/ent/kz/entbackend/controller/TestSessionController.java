package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.FinishTestResponse;
import ent.kz.entbackend.dto.StartTestRequest;
import ent.kz.entbackend.dto.StartTestResponse;
import ent.kz.entbackend.dto.SubmitAnswerRequest;
import ent.kz.entbackend.dto.SubmitAnswerResponse;
import ent.kz.entbackend.dto.TestQuestionResponse;
import ent.kz.entbackend.dto.TestResultsResponse;
import ent.kz.entbackend.service.TestSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
public class TestSessionController {

  private final TestSessionService testSessionService;

  public TestSessionController(TestSessionService testSessionService) {
    this.testSessionService = testSessionService;
  }

  @GetMapping("/{sessionId}")
  public ent.kz.entbackend.dto.SessionResponse state(
    @PathVariable UUID sessionId
  ) {
    return testSessionService.getState(sessionId);
  }

  @PostMapping(
    value = "/start",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public StartTestResponse startTest(
    @Valid @RequestBody StartTestRequest request
  ) {
    return testSessionService.startTest(request);
  }

  @GetMapping(
    value = "/{sessionId}/questions/{index}",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public TestQuestionResponse getQuestion(
    @PathVariable UUID sessionId,
    @PathVariable Integer index
  ) {
    return testSessionService.getQuestionByIndex(sessionId, index);
  }

  @PostMapping(
    value = "/{sessionId}/answers",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public SubmitAnswerResponse submitAnswer(
    @PathVariable UUID sessionId,
    @Valid @RequestBody SubmitAnswerRequest request
  ) {
    return testSessionService.submitAnswer(sessionId, request);
  }

  @PostMapping(
    value = "/{sessionId}/finish",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public FinishTestResponse finishTest(@PathVariable UUID sessionId) {
    return testSessionService.finishTest(sessionId);
  }

  @GetMapping(
    value = "/{sessionId}/results",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public TestResultsResponse getResults(@PathVariable UUID sessionId) {
    return testSessionService.getResults(sessionId);
  }
}
