package akegp.rules.api;

import akegp.rules.service.DuplicateProjectException;
import akegp.rules.service.ProjectNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProjectRegistrationExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
  }

  @ExceptionHandler(DuplicateProjectException.class)
  ResponseEntity<ApiError> handleDuplicate(DuplicateProjectException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(ProjectNotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(ProjectNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
    return error(HttpStatus.BAD_REQUEST, "Request body contains invalid or unsupported values", Map.of());
  }

  private static ResponseEntity<ApiError> error(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fieldErrors));
  }
}
