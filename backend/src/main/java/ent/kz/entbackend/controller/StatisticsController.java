package ent.kz.entbackend.controller;

import ent.kz.entbackend.service.AuthService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

  private final JdbcTemplate db;
  private final AuthService auth;

  public StatisticsController(JdbcTemplate db, AuthService auth) {
    this.db = db;
    this.auth = auth;
  }

  @GetMapping("/me")
  @Transactional(readOnly = true)
  public Map<String, Object> me() {
    UUID id = auth.getCurrentUser().getId();
    Map<String, Object> result = new LinkedHashMap<>(
      db.queryForMap(
        """
        select count(*) as "testsTaken", coalesce(round(avg(score),2),0) as "averageScore",
        coalesce(max(score),0) as "bestScore", coalesce(sum(time_taken_secs),0) as "timeTakenSecs"
        from test_sessions where user_id=? and status='COMPLETED'
        """,
        id
      )
    );
    result.put(
      "recentAttempts",
      db.queryForList(
        """
        select s.id as "sessionId",s.topic_id as "topicId",s.subject_id as "subjectId",
        b.name_ru as "nameRu",b.name_kz as "nameKz",s.score,s.total_questions as "totalQuestions",
        s.correct_answers as "correctAnswers",s.completed_at as "completedAt"
        from test_sessions s join subjects b on b.id=s.subject_id where s.user_id=? and s.status='COMPLETED'
        order by s.completed_at desc,s.id desc limit 10
        """,
        id
      )
    );
    result.put(
      "subjects",
      db.queryForList(
        """
        select b.id as "subjectId",b.name_ru as "nameRu",b.name_kz as "nameKz",count(*) as "testsTaken",
        round(avg(s.score),2) as "averageScore",max(s.score) as "bestScore"
        from test_sessions s join subjects b on b.id=s.subject_id where s.user_id=? and s.status='COMPLETED'
        group by b.id,b.name_ru,b.name_kz order by b.name_ru
        """,
        id
      )
    );
    result.put(
      "activeAttempts",
      db.queryForList(
        """
        select s.id as "sessionId",s.topic_id as "topicId",b.name_ru as "nameRu",b.name_kz as "nameKz",
        s.total_questions as "totalQuestions",(select count(*) from test_answers a where a.session_id=s.id) as "answeredQuestions"
        from test_sessions s join subjects b on b.id=s.subject_id where s.user_id=? and s.status='IN_PROGRESS'
        order by s.updated_at desc limit 5
        """,
        id
      )
    );
    return result;
  }
}
