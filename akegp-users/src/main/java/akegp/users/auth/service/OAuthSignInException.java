package akegp.users.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OAuthSignInException extends ResponseStatusException {
  private final String code;
  public OAuthSignInException(HttpStatus status, String code, String reason) {
    super(status, reason);
    this.code = code;
  }
  public String code() { return code; }
}
