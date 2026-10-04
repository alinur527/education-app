package ent.kz.entbackend.config;

import ent.kz.entbackend.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwt)
    throws Exception {
    return http
      .csrf(c -> c.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .exceptionHandling(e ->
        e
          .authenticationEntryPoint((req, res, ex) -> {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"UNAUTHORIZED\"}");
          })
          .accessDeniedHandler((req, res, ex) -> {
            res.setStatus(403);
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"FORBIDDEN\"}");
          })
      )
      .authorizeHttpRequests(a ->
        a
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/auth/register",
            "/api/auth/login"
          )
          .permitAll()
          .requestMatchers("/api/test/ping", "/error")
          .permitAll()
          .requestMatchers("/api/admin/**")
          .hasRole("ADMIN")
          .requestMatchers("/api/cms/**")
          .hasAnyRole("ADMIN", "TEACHER", "CONTENT_EDITOR")
          .requestMatchers("/api/teacher/**")
          .hasAnyRole("ADMIN", "TEACHER")
          .anyRequest()
          .authenticated()
      )
      .httpBasic(c -> c.disable())
      .formLogin(c -> c.disable())
      .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
      .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
    @Value("${app.cors-origins}") String origins
  ) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(
      Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList()
    );
    c.setAllowedMethods(
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    );
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    c.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source =
      new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
