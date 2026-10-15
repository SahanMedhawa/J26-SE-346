package akegp.rules.service;

public class StaleADRException extends RuntimeException {
  public StaleADRException() {
    super("This ADR was modified by another request. Refresh and try again.");
  }
}
