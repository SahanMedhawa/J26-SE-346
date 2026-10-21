package akegp.analytics;

import akegp.analytics.repository.*;
import akegp.analytics.domain.AnalyticsRun;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectHistoryReadCacheTest {
  @Test void verifiesRevisionReusesHistoryAndNeverServesStaleOnDatabaseFailure() throws Exception {
    var ids = new AtomicReference<List<UUID>>(List.of());
    var failed = new AtomicBoolean();
    var query = proxy(PreparedStatement.class, (p, method, args) -> {
      if (!method.getName().equals("executeQuery")) return null;
      if (failed.get()) throw new SQLException("private diagnostic", "08006");
      var iterator = ids.get().iterator(); var current = new AtomicReference<UUID>();
      return proxy(ResultSet.class, (r, operation, parameters) -> switch (operation.getName()) {
        case "next" -> { if (!iterator.hasNext()) yield false; current.set(iterator.next()); yield true; }
        case "getObject" -> current.get();
        default -> null;
      });
    });
    var connection = proxy(Connection.class, (p, method, args) -> method.getName().equals("prepareStatement") ? query : null);
    var database = proxy(DataSource.class, (p, method, args) -> method.getName().equals("getConnection") ? connection : null);
    var reads = new AtomicInteger();
    var cache = new ProjectHistoryReadCache(database, project -> {
      reads.incrementAndGet();
      return ids.get().stream().map(id -> new AnalyticsRun(id, project, "main", id.toString(), 1,
          java.time.Instant.EPOCH, "repo", "main", "DEMO", "test", "commit", "COMPLETE", "PASS", "PASS",
          "v1", "v1", "v1", "v1", "v1", List.of(), 100L, Map.of())).toList();
    });
    UUID project = UUID.randomUUID();
    assertEquals(0, cache.readProject(project).size());
    assertEquals(0, cache.readProject(project).size());
    assertEquals(1, reads.get());
    ids.set(List.of(UUID.randomUUID()));
    var first = cache.readProject(project);
    assertSame(first, cache.readProject(project));
    assertEquals(2, reads.get());
    assertThrows(UnsupportedOperationException.class, () -> first.clear());
    cache.readProject(UUID.randomUUID());
    assertEquals(3, reads.get(), "Projects cannot share cached history");
    ids.set(List.of(ids.get().getFirst(), UUID.randomUUID()));
    assertEquals(2, cache.readProject(project).size());
    assertEquals(4, reads.get(), "A newly ingested run must be visible immediately");
    failed.set(true);
    var failure = assertThrows(IllegalStateException.class, () -> cache.readProject(project));
    assertFalse(failure.getMessage().contains("private diagnostic"));
  }
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
  }
}
