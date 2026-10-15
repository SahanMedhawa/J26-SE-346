package akegp.enforcement.service;

/** A problem the caller should hear about, with the HTTP status that fits it. */
public class EnforcementException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final int status;

  public EnforcementException(int status, String message) { super(message); this.status = status; }

  public int status() { return status; }
}
