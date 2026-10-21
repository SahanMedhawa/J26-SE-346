package akegp.enforcement.github;

public enum StatusState {
  PENDING, SUCCESS, FAILURE, ERROR;

  public String wire() { return name().toLowerCase(); }
}
