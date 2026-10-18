package akegp.rules;

import akegp.rules.repository.ReadableIdRepository;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Test double only; PostgreSQL behavior is exercised separately. */
final class TestReadableIdRepository implements ReadableIdRepository {
  private record Key(UUID project, RecordType type) {}
  private final Map<Key, BigInteger> counters = new HashMap<>();

  synchronized void seed(UUID project, RecordType type, long lastNumber) {
    counters.put(new Key(project, type), BigInteger.valueOf(lastNumber));
  }

  @Override
  public synchronized String nextId(UUID project, RecordType type) {
    String digits = counters.merge(new Key(project, type), BigInteger.ONE, BigInteger::add).toString();
    return (type == RecordType.ADR ? "ADR-" : "R-")
        + "0".repeat(Math.max(0, 3 - digits.length())) + digits;
  }
}
