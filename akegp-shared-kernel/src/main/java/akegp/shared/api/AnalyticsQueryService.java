package akegp.shared.api;
import akegp.shared.dto.CicdRunDto;
import java.util.List;
public interface AnalyticsQueryService { List<CicdRunDto> recentRuns(); }
