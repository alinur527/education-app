package ent.kz.entbackend.platform.materials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class MaterialConfiguration {

  @Bean
  @ConditionalOnMissingBean(MalwareScanner.class)
  MalwareScanner scanner(
    @Value("${app.scan.engine:none}") String engine,
    @Value("${app.scan.host:127.0.0.1}") String host,
    @Value("${app.scan.port:3310}") int port,
    @Value("${app.scan.timeout-ms:10000}") int timeout
  ) {
    if (engine.equals("clamav")) return new ClamAvScanner(host, port, timeout);
    if (!engine.equals("none")) throw new IllegalArgumentException(
      "Unknown malware scanner engine"
    );
    return (bytes, mime) ->
      new MalwareScanner.Result(
        MalwareScanner.Status.UNSCANNED,
        "none",
        "SCANNER_NOT_CONFIGURED"
      );
  }
}
