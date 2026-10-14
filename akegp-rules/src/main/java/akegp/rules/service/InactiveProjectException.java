package akegp.rules.service;

public class InactiveProjectException extends RuntimeException {
  public InactiveProjectException() {
    super("Cannot create an ADR for an inactive project.");
  }
}
