package ent.kz.entbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @ManyToOne
  @JoinColumn(name = "topic_id")
  private Topic topic;

  @Column(name = "topic_ru", length = 300)
  private String topicRu;

  @Column(name = "topic_kz", length = 300)
  private String topicKz;

  @Column(name = "question_ru", nullable = false)
  private String questionRu;

  @Column(name = "question_kz")
  private String questionKz;

  @Column(name = "options", nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(read = "options::text", write = "?::jsonb")
  private String options;

  @Column(name = "assessment", columnDefinition = "jsonb")
  @ColumnTransformer(read = "assessment::text", write = "?::jsonb")
  private String assessment;

  @Column(name = "correct_option_id", length = 10)
  private String correctOptionId;

  @Column(name = "explanation_ru")
  private String explanationRu;

  @Column(name = "explanation_kz")
  private String explanationKz;

  @Column(name = "difficulty", nullable = false, length = 10)
  private String difficulty;

  @Column(name = "year")
  private Integer year;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
