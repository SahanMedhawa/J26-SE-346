package akegp.analytics.ingestion;

/** Trusted server-side composition only. Never bind this object from a request. */
public record AdapterSource(String system, Provenance provenance, String dataset,
    boolean verifiedStableUpstreamIds) {
  public enum Provenance { DEMO, UPSTREAM }
  public AdapterSource {
    if (system == null || system.isBlank() || provenance == null || dataset == null
        || (provenance == Provenance.DEMO && dataset.isBlank())
        || (provenance == Provenance.UPSTREAM && !dataset.isEmpty())) {
      throw new IllegalArgumentException("Explicit source and valid provenance/dataset required");
    }
  }
  public String label() {
    return provenance == Provenance.DEMO
        ? "Demo snapshot - not upstream analysis" : "Upstream analysis";
  }
}
