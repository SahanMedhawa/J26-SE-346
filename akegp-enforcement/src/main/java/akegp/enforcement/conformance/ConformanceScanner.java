package akegp.enforcement.conformance;

/**
 * Port to Component 2. Any failure must be thrown as a RuntimeException: Component 3 then holds the pull request
 * instead of letting it through unchecked.
 *
 * The real implementation will be a thin adapter over the conformance interface in akegp-shared-kernel.
 */
public interface ConformanceScanner {
  ScanReport scan(ScanRequest request);
}
