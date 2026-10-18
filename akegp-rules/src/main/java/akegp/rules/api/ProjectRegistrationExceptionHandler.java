package akegp.rules.api;

import akegp.rules.service.DuplicateProjectException;
import akegp.rules.service.ADRNotFoundException;
import akegp.rules.service.DuplicateADRException;
import akegp.rules.service.InactiveProjectException;
import akegp.rules.service.ProjectNotFoundException;
import akegp.rules.service.StaleADRException;
import akegp.rules.service.DuplicateRuleException;
import akegp.rules.service.RuleNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProjectRegistrationExceptionHandler {
  @ExceptionHandler(akegp.rules.service.RepositoryBrowsingException.class)
  ResponseEntity<ApiError> handleRepositoryBrowsing(
      akegp.rules.service.RepositoryBrowsingException exception) {
    ResponseEntity<ApiError> response = error(exception.status(), exception.getMessage(), Map.of());
    if (exception.retryAfter() == null) return response;
    return ResponseEntity.status(exception.status())
        .header("Retry-After", exception.retryAfter()).body(response.getBody());
  }

  @ExceptionHandler(akegp.rules.service.ImplementationMappingValidationException.class)
  ResponseEntity<ApiError> handleImplementationMappingValidation(
      akegp.rules.service.ImplementationMappingValidationException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(akegp.rules.service.ReadableIdException.class)
  ResponseEntity<ApiError> handleReadableId(akegp.rules.service.ReadableIdException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(akegp.rules.validation.RuleValidationException.class)
  ResponseEntity<ApiError> handleRuleValidation(
      akegp.rules.validation.RuleValidationException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
  }

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

  @ExceptionHandler(ADRNotFoundException.class)
  ResponseEntity<ApiError> handleADRNotFound(ADRNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(DuplicateADRException.class)
  ResponseEntity<ApiError> handleDuplicateADR(DuplicateADRException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(StaleADRException.class)
  ResponseEntity<ApiError> handleStaleADR(StaleADRException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(InactiveProjectException.class)
  ResponseEntity<ApiError> handleInactiveProject(InactiveProjectException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(DuplicateRuleException.class)
  ResponseEntity<ApiError> handleDuplicateRule(DuplicateRuleException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(RuleNotFoundException.class)
  ResponseEntity<ApiError> handleRuleNotFound(RuleNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
    return error(HttpStatus.BAD_REQUEST, "Request body contains invalid or unsupported values", Map.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
    return error(HttpStatus.BAD_REQUEST, "Request contains an invalid value", Map.of());
  }

  private static ResponseEntity<ApiError> error(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fieldErrors));
  }
}
