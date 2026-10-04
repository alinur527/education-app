package ent.kz.entbackend.platform;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PlatformException extends ResponseStatusException {

  private final String code;

  public PlatformException(int status, String code) {
    super(HttpStatus.valueOf(status));
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static PlatformException missing() {
    return new PlatformException(404, "NOT_FOUND");
  }

  public static PlatformException forbidden() {
    return new PlatformException(403, "FORBIDDEN");
  }

  public static void require(boolean condition, String code) {
    if (!condition) throw new PlatformException(400, code);
  }
}
