package akegp.rules.service;

public class DuplicateProjectException extends RuntimeException {
  public DuplicateProjectException(String message) {
    super(message);
  }
}
