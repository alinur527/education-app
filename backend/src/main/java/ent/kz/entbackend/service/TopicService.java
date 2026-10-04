package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.TopicResponse;
import ent.kz.entbackend.repository.CurriculumQueryRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TopicService {

  private final CurriculumQueryRepository queries;
  private final ContentAccess access;

  public TopicService(CurriculumQueryRepository queries, ContentAccess access) {
    this.queries = queries;
    this.access = access;
  }

  public List<TopicResponse> getActiveTopicsBySubjectId(UUID subjectId) {
    access.subject(subjectId);
    return queries.topics(subjectId, null);
  }

  public TopicResponse getTopic(UUID id) {
    access.topic(id);
    return queries.topics(null, id).getFirst();
  }
}
