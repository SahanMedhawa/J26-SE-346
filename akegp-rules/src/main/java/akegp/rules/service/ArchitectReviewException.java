package akegp.rules.service;

import org.springframework.http.HttpStatus;

public class ArchitectReviewException extends RuntimeException {
  private final HttpStatus status;

  public ArchitectReviewException(HttpStatus status, String message) { super(message); this.status = status; }

  public HttpStatus status() { return status; }
}
