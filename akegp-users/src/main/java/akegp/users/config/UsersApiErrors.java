package akegp.users.config;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice(basePackages="akegp.users")
public class UsersApiErrors {
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<Map<String,Object>> conflict(DataIntegrityViolationException e){return ResponseEntity.status(409).body(Map.of("status",409,"message","This record already exists or is still in use"));}
}
