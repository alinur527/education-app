package ent.kz.entbackend.platform.materials;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class MaterialConfiguration {

  @Bean
  @ConditionalOnMissingBean(MalwareScanner.class)
  MalwareScanner scanner() {
    return (bytes, mime) -> {};
  }
}
