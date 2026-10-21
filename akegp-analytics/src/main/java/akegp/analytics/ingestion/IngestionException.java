package akegp.analytics.ingestion;

public final class IngestionException extends RuntimeException {
  public enum Code { INVALID, CONFLICT, OUT_OF_ORDER, FORBIDDEN }
  private final Code code;
  public IngestionException(Code code, String message) { super(message); this.code = code; }
  public Code code() { return code; }
}
