package akegp.enforcement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import akegp.enforcement.domain.ChangeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeClassifierTest {
  private final ChangeClassifier c = new ChangeClassifier();

  @Test void docsOnly() { assertEquals(ChangeType.DOCUMENTATION, c.classify(List.of("README.md", "docs/a.txt"))); }

  @Test void configOnly() {
    assertEquals(ChangeType.CONFIGURATION, c.classify(List.of(".github/workflows/x.yml", "app.json")));
  }

  @Test void docsAndConfigMixIsConfiguration() {
    assertEquals(ChangeType.CONFIGURATION, c.classify(List.of("a.md", "b.yml")));
  }

  @Test void anySourceFileMakesItACodeChange() {
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("README.md", "src/A.java")));
  }

  @Test void dependencyManifestsCountAsCode() {
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("pom.xml")));
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("akegp-enforcement/pom.xml")));
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("package.json")));
  }

  @Test void unknownAndMissingExtensionsAreCode() {
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("Dockerfile")));
    assertEquals(ChangeType.SOURCE_CODE, c.classify(List.of("src/x.ts")));
  }

  @Test void dotEnvIsConfiguration() { assertEquals(ChangeType.CONFIGURATION, c.classify(List.of(".env"))); }

  @Test void upperCaseExtensionHandled() { assertEquals(ChangeType.DOCUMENTATION, c.classify(List.of("README.MD"))); }

  @Test void emptyOrBlankNeedsNoScan() {
    assertEquals(ChangeType.DOCUMENTATION, c.classify(List.of()));
    assertEquals(ChangeType.DOCUMENTATION, c.classify(List.of("  ", "")));
    assertEquals(ChangeType.DOCUMENTATION, c.classify(null));
  }
}
