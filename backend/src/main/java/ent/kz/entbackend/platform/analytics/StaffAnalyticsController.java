package ent.kz.entbackend.platform.analytics;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/analytics")
public class StaffAnalyticsController {

  private final StaffAnalyticsService service;

  public StaffAnalyticsController(StaffAnalyticsService service) {
    this.service = service;
  }

  @GetMapping
  public StaffAnalyticsService.Overview overview(
    @RequestParam(defaultValue = "7d") String period
  ) {
    return service.overview(period);
  }
}
