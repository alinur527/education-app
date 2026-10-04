package ent.kz.entbackend.controller;

import ent.kz.entbackend.dto.AuthResponse;
import ent.kz.entbackend.dto.LoginRequest;
import ent.kz.entbackend.dto.RegisterRequest;
import ent.kz.entbackend.dto.UserResponse;
import ent.kz.entbackend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping(
    value = "/register",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping(
    value = "/login",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @org.springframework.web.bind.annotation.PatchMapping("/me/language")
  public UserResponse language(
    @Valid @RequestBody ent.kz.entbackend.dto.LanguageRequest request
  ) {
    return authService.updateLanguage(request.language());
  }

  @GetMapping(
    value = "/me",
    produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
  )
  public UserResponse me() {
    return authService.getMe();
  }
}
