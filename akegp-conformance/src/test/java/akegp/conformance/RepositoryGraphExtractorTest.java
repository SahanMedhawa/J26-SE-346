package akegp.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.conformance.domain.CodeGraph;
import akegp.conformance.extraction.RepositoryGraphExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RepositoryGraphExtractorTest {
  @Test
  void extractsFilesAndRelativeTypeScriptDependencies() throws Exception {
    Path root = Files.createTempDirectory("akegp-graph");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/App.tsx"), "import { api } from './api';\nexport const app = api;\n");
    Files.writeString(root.resolve("src/api.ts"), "export const api = 'ok';\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals(2, graph.files().size());
    assertEquals(1, graph.dependencies().size());
    assertEquals(new CodeGraph.Dependency("src/App.tsx", "src/api.ts"), graph.dependencies().getFirst());
  }

  @Test
  void extractsMultipleLanguagesIntoOneGraph() throws Exception {
    Path root = Files.createTempDirectory("akegp-mixed-graph");
    Files.createDirectories(root.resolve("src/java"));
    Files.createDirectories(root.resolve("src/web"));
    Files.writeString(root.resolve("src/java/App.java"), "package app;\nclass App {}\n");
    Files.writeString(root.resolve("src/web/App.tsx"), "export const app = true;\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals(2, graph.files().size());
    assertTrue(graph.files().contains(new CodeGraph.CodeFile("src/java/App.java", "Java")));
    assertTrue(graph.files().contains(new CodeGraph.CodeFile("src/web/App.tsx", "TypeScript")));
  }

  @Test
  void extractsJavaImportsAfterTheFirstLineAndWildcardImports() throws Exception {
    Path root = Files.createTempDirectory("akegp-java-graph");
    Files.createDirectories(root.resolve("src/main/java/com/example/other"));
    Files.writeString(root.resolve("src/main/java/com/example/App.java"), "package com.example;\n\nimport com.example.other.Helper;\nimport com.example.other.*;\nclass App {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/other/Helper.java"), "package com.example.other;\nclass Helper {}\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals(2, graph.files().size());
    assertEquals(1, graph.dependencies().size());
    assertEquals(new CodeGraph.Dependency("src/main/java/com/example/App.java", "src/main/java/com/example/other/Helper.java"), graph.dependencies().getFirst());
  }

  @Test
  void extractsMavenModuleDependencies() throws Exception {
    Path root = Files.createTempDirectory("akegp-maven-graph");
    Files.createDirectories(root.resolve("module-a"));
    Files.createDirectories(root.resolve("module-b"));
    Files.writeString(root.resolve("pom.xml"), "<project><artifactId>root</artifactId><modules><module>module-a</module><module>module-b</module></modules></project>");
    Files.writeString(root.resolve("module-a/pom.xml"), "<project><artifactId>module-a</artifactId><dependencies><dependency><artifactId>module-b</artifactId></dependency></dependencies></project>");
    Files.writeString(root.resolve("module-b/pom.xml"), "<project><artifactId>module-b</artifactId></project>");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertTrue(graph.dependencies().contains(new CodeGraph.Dependency("module-a/pom.xml", "module-b/pom.xml")));
  }
}
