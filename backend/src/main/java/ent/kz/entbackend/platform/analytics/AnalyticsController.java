package ent.kz.entbackend.platform.analytics;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics/me")
public class AnalyticsController {

  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/analytics")
  public AnalyticsDtos.Overview overview(
    @RequestParam(defaultValue = "7d") String period
  ) {
    return service.overview(period);
  }

  @GetMapping("/activity")
  public AnalyticsDtos.History history(
    @RequestParam(defaultValue = "7d") String period,
    @RequestParam(defaultValue = "ALL") String kind,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return service.history(period, kind, page, size);
  }
}
