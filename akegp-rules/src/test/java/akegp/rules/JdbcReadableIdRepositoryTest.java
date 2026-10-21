package akegp.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import akegp.rules.repository.JdbcReadableIdRepository;
import akegp.rules.repository.ReadableIdRepository.RecordType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcReadableIdRepositoryTest {
  @ParameterizedTest
  @CsvSource({"ADR,1,ADR-001", "ADR,2,ADR-002", "RULE,1,R-001", "RULE,2,R-002",
      "ADR,999,ADR-999", "ADR,1000,ADR-1000", "RULE,999,R-999", "RULE,1000,R-1000",
      "ADR,9223372036854775808,ADR-9223372036854775808"})
  void formatsDatabaseAllocatedNumberWithoutTruncation(RecordType type, String number, String expected) {
    UUID project = UUID.randomUUID();
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(project), eq(type.name())))
        .thenReturn(new BigDecimal(number));
    assertEquals(expected, new JdbcReadableIdRepository(jdbc).nextId(project, type));
  }
}
