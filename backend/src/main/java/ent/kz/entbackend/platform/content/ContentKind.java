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
  ASSIGNMENT;

  public boolean ent() {
    return ordinal() <= QUESTION.ordinal();
  }
}
