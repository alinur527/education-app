package ent.kz.entbackend.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class JsonRequestLimitFilterTests {

  @Test
  void knownLengthJsonIsRejectedBeforeTheController() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setContentType("application/json");
    request.setContent(new byte[2 * 1024 * 1024 + 1]);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new JsonRequestLimitFilter().doFilter(request, response, chain);
    assertEquals(413, response.getStatus());
    assertNull(chain.getRequest());
  }

  @Test
  void vendorJsonWithoutContentLengthIsStillBounded() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/cms/content") {
      @Override
      public long getContentLengthLong() {
        return -1;
      }

      @Override
      public int getContentLength() {
        return -1;
      }
    };
    request.setContentType("application/vnd.education+json; charset=UTF-8");
    request.setContent(new byte[2 * 1024 * 1024 + 1]);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new JsonRequestLimitFilter().doFilter(request, response, chain);
    assertEquals(413, response.getStatus());
    assertNull(chain.getRequest());
  }

  @Test
  void acceptedJsonCanBeReadByJacksonDownstream() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/cms/content");
    byte[] body = "{\"text\":\"Қазақша\"}".getBytes(
      java.nio.charset.StandardCharsets.UTF_8
    );
    request.setContentType("application/json");
    request.setContent(body);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new JsonRequestLimitFilter().doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
    assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
  }
}
