package akegp.conformance.api;
import akegp.shared.api.ConformanceResultService;
import akegp.shared.dto.ViolationDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Service;
@RestController
public class ConformanceHealthController {
  @GetMapping("/api/conformance/health") public String health() { return "ok"; }
}
@Service
class ConformanceResultServiceImpl implements ConformanceResultService {
  public List<ViolationDto> findByRun(String runId) { return List.of(); }
}
