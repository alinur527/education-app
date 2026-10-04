package ent.kz.entbackend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartTestRequest(@NotNull UUID topicId) {}
