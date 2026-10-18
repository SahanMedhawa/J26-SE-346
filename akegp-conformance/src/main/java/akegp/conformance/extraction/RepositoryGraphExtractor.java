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

  // Java annotation patterns
  private static final Pattern JAVA_ANNOTATION = Pattern.compile(
      "@(RestController|Controller|Service|Repository|Component|Configuration|Entity|MappedSuperclass|Embeddable)\\b");
  // Java inheritance patterns
  private static final Pattern JAVA_EXTENDS_REPO = Pattern.compile(
      "(?:extends|implements)\\s+\\w*(?:Repository|CrudRepository|JpaRepository|PagingAndSortingRepository|MongoRepository|ReactiveCrudRepository|ReactiveMongoRepository|ElasticsearchRepository)\\b");
  // Java HTTP mapping annotations (indicates controller-like)
  private static final Pattern JAVA_MAPPING = Pattern.compile(
      "@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\b");
  // NestJS decorators
  private static final Pattern NESTJS_CONTROLLER = Pattern.compile("@Controller\\s*\\(");
  private static final Pattern NESTJS_INJECTABLE = Pattern.compile("@Injectable\\s*\\(");

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
    // Also detect package.json for JS/TS projects
    List<Path> packageJsonPaths;
    try (Stream<Path> paths = Files.walk(root)) {
      packageJsonPaths = paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals("package.json"))
          .filter(path -> !isIgnored(path))
          .sorted()
          .toList();
    }

    // Read file contents for classification
    Map<Path, String> fileContents = new HashMap<>();
    for (Path source : sourcePaths) {
      fileContents.put(source, Files.readString(source));
    }

    SourceIndex index = buildIndex(root, sourcePaths, fileContents);

    List<CodeGraph.CodeFile> files = new ArrayList<>();
    // Add source files with classification
    for (Path source : sourcePaths) {
      String rel = relative(root, source);
      String lang = language(source);
      String content = fileContents.get(source);
      String kind = classifyFile(source, rel, lang, content);
      files.add(new CodeGraph.CodeFile(rel, lang, kind));
    }
    // Add pom.xml files
    for (Path pom : pomPaths) {
      files.add(new CodeGraph.CodeFile(relative(root, pom), "Maven", "Build"));
    }
    // Add package.json files
    for (Path pkg : packageJsonPaths) {
      files.add(new CodeGraph.CodeFile(relative(root, pkg), "npm", "Build"));
    }

    Set<String> dependencyKeys = new HashSet<>();
    List<CodeGraph.Dependency> dependencies = new ArrayList<>();

    for (Path source : sourcePaths) {
      String sourcePath = relative(root, source);
      String content = fileContents.get(source);
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

  /**
   * Classifies a source file using a multi-layered strategy.
   * Priority order:
   *   1. Java annotations (@Controller, @Service, @Repository, etc.)
   *   2. Java inheritance (extends JpaRepository, etc.)
   *   3. Content-based heuristics (@GetMapping, NestJS decorators)
   *   4. Class/filename suffix conventions (*Controller, *Service, *Repository, etc.)
   *   5. Directory/package path conventions (controller/, service/, repository/, etc.)
   *   6. TypeScript/JS dotted filename conventions (*.controller.ts, *.service.ts, etc.)
   *   7. Fallback: "Internal" (never "External" for files in the repo)
   */
  String classifyFile(Path filePath, String relativePath, String lang, String content) {
    if ("Maven".equals(lang) || "npm".equals(lang)) return "Build";

    String lowerPath = relativePath.toLowerCase(Locale.ROOT);
    String fileName = filePath.getFileName().toString();
    String lowerFileName = fileName.toLowerCase(Locale.ROOT);
    // Remove extension for suffix matching
    String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

    // === Layer 1: Java annotations ===
    if ("Java".equals(lang) && content != null) {
      Matcher annotationMatcher = JAVA_ANNOTATION.matcher(content);
      if (annotationMatcher.find()) {
        String annotation = annotationMatcher.group(1);
        return switch (annotation) {
          case "RestController", "Controller" -> "Controller";
          case "Service" -> "Service";
          case "Repository" -> "Repository";
          case "Configuration" -> "Config";
          case "Entity", "MappedSuperclass", "Embeddable" -> "Model";
          case "Component" -> classifyComponentFurther(filePath, relativePath, baseName, content);
          default -> "Internal";
        };
      }
    }

    // === Layer 2: Java inheritance/implements patterns ===
    if ("Java".equals(lang) && content != null) {
      if (JAVA_EXTENDS_REPO.matcher(content).find()) return "Repository";
    }

    // === Layer 3: Content-based heuristics ===
    if ("Java".equals(lang) && content != null) {
      if (JAVA_MAPPING.matcher(content).find()) return "Controller";
    }
    if (("TypeScript".equals(lang) || "JavaScript".equals(lang)) && content != null) {
      if (NESTJS_CONTROLLER.matcher(content).find()) return "Controller";
      if (NESTJS_INJECTABLE.matcher(content).find()) {
        // Disambiguate injectable: check path/name for service vs repository vs guard vs pipe
        if (lowerPath.contains("/service") || lowerFileName.contains("service")) return "Service";
        if (lowerPath.contains("/repositor") || lowerFileName.contains("repository") || lowerFileName.contains("repo")) return "Repository";
        return "Service";
      }
    }

    // === Layer 4: Class/filename suffix conventions ===
    String kind = classifyBySuffix(baseName);
    if (kind != null) return kind;

    // === Layer 5: Directory/package path conventions ===
    kind = classifyByPath(lowerPath);
    if (kind != null) return kind;

    // === Layer 6: TypeScript/JS dotted filename conventions ===
    if ("TypeScript".equals(lang) || "JavaScript".equals(lang)) {
      kind = classifyByDottedName(lowerFileName);
      if (kind != null) return kind;
      // React component heuristic: TSX/JSX files that export a function matching the filename
      if ((lowerFileName.endsWith(".tsx") || lowerFileName.endsWith(".jsx")) && content != null) {
        if (content.contains("export default function") || content.contains("export function")
            || content.contains("React.FC") || content.contains("React.Component")
            || content.contains("useState") || content.contains("useEffect")) {
          return "Component";
        }
      }
    }

    // === Fallback ===
    return "Internal";
  }

  /** Further classify a @Component-annotated Java class by its name/path */
  private String classifyComponentFurther(Path filePath, String relativePath, String baseName, String content) {
    String kind = classifyBySuffix(baseName);
    if (kind != null) return kind;
    kind = classifyByPath(relativePath.toLowerCase(Locale.ROOT));
    if (kind != null) return kind;
    return "Component";
  }

  /** Classify by class/filename suffix (case-insensitive) */
  private String classifyBySuffix(String baseName) {
    String lower = baseName.toLowerCase(Locale.ROOT);
    // Controller layer
    if (lower.endsWith("controller") || lower.endsWith("restcontroller")
        || lower.endsWith("resource") || lower.endsWith("endpoint") || lower.endsWith("api")) {
      return "Controller";
    }
    // Service layer
    if (lower.endsWith("service") || lower.endsWith("serviceimpl")
        || lower.endsWith("usecase") || lower.endsWith("interactor")
        || lower.endsWith("manager") || lower.endsWith("provider")
        || lower.endsWith("facade") || lower.endsWith("orchestrator")) {
      return "Service";
    }
    // Repository/DAO layer
    if (lower.endsWith("repository") || lower.endsWith("repositoryimpl")
        || lower.endsWith("repo") || lower.endsWith("dao") || lower.endsWith("daoimpl")
        || lower.endsWith("store") || lower.endsWith("gateway") || lower.endsWith("datasource")) {
      return "Repository";
    }
    // Model/Entity
    if (lower.endsWith("entity") || lower.endsWith("model")
        || lower.endsWith("dto") || lower.endsWith("vo") || lower.endsWith("pojo")
        || lower.endsWith("request") || lower.endsWith("response") || lower.endsWith("command")
        || lower.endsWith("event") || lower.endsWith("aggregate")) {
      return "Model";
    }
    // Config
    if (lower.endsWith("config") || lower.endsWith("configuration")
        || lower.endsWith("properties") || lower.endsWith("settings")) {
      return "Config";
    }
    // Utility
    if (lower.endsWith("util") || lower.endsWith("utils") || lower.endsWith("utility")
        || lower.endsWith("helper") || lower.endsWith("helpers") || lower.endsWith("constants")
        || lower.endsWith("converter") || lower.endsWith("mapper") || lower.endsWith("adapter")
        || lower.endsWith("transformer") || lower.endsWith("serializer") || lower.endsWith("deserializer")) {
      return "Utility";
    }
    // Exception
    if (lower.endsWith("exception") || lower.endsWith("error")
        || lower.endsWith("handler") && lower.contains("exception")) {
      return "Exception";
    }
    // Middleware/Filter/Interceptor
    if (lower.endsWith("filter") || lower.endsWith("interceptor")
        || lower.endsWith("middleware") || lower.endsWith("guard") || lower.endsWith("pipe")) {
      return "Middleware";
    }
    return null;
  }

  /** Classify by directory path segments */
  private String classifyByPath(String lowerPath) {
    String[] segments = lowerPath.split("/");
    for (String segment : segments) {
      switch (segment) {
        case "controller", "controllers", "api", "rest", "web", "endpoint", "endpoints", "resource", "resources" -> {
          // Only classify as Controller if it's a directory name, not the filename itself
          if (!segment.equals(segments[segments.length - 1])) return "Controller";
        }
        case "service", "services", "usecase", "usecases", "interactor", "interactors", "application" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Service";
        }
        case "repository", "repositories", "repo", "repos", "dao", "persistence", "dataaccess", "data-access" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Repository";
        }
        case "model", "models", "entity", "entities", "domain", "dto", "dtos", "vo", "pojo" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Model";
        }
        case "config", "configuration", "configurations" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Config";
        }
        case "util", "utils", "utility", "utilities", "helper", "helpers", "common", "commons", "shared", "lib" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Utility";
        }
        case "exception", "exceptions", "error", "errors" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Exception";
        }
        case "filter", "filters", "interceptor", "interceptors", "middleware", "middlewares", "guard", "guards", "pipe", "pipes" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Middleware";
        }
        case "component", "components", "page", "pages", "view", "views", "screen", "screens", "widget", "widgets" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Component";
        }
        case "hook", "hooks" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Hook";
        }
        case "route", "routes", "router", "routing" -> {
          if (!segment.equals(segments[segments.length - 1])) return "Controller";
        }
        case "module", "modules" -> {
          // modules is too generic, skip
        }
        default -> {}
      }
    }
    return null;
  }

  /** Classify TypeScript/JS files by dotted naming convention (e.g. user.controller.ts) */
  private String classifyByDottedName(String lowerFileName) {
    if (lowerFileName.contains(".controller.") || lowerFileName.contains(".ctrl.")) return "Controller";
    if (lowerFileName.contains(".service.") || lowerFileName.contains(".svc.")) return "Service";
    if (lowerFileName.contains(".repository.") || lowerFileName.contains(".repo.")) return "Repository";
    if (lowerFileName.contains(".model.") || lowerFileName.contains(".entity.") || lowerFileName.contains(".dto.")) return "Model";
    if (lowerFileName.contains(".config.") || lowerFileName.contains(".configuration.")) return "Config";
    if (lowerFileName.contains(".util.") || lowerFileName.contains(".utils.") || lowerFileName.contains(".helper.")) return "Utility";
    if (lowerFileName.contains(".middleware.") || lowerFileName.contains(".guard.") || lowerFileName.contains(".pipe.") || lowerFileName.contains(".filter.") || lowerFileName.contains(".interceptor.")) return "Middleware";
    if (lowerFileName.contains(".component.")) return "Component";
    if (lowerFileName.contains(".hook.") || lowerFileName.startsWith("use")) return "Hook";
    if (lowerFileName.contains(".module.")) return "Module";
    if (lowerFileName.contains(".spec.") || lowerFileName.contains(".test.")) return "Test";
    if (lowerFileName.contains(".route.") || lowerFileName.contains(".routes.") || lowerFileName.contains(".router.")) return "Controller";
    return null;
  }

  private SourceIndex buildIndex(Path root, List<Path> sourcePaths, Map<Path, String> fileContents) throws IOException {
    Map<String, String> exact = new HashMap<>();
    Map<String, List<String>> packages = new HashMap<>();
    for (Path path : sourcePaths) {
      String relative = relative(root, path);
      String withoutExtension = relative.substring(0, relative.lastIndexOf('.'));
      exact.put(withoutExtension, relative);
      exact.putIfAbsent(path.getFileName().toString().replaceFirst("\\.[^.]+$", ""), relative);
      if (extension(path).equals("java")) {
        String content = fileContents.get(path);
        if (content != null) {
          String packageName = packageName(content);
          if (!packageName.isBlank()) {
            exact.put(packageName + "." + path.getFileName().toString().replace(".java", ""), relative);
            packages.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(relative);
          }
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
        || path.toString().contains(Path.of("dist").toString())
        || path.toString().contains(Path.of("build").toString())
        || path.toString().contains(Path.of("__pycache__").toString())
        || path.toString().contains(Path.of(".next").toString());
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
    if (path.getFileName().toString().equals("package.json")) return "npm";
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
