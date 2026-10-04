package ent.kz.entbackend.platform;

import ent.kz.entbackend.entity.*;
import ent.kz.entbackend.service.AuthService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Actor {

  private final AuthService auth;

  public Actor(AuthService auth) {
    this.auth = auth;
  }

  public User user() {
    return auth.getCurrentUser();
  }

  public UUID id() {
    return user().getId();
  }

  public boolean admin() {
    return user().getRole() == UserRole.ADMIN;
  }

  public boolean editor() {
    return admin() || user().getRole() == UserRole.CONTENT_EDITOR;
  }

  public boolean staff() {
    return editor() || user().getRole() == UserRole.TEACHER;
  }

  public void staffOnly() {
    if (!staff()) throw PlatformException.forbidden();
  }

  public void editorOnly() {
    if (!editor()) throw PlatformException.forbidden();
  }

  public void adminOnly() {
    if (!admin()) throw PlatformException.forbidden();
  }
}
