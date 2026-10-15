package akegp.conformance.extraction;

import akegp.conformance.domain.CodeGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.springframework.stereotype.Component;

@Component
public class RepositoryGraphExtractor {
  private static final Set<String> SOURCE_EXTENSIONS = Set.of("java", "ts", "tsx", "js", "jsx");
  private static final Pattern JAVA_IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
  private static final Pattern JS_IMPORT = Pattern.compile("(?:from\\s*|import\\s*|require\\s*\\()(['\"])([^'\"]+)\\1");

  public CodeGraph extract(Path repositoryRoot) throws IOException {
    return extract(repositoryRoot, Set.of("Java", "TypeScript", "JavaScript"));
  }

  public CodeGraph extract(Path repositoryRoot, Set<String> languages) throws IOException {
    Path root = repositoryRoot.toAbsolutePath().normalize();
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Repository path is not a directory: " + root);
    }

    List<Path> sourcePaths;
    try (Stream<Path> paths = Files.walk(root)) {
      sourcePaths = paths.filter(Files::isRegularFile)
          .filter(path -> !isIgnored(path))
          .filter(path -> SOURCE_EXTENSIONS.contains(extension(path)))
          .filter(path -> languages.contains(language(path)))
          .sorted()
          .toList();
    }
        List<Path> pomPaths;
        try (Stream<Path> paths = Files.walk(root)) {
          pomPaths = paths.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equals("pom.xml"))
            .filter(path -> !isIgnored(path))
            .sorted()
            .toList();
        }

        SourceIndex index = buildIndex(root, sourcePaths);
        List<CodeGraph.CodeFile> files = Stream.concat(sourcePaths.stream(), pomPaths.stream())
        .map(path -> new CodeGraph.CodeFile(relative(root, path), language(path)))
        .toList();
    Set<String> dependencyKeys = new HashSet<>();
    List<CodeGraph.Dependency> dependencies = new ArrayList<>();

    for (Path source : sourcePaths) {
      String sourcePath = relative(root, source);
      String content = Files.readString(source);
      for (String imported : imports(source, content)) {
        for (String target : resolve(root, source, imported, index)) {
          if (target != null && !target.equals(sourcePath) && dependencyKeys.add(sourcePath + "->" + target)) {
          dependencies.add(new CodeGraph.Dependency(sourcePath, target));
          }
        }
      }
    }
    dependencies.addAll(buildModuleDependencies(root, pomPaths, dependencyKeys));
    return new CodeGraph(files, dependencies);
  }

  private SourceIndex buildIndex(Path root, List<Path> sourcePaths) throws IOException {
    Map<String, String> exact = new HashMap<>();
    Map<String, List<String>> packages = new HashMap<>();
    for (Path path : sourcePaths) {
      String relative = relative(root, path);
      String withoutExtension = relative.substring(0, relative.lastIndexOf('.'));
      exact.put(withoutExtension, relative);
      exact.putIfAbsent(path.getFileName().toString().replaceFirst("\\.[^.]+$", ""), relative);
      if (extension(path).equals("java")) {
        String packageName = packageName(Files.readString(path));
        if (!packageName.isBlank()) {
          exact.put(packageName + "." + path.getFileName().toString().replace(".java", ""), relative);
          packages.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(relative);
        }
      }
    }
    return new SourceIndex(exact, packages);
  }

  private List<String> resolve(Path root, Path source, String imported, SourceIndex index) {
    if (imported.startsWith(".")) {
      Path candidate = source.getParent().resolve(imported).normalize();
      for (String extension : SOURCE_EXTENSIONS) {
        String resolved = lookup(root, candidate.resolveSibling(candidate.getFileName() + "." + extension));
        if (resolved != null) return List.of(resolved);
      }
      String resolved = lookup(root, candidate);
      if (resolved != null) return List.of(resolved);
      for (String extension : SOURCE_EXTENSIONS) {
        resolved = lookup(root, candidate.resolve("index." + extension));
        if (resolved != null) return List.of(resolved);
      }
      return List.of();
    }
    String javaImport = imported.replace('/', '.');
    if (javaImport.endsWith(".*")) {
      return index.packages().getOrDefault(javaImport.substring(0, javaImport.length() - 2), List.of());
    }
    String resolved = index.exact().get(javaImport);
    if (resolved != null) return List.of(resolved);
    String module = imported.substring(imported.lastIndexOf('.') + 1);
    resolved = index.exact().get(module);
    return resolved == null ? List.of() : List.of(resolved);
  }

  private List<CodeGraph.Dependency> buildModuleDependencies(Path root, List<Path> pomPaths, Set<String> dependencyKeys) throws IOException {
    Map<String, String> modules = new HashMap<>();
    for (Path pom : pomPaths) {
      String artifactId = artifactId(pom);
      if (!artifactId.isBlank()) modules.put(artifactId, relative(root, pom));
    }
    List<CodeGraph.Dependency> dependencies = new ArrayList<>();
    for (Path pom : pomPaths) {
      String source = relative(root, pom);
      for (String artifactId : dependencyArtifactIds(pom)) {
        String target = modules.get(artifactId);
        if (target != null && !target.equals(source) && dependencyKeys.add(source + "->" + target)) {
          dependencies.add(new CodeGraph.Dependency(source, target));
        }
      }
    }
    return dependencies;
  }

  private String artifactId(Path pom) throws IOException {
    var document = xml(pom);
    var elements = document.getElementsByTagName("artifactId");
    return elements.getLength() == 0 ? "" : elements.item(0).getTextContent().trim();
  }

  private List<String> dependencyArtifactIds(Path pom) throws IOException {
    var document = xml(pom);
    List<String> artifactIds = new ArrayList<>();
    var dependencies = document.getElementsByTagName("dependency");
    for (int index = 0; index < dependencies.getLength(); index++) {
      if (dependencies.item(index) instanceof Element dependency) {
        var artifacts = dependency.getElementsByTagName("artifactId");
        if (artifacts.getLength() > 0) artifactIds.add(artifacts.item(0).getTextContent().trim());
      }
    }
    return artifactIds;
  }

  private org.w3c.dom.Document xml(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
    } catch (Exception error) {
      throw new IOException("Could not parse " + path, error);
    }
  }

  private String lookup(Path root, Path candidate) {
    if (!Files.isRegularFile(candidate)) return null;
    return relative(root, candidate);
  }

  private List<String> imports(Path source, String content) {
    List<String> imports = new ArrayList<>();
    if (extension(source).equals("java")) {
      Matcher matcher = JAVA_IMPORT.matcher(content);
      while (matcher.find()) imports.add(matcher.group(1));
    } else {
      Matcher matcher = JS_IMPORT.matcher(content);
      while (matcher.find()) imports.add(matcher.group(2));
    }
    return imports;
  }

  private boolean isIgnored(Path path) {
    return path.toString().contains(Path.of(".git").toString())
        || path.toString().contains(Path.of("node_modules").toString())
        || path.toString().contains(Path.of("target").toString())
        || path.toString().contains(Path.of("dist").toString());
  }

  private String relative(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }

  private String extension(Path path) {
    String name = path.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  private String language(Path path) {
    if (path.getFileName().toString().equals("pom.xml")) return "Maven";
    return switch (extension(path)) {
      case "java" -> "Java";
      case "ts", "tsx" -> "TypeScript";
      default -> "JavaScript";
    };
  }

  private String packageName(String content) {
    Matcher matcher = Pattern.compile("^\\s*package\\s+([\\w.]+)", Pattern.MULTILINE).matcher(content);
    return matcher.find() ? matcher.group(1) : "";
  }

  private record SourceIndex(Map<String, String> exact, Map<String, List<String>> packages) {}
}
