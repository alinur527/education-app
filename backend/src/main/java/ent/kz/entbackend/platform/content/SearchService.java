package ent.kz.entbackend.platform.content;

import ent.kz.entbackend.platform.Actor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SearchService {

  private final SearchRepository repository;
  private final Actor actor;

  public SearchService(SearchRepository repository, Actor actor) {
    this.repository = repository;
    this.actor = actor;
  }

  public List<Map<String, Object>> search(String query) {
    String q = query.trim();
    return q.isEmpty() || q.length() > 200
      ? List.of()
      : repository.search(actor.id(), q);
  }
}
