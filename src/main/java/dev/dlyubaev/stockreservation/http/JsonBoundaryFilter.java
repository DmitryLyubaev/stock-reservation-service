package dev.dlyubaev.stockreservation.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonBoundaryFilter extends OncePerRequestFilter {
  private final JsonMapper mapper;

  public JsonBoundaryFilter(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getMethod().equals("POST")) {
      chain.doFilter(request, response);
      return;
    }
    String type = request.getContentType();
    if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
      reject(
          response,
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_MEDIA_TYPE",
          request.getRequestURI());
      return;
    }
    byte[] body = request.getInputStream().readNBytes(2049);
    if (body.length > 2048) {
      reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "BODY_TOO_LARGE", request.getRequestURI());
      return;
    }
    var keys = java.util.Collections.list(request.getHeaders("Idempotency-Key"));
    if (keys.size() != 1 || !keys.getFirst().matches("[A-Za-z0-9._:-]{1,128}")) {
      reject(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request.getRequestURI());
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public int getContentLength() {
            return body.length;
          }

          @Override
          public long getContentLengthLong() {
            return body.length;
          }

          @Override
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] b, int off, int len) {
                return input.read(b, off, len);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous request body");
              }
            };
          }
        },
        response);
  }

  private void reject(HttpServletResponse response, HttpStatus status, String code, String path)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(mapper.writeValueAsString(ProblemBody.of(status, code, path)));
  }
}
