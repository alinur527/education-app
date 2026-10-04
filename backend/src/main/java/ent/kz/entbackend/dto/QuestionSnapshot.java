package ent.kz.entbackend.dto;

import java.util.*;

public record QuestionSnapshot(
  UUID id,
  UUID topicId,
  String topicRu,
  String topicKz,
  String questionRu,
  String questionKz,
  List<QuestionOptionResponse> options,
  String correctOptionId,
  String explanationRu,
  String explanationKz,
  String difficulty,
  Integer year
) {}
