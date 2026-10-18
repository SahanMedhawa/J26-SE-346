package akegp.enforcement.conformance;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One architecture rule of the stand-in scanner. A rule belongs to an architecture style (LAYERED or MICROSERVICES), it
 * applies to some Java files (by folder prefix and/or by layer) and it is violated by a source line that matches a
 * pattern, for example a forbidden import. Test sources are never checked.
 *
 * A file belongs to a layer when a folder of its path is named like the layer, or when its class name ends with it
 * (for example controller/AccountApi.java or AccountController.java). Empty "pathPrefixes" means any folder and empty
 * "fromLayers" means any layer.
 *
 * These sample rules stand in for the rules that Component 1 will provide.
 */
public record ArchitectureRule(String ruleId, String severity, String style, List<String> pathPrefixes,
                               List<String> fromLayers, Pattern forbidden, boolean flagWhenUnreadable, String message) {

  public ArchitectureRule {
    pathPrefixes = pathPrefixes.stream().map(ArchitectureRule::normalisePrefix).toList();
    fromLayers = fromLayers.stream().map(layer -> layer.toLowerCase(Locale.ROOT)).toList();
  }

  private static String normalisePrefix(String prefix) {
    String p = prefix.replace('\\', '/').toLowerCase(Locale.ROOT);
    return p.endsWith("/") ? p : p + "/";
  }

  /** Files must not import anything that has one of these names as a package segment (a.b.NAME.X). */
  public static ArchitectureRule dependsOnLayer(String ruleId, String severity, String style, List<String> pathPrefixes,
                                                List<String> from, List<String> forbiddenSegments,
                                                boolean flagWhenUnreadable, String message) {
    String names = String.join("|", forbiddenSegments.stream().map(Pattern::quote).toList());
    Pattern pattern = Pattern.compile("^\\s*import\\s+(?:static\\s+)?(?:\\w+\\.)*(?:" + names + ")\\.[\\w.*]+\\s*;");
    return new ArchitectureRule(ruleId, severity, style, pathPrefixes, from, pattern, flagWhenUnreadable, message);
  }

  /** Files must not import anything that starts with one of these package or class prefixes. */
  public static ArchitectureRule dependsOnPackage(String ruleId, String severity, String style, List<String> pathPrefixes,
                                                  List<String> from, List<String> prefixes,
                                                  boolean flagWhenUnreadable, String message) {
    String starts = String.join("|", prefixes.stream().map(Pattern::quote).toList());
    Pattern pattern = Pattern.compile("^\\s*import\\s+(?:static\\s+)?(?:" + starts + ")[\\w.*]*\\s*;");
    return new ArchitectureRule(ruleId, severity, style, pathPrefixes, from, pattern, flagWhenUnreadable, message);
  }

  /** Any added source line that matches the pattern is a violation. */
  public static ArchitectureRule matchingLine(String ruleId, String severity, String style, List<String> pathPrefixes,
                                              List<String> from, Pattern line, boolean flagWhenUnreadable, String message) {
    return new ArchitectureRule(ruleId, severity, style, pathPrefixes, from, line, flagWhenUnreadable, message);
  }

  public boolean appliesTo(String path) {
    String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
    if (!p.endsWith(".java")) return false;
    if (p.startsWith("src/test/") || p.contains("/src/test/")) return false;
    if (!pathPrefixes.isEmpty() && pathPrefixes.stream().noneMatch(p::startsWith)) return false;
    if (fromLayers.isEmpty()) return true;
    int slash = p.lastIndexOf('/');
    String folders = slash < 0 ? "" : p.substring(0, slash);
    String className = p.substring(slash + 1, p.length() - ".java".length());
    for (String layer : fromLayers) {
      for (String folder : folders.split("/")) if (folder.equals(layer)) return true;
      if (className.endsWith(layer)) return true;
    }
    return false;
  }

  /** True when this added source line breaks the rule. Comment lines never do. */
  public boolean isViolatedBy(String sourceLine) {
    if (sourceLine == null) return false;
    String trimmed = sourceLine.stripLeading();
    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return false;
    return forbidden.matcher(sourceLine).find();
  }
}