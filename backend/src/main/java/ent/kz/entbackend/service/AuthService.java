package ent.kz.entbackend.service;

import ent.kz.entbackend.dto.AuthResponse;
import ent.kz.entbackend.dto.LoginRequest;
import ent.kz.entbackend.dto.RegisterRequest;
import ent.kz.entbackend.dto.UserResponse;
import ent.kz.entbackend.entity.User;
import ent.kz.entbackend.entity.UserRole;
import ent.kz.entbackend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
    UserRepository userRepository,
    PasswordEncoder passwordEncoder,
    JwtService jwtService
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @org.springframework.transaction.annotation.Transactional
  public AuthResponse register(RegisterRequest request) {
    if (
      request
        .password()
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        .length > 72
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid password length"
      );
    }
    String normalizedEmail = normalizeEmail(request.email());

    if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Email is already registered"
      );
    }

    User user = new User();
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setFirstName(request.firstName().trim());
    user.setLastName(request.lastName().trim());
    user.setLanguage(normalizeLanguage(request.language()));
    user.setRole(UserRole.STUDENT);
    user.setIsActive(true);

    User savedUser = userRepository.save(user);
    setAuthentication(savedUser);

    return buildAuthResponse(savedUser);
  }

  public AuthResponse login(LoginRequest request) {
    if (
      request
        .password()
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        .length > 72
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid password length"
      );
    }
    String normalizedEmail = normalizeEmail(request.email());
    User user = userRepository
      .findByEmailIgnoreCase(normalizedEmail)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Invalid email or password"
        )
      );

    if (
      !user.isEnabled() ||
      !passwordEncoder.matches(request.password(), user.getPasswordHash())
    ) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Invalid email or password"
      );
    }

    userRepository.recordLogin(user.getId(), LocalDateTime.now());
    setAuthentication(user);
    return buildAuthResponse(user);
  }

  public UserResponse updateLanguage(String language) {
    User user = getCurrentUser();
    userRepository.changeLanguage(user.getId(), language);
    user.setLanguage(language);
    return toUserResponse(user);
  }

  public UserResponse getMe() {
    return toUserResponse(getCurrentUser());
  }

  public User getCurrentUser() {
    Authentication authentication =
      SecurityContextHolder.getContext().getAuthentication();
    if (
      authentication == null ||
      !(authentication.getPrincipal() instanceof User user)
    ) {
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Unauthorized"
      );
    }

    return user;
  }

  private AuthResponse buildAuthResponse(User user) {
    return new AuthResponse(
      jwtService.generateToken(user),
      toUserResponse(user)
    );
  }

  private UserResponse toUserResponse(User user) {
    return new UserResponse(
      user.getId(),
      user.getEmail(),
      user.getFirstName(),
      user.getLastName(),
      user.getLanguage(),
      user.getRole()
    );
  }

  private void setAuthentication(User user) {
    UsernamePasswordAuthenticationToken authenticationToken =
      new UsernamePasswordAuthenticationToken(
        user,
        null,
        user.getAuthorities()
      );
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeLanguage(String language) {
    return language.trim().toLowerCase(Locale.ROOT);
  }
}
