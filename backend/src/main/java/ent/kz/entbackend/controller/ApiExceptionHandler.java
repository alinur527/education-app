package ent.kz.entbackend.controller;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ent.kz.entbackend.platform.PlatformException.class)
  ResponseEntity<Map<String, String>> platform(
    ent.kz.entbackend.platform.PlatformException e
  ) {
    return ResponseEntity.status(e.getStatusCode()).body(
      Map.of("code", e.code())
    );
  }

  @ExceptionHandler(
    org.springframework.web.multipart.MaxUploadSizeExceededException.class
  )
  ResponseEntity<Map<String, String>> uploadTooLarge(Exception e) {
    return ResponseEntity.status(413).body(Map.of("code", "FILE_TOO_LARGE"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
    int status = e.getStatusCode().value();
    String code = switch (status) {
      case 400 -> "INVALID_REQUEST";
      case 401 -> "UNAUTHORIZED";
      case 403 -> "FORBIDDEN";
      case 404 -> "NOT_FOUND";
      case 409 -> "CONFLICT";
      default -> "SERVER_ERROR";
    };
    return ResponseEntity.status(status).body(Map.of("code", code));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
  })
  ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> conflict(Exception e) {
    return ResponseEntity.status(409).body(Map.of("code", "CONFLICT"));
  }

  @ExceptionHandler(
    org.springframework.web.servlet.resource.NoResourceFoundException.class
  )
  ResponseEntity<Map<String, String>> missing(Exception e) {
    return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND"));
  }

  @ExceptionHandler(
    org.springframework.web.HttpRequestMethodNotSupportedException.class
  )
  ResponseEntity<Map<String, String>> method(Exception e) {
    return ResponseEntity.status(405).body(
      Map.of("code", "METHOD_NOT_ALLOWED")
    );
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass()).error(
      "Unexpected API failure: {}",
      e.getClass().getSimpleName()
    );
    return ResponseEntity.internalServerError().body(
      Map.of("code", "SERVER_ERROR")
    );
  }
}
