package akegp.enforcement.domain;

public enum RunState {
  RECEIVED, PASSED, HELD, APPROVED_EXCEPTION, CHANGES_REQUESTED, REJECTED;

  public String wire() { return name().toLowerCase(); }
}
