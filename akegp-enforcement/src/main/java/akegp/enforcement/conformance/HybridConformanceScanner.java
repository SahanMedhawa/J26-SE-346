package akegp.enforcement.conformance;

/**
 * Hybrid mode: Component 2 is called on every scan so it can build its dependency graph from the pull request, but the
 * decision (the violations) still comes from the stand-in scanner. A failure inside Component 2 is logged and never
 * changes the outcome, so it cannot hold or release a pull request.
 */
public class HybridConformanceScanner implements ConformanceScanner {
  private static final System.Logger LOG = System.getLogger(HybridConformanceScanner.class.getName());

  private final ConformanceScanner decider;
  private final ConformanceScanner observer;

  public HybridConformanceScanner(ConformanceScanner decider, ConformanceScanner observer) {
    this.decider = decider;
    this.observer = observer;
  }

  @Override
  public ScanReport scan(ScanRequest request) {
    ScanReport decision = decider.scan(request);
    try {
      ScanReport seen = observer.scan(request);
      int n = seen == null || seen.violations() == null ? 0 : seen.violations().size();
      LOG.log(System.Logger.Level.INFO, "[hybrid] Component 2 scanned PR #{0}: {1} finding(s); decision uses the stand-in ({2})",
          request.prNumber(), n, decision.violations().size());
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "[hybrid] Component 2 failed for PR #" + request.prNumber()
          + ", decision unchanged: " + e);
    }
    return decision;
  }
}
