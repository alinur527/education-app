package ent.kz.entbackend.dto;

public record LanguageRequest(
  @jakarta.validation.constraints.NotBlank
  @jakarta.validation.constraints.Pattern(regexp = "ru|kz")
  String language
) {}
