package akegp.analytics.repository;

import akegp.analytics.domain.AnalyticsRun;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface AnalyticsReadRepository {
  List<AnalyticsRun> readProject(UUID projectId);
}
