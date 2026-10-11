package akegp.analytics.api;
import akegp.shared.api.AnalyticsQueryService;
import akegp.shared.dto.CicdRunDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Service;
@RestController
public class AnalyticsHealthController {
  @GetMapping("/api/analytics/health") public String health() { return "ok"; }
}
@Service
class AnalyticsQueryServiceImpl implements AnalyticsQueryService {
  public List<CicdRunDto> recentRuns() { return List.of(); }
}
