package ent.kz.entbackend.security;

import ent.kz.entbackend.entity.User;
import ent.kz.entbackend.service.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwt;
  private final UserDetailsService users;

  public JwtAuthenticationFilter(JwtService jwt, UserDetailsService users) {
    this.jwt = jwt;
    this.users = users;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return (
      r.getServletPath().equals("/api/auth/login") ||
      r.getServletPath().equals("/api/auth/register")
    );
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest req,
    HttpServletResponse res,
    FilterChain chain
  ) throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7).trim();
      String email = jwt.extractEmail(token);
      if (email != null) try {
        User user = (User) users.loadUserByUsername(email);
        if (
          jwt.isTokenValid(token, user)
        ) SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(
            user,
            null,
            user.getAuthorities()
          )
        );
      } catch (UsernameNotFoundException ignored) {
        /* Invalid identity is anonymous. */
      }
    }
    chain.doFilter(req, res);
  }
}
