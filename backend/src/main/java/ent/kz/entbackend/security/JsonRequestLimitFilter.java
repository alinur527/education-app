package ent.kz.entbackend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforce a bounded body before Jackson, including requests without Content-Length. */
@Component
@Order(-100)
public class JsonRequestLimitFilter extends OncePerRequestFilter {

  private static final int MAX = 2 * 1024 * 1024;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    if (
      !request.getRequestURI().startsWith("/api/") ||
      request.getContentType() == null ||
      !(
        request
          .getContentType()
          .toLowerCase(java.util.Locale.ROOT)
          .contains("application/json") ||
        request
          .getContentType()
          .split(";")[0]
          .toLowerCase(java.util.Locale.ROOT)
          .endsWith("+json")
      )
    ) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > MAX) {
      reject(response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(MAX + 1);
    if (body.length > MAX) {
      reject(response);
      return;
    }
    chain.doFilter(
      new HttpServletRequestWrapper(request) {
        @Override
        public ServletInputStream getInputStream() {
          var stream = new ByteArrayInputStream(body);
          return new ServletInputStream() {
            public int read() {
              return stream.read();
            }

            public int read(byte[] b, int off, int len) {
              return stream.read(b, off, len);
            }

            public boolean isFinished() {
              return stream.available() == 0;
            }

            public boolean isReady() {
              return true;
            }

            public void setReadListener(ReadListener listener) {
              try {
                if (!isFinished()) listener.onDataAvailable();
                if (isFinished()) listener.onAllDataRead();
              } catch (IOException e) {
                listener.onError(e);
              }
            }
          };
        }

        @Override
        public BufferedReader getReader() {
          return new BufferedReader(
            new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)
          );
        }
      },
      response
    );
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType("application/json");
    response.getWriter().write("{\"code\":\"JSON_BODY_TOO_LARGE\"}");
  }
}
