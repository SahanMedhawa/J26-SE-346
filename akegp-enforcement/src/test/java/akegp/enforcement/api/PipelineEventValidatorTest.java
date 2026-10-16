package akegp.enforcement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.service.EnforcementException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Builds the same structure Spring hands to the controller (Map / List / String / Number) and validates it. */
class PipelineEventValidatorTest {

  private static Map<String, Object> m(Object... kv) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
    return map;
  }

  private static Map<String, Object> full() {
    return m("repo", "acme/shop", "repoUrl", "https://github.com/acme/shop", "prNumber", 7,
        "commitSha", "abc1234", "baseSha", "def5678",
        "changedFiles", List.of("src/A.java", "README.md"),
        "changes", List.of(
            m("path", "src/A.java", "status", "modified", "additions", 3, "deletions", 1, "binary", false,
                "addedLines", List.of(m("line", 10, "text", "x")), "removedLines", List.of()),
            m("path", "README.md", "status", "added", "additions", 5, "deletions", 0)));
  }

  private static Map<String, Object> minimal() {
    return m("repo", "a/b", "prNumber", 1, "commitSha", "abc1234", "changedFiles", List.of("x.md"));
  }

  private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(base);
    if (value == null) copy.remove(key); else copy.put(key, value);
    return copy;
  }

  @Test void parsesTheFullPayloadTheWorkflowSends() {
    PipelineEvent e = PipelineEventValidator.parse(full());
    assertEquals("acme/shop", e.repo());
    assertEquals(7, e.prNumber());
    assertEquals("def5678", e.baseSha());
    assertEquals(2, e.changes().size());
    assertEquals(10, e.changes().get(0).addedLines().get(0).line());
    assertEquals("added", e.changes().get(1).status());
    assertEquals(5, e.changes().get(1).additions());
  }

  @Test void changesAndBaseShaAreOptional() {
    PipelineEvent e = PipelineEventValidator.parse(minimal());
    assertNull(e.baseSha());
    // no detailed changes sent: every listed file still becomes an entry, so the file list is kept
    assertEquals(1, e.changes().size());
    assertEquals("x.md", e.changes().get(0).path());
    assertEquals("modified", e.changes().get(0).status());
  }

  @Test void rejectsBadInputWith400() {
    List<Map<String, Object>> bad = List.of(
        with(minimal(), "repo", "nope"),
        with(minimal(), "prNumber", 0),
        with(minimal(), "prNumber", 1.5),
        with(minimal(), "commitSha", "zzz"),
        with(minimal(), "changedFiles", null),
        with(minimal(), "baseSha", "xyz"),
        with(minimal(), "changedFiles", List.of("")),
        with(with(minimal(), "changedFiles", List.of("a")), "changes", List.of(m("path", "a", "status", "exploded"))),
        with(with(minimal(), "changedFiles", List.of("a")), "changes", List.of(m("path", "b", "status", "added"))));
    for (Map<String, Object> body : bad) {
      EnforcementException ex = assertThrows(EnforcementException.class, () -> PipelineEventValidator.parse(body));
      assertEquals(400, ex.status());
    }
  }
}
