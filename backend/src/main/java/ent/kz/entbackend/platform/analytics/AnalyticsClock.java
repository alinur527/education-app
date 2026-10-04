package ent.kz.entbackend.platform.analytics;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration("analyticsClockConfiguration")
public class AnalyticsClock {

  @Bean("analyticsClock")
  Clock analyticsClock() {
    return Clock.systemUTC();
  }
}
