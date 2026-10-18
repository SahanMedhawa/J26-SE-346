package akegp.rules.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keep generic database error handling confined to Component 1 controllers. */
@RestControllerAdvice(basePackages = "akegp.rules.api")
public class RulePersistenceExceptionHandler {
  @ExceptionHandler(ConcurrencyFailureException.class)
  ResponseEntity<ApiError> concurrent(ConcurrencyFailureException exception) {
    return error(HttpStatus.CONFLICT, "Rule evidence changed concurrently; reload and retry the operation.");
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<ApiError> persistence(RuntimeException exception) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "Database operation could not be completed; retry later.");
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(new ApiError(
        Instant.now(), status.value(), status.getReasonPhrase(), message, Map.of()));
  }
}
