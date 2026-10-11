package akegp.shared.api;
import akegp.shared.dto.ViolationDto;
import java.util.List;
public interface ConformanceResultService { List<ViolationDto> findByRun(String runId); }
