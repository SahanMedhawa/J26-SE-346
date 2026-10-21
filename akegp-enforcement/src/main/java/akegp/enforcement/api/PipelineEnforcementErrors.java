package akegp.enforcement.api;

import akegp.enforcement.service.EnforcementException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns module errors into {"error": "..."} with the right HTTP status; only applies to this module's controller. */
@RestControllerAdvice(assignableTypes = PipelineEnforcementApi.class)
public class PipelineEnforcementErrors {
  @ExceptionHandler(EnforcementException.class)
  public ResponseEntity<Map<String, String>> handle(EnforcementException e) {
    return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
  }
}
