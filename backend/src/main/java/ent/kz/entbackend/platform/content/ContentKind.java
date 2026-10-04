package ent.kz.entbackend.platform.content;

public enum ContentKind {
  SUBJECT,
  TOPIC,
  THEORY,
  QUESTION,
  COURSE,
  MODULE,
  LESSON,
  QUIZ,
  ASSIGNMENT,
  CONTEXT;

  public boolean ent() {
    return java.util.Set.of(SUBJECT, TOPIC, THEORY, QUESTION, CONTEXT).contains(
      this
    );
  }
}
