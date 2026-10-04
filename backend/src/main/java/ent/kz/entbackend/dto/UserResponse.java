package ent.kz.entbackend.dto;

import ent.kz.entbackend.entity.UserRole;
import java.util.UUID;

public record UserResponse(
  UUID id,
  String email,
  String firstName,
  String lastName,
  String language,
  UserRole role
) {}
