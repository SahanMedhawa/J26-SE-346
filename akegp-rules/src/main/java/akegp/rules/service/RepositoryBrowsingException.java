package akegp.rules.service;

import org.springframework.http.HttpStatus;

public class RepositoryBrowsingException extends RuntimeException {
  private final HttpStatus status;
  private final String retryAfter;

  public RepositoryBrowsingException(HttpStatus status, String message) {
    this(status, message, null);
  }

  public RepositoryBrowsingException(HttpStatus status, String message, String retryAfter) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter != null && retryAfter.matches("[0-9]{1,10}") ? retryAfter : null;
  }

  public HttpStatus status() { return status; }

  public String retryAfter() { return retryAfter; }
}
