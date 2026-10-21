package akegp.shared.api;

import akegp.shared.dto.ScanRequestDto;
import akegp.shared.dto.ScanResultDto;

/**
 * Implemented by the Conformance module, called by the Enforcement module.
 * Runs the scan and returns the findings. Throw a RuntimeException if the scan cannot be done:
 * Enforcement then keeps the pull request on hold instead of letting it through.
 */
public interface ConformanceScanService {
  ScanResultDto scan(ScanRequestDto request);
}