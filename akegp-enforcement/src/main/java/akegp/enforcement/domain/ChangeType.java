package akegp.enforcement.domain;

/** What kind of change a pull request contains. Only source-code changes need an architecture scan. */
public enum ChangeType {
  DOCUMENTATION("documentation", "Documentation"),
  CONFIGURATION("configuration", "Configuration"),
  SOURCE_CODE("code change", "Source code");

  private final String wire;
  private final String label;

  ChangeType(String wire, String label) { this.wire = wire; this.label = label; }

  /** Value used in the pipeline's JSON responses. */
  public String wire() { return wire; }
  /** Text shown in the dashboard. */
  public String label() { return label; }
  public boolean needsScan() { return this == SOURCE_CODE; }
}
