package akegp.rules.api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class RulesHealthController {
  @GetMapping("/api/rules/health") public String health() { return "ok"; }
}
