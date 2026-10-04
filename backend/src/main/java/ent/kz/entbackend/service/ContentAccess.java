package ent.kz.entbackend.service;

import ent.kz.entbackend.entity.*;
import ent.kz.entbackend.repository.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentAccess {

  private final TopicRepository topics;
  private final SubjectRepository subjects;

  public ContentAccess(TopicRepository topics, SubjectRepository subjects) {
    this.topics = topics;
    this.subjects = subjects;
  }

  public Subject subject(UUID id) {
    return subjects
      .findById(id)
      .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public Topic topic(UUID id) {
    Topic t = topics
      .findById(id)
      .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    subject(t.getSubject().getId());
    return t;
  }
}
