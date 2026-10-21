package akegp.enforcement.service;

import akegp.enforcement.domain.ChangeType;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The trigger check. Looks at file paths only (no file contents), so it is cheap. Heavy analysis stays in Component 2.
 * Must behave exactly like the classifier in the GitHub pipeline script (github-pipeline/src/classifier.js).
 */
public class ChangeClassifier {
  private static final Set<String> DOC_EXT = Set.of(".md", ".txt", ".rst", ".adoc");
  private static final Set<String> CONFIG_EXT =
      Set.of(".yml", ".yaml", ".json", ".env", ".toml", ".ini", ".properties", ".xml");
  /** Dependency manifests are JSON/XML but can change the architecture (new modules or dependencies). */
  private static final Set<String> CODE_AFFECTING_NAMES =
      Set.of("pom.xml", "build.gradle", "package.json", "settings.gradle");

  public ChangeType classify(List<String> filePaths) {
    List<String> files = filePaths == null ? List.of()
        : filePaths.stream().map(String::trim).filter(f -> !f.isEmpty()).toList();
    if (files.isEmpty()) return ChangeType.DOCUMENTATION; // nothing changed, nothing to scan
    boolean anyDoc = false, anyConfig = false;
    for (String f : files) {
      ChangeType kind = kindOf(f);
      if (kind == ChangeType.SOURCE_CODE) return ChangeType.SOURCE_CODE;
      if (kind == ChangeType.DOCUMENTATION) anyDoc = true; else anyConfig = true;
    }
    return anyConfig ? ChangeType.CONFIGURATION : ChangeType.DOCUMENTATION; // docs+config mix counts as configuration
  }

  ChangeType kindOf(String path) {
    String base = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    if (CODE_AFFECTING_NAMES.contains(base)) return ChangeType.SOURCE_CODE;
    String ext = extensionOf(base);
    if (DOC_EXT.contains(ext)) return ChangeType.DOCUMENTATION;
    if (CONFIG_EXT.contains(ext)) return ChangeType.CONFIGURATION;
    return ChangeType.SOURCE_CODE;
  }

  private static String extensionOf(String base) {
    int i = base.lastIndexOf('.');
    if (i < 0) return "";
    if (i == 0) return base; // dot-file such as ".env"
    return base.substring(i);
  }
}
