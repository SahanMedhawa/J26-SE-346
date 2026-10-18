package akegp.conformance.domain;

import java.util.List;

public record CodeGraph(List<CodeFile> files, List<Dependency> dependencies) {
  public record CodeFile(String path, String language, String kind) {}
  public record Dependency(String source, String target) {}
}