package akegp.analytics.domain;

/** A source directory is the module projection of a service finding; identities are never duplicated. */
public final class ArchitecturalAreas {
  private ArchitecturalAreas() {}
  public static String module(AnalyticsRun.Finding finding) {
    if ("MODULE".equals(finding.entityType())) return finding.entityKey();
    if (!"SERVICE".equals(finding.entityType()) || finding.location() == null) return null;
    String path = finding.location().replace('\\', '/').replaceFirst(":\\d+(?::\\d+)?$", "");
    int last = path.lastIndexOf('/');
    return last <= 0 ? null : path.substring(0, last);
  }
}
