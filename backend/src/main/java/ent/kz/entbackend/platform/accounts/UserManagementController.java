package ent.kz.entbackend.platform.accounts;

import ent.kz.entbackend.platform.content.ContentDtos.Page;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {

  private final UserManagementService service;

  public UserManagementController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Map<String, Object>> list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String role,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.list(q, role, page);
  }

  @PatchMapping("/{id}")
  public Map<String, Boolean> update(
    @PathVariable UUID id,
    @Valid @RequestBody UserManagementService.Change req
  ) {
    service.update(id, req);
    return Map.of("saved", true);
  }
}
