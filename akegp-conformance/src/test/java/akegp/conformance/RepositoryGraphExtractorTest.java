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
    assertTrue(graph.files().stream().anyMatch(f -> f.path().equals("src/java/App.java") && f.language().equals("Java")));
    assertTrue(graph.files().stream().anyMatch(f -> f.path().equals("src/web/App.tsx") && f.language().equals("TypeScript")));
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

  @Test
  void classifiesJavaFilesByAnnotation() throws Exception {
    Path root = Files.createTempDirectory("akegp-classify");
    Files.createDirectories(root.resolve("src/main/java/com/example"));
    Files.writeString(root.resolve("src/main/java/com/example/UserController.java"),
        "package com.example;\nimport org.springframework.web.bind.annotation.RestController;\n@RestController\npublic class UserController {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/UserService.java"),
        "package com.example;\nimport org.springframework.stereotype.Service;\n@Service\npublic class UserService {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/UserRepository.java"),
        "package com.example;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic interface UserRepository {}\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals("Controller", graph.files().stream().filter(f -> f.path().contains("UserController")).findFirst().get().kind());
    assertEquals("Service", graph.files().stream().filter(f -> f.path().contains("UserService")).findFirst().get().kind());
    assertEquals("Repository", graph.files().stream().filter(f -> f.path().contains("UserRepository")).findFirst().get().kind());
  }

  @Test
  void classifiesJavaFilesBySuffixWithoutAnnotations() throws Exception {
    Path root = Files.createTempDirectory("akegp-suffix");
    Files.createDirectories(root.resolve("src/main/java/com/example"));
    Files.writeString(root.resolve("src/main/java/com/example/OrderController.java"),
        "package com.example;\npublic class OrderController {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/OrderService.java"),
        "package com.example;\npublic class OrderService {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/OrderRepository.java"),
        "package com.example;\npublic interface OrderRepository {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/OrderDTO.java"),
        "package com.example;\npublic class OrderDTO {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/StringUtils.java"),
        "package com.example;\npublic class StringUtils {}\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals("Controller", graph.files().stream().filter(f -> f.path().contains("OrderController")).findFirst().get().kind());
    assertEquals("Service", graph.files().stream().filter(f -> f.path().contains("OrderService")).findFirst().get().kind());
    assertEquals("Repository", graph.files().stream().filter(f -> f.path().contains("OrderRepository")).findFirst().get().kind());
    assertEquals("Model", graph.files().stream().filter(f -> f.path().contains("OrderDTO")).findFirst().get().kind());
    assertEquals("Utility", graph.files().stream().filter(f -> f.path().contains("StringUtils")).findFirst().get().kind());
  }

  @Test
  void classifiesJavaFilesByDirectoryConvention() throws Exception {
    Path root = Files.createTempDirectory("akegp-dir");
    Files.createDirectories(root.resolve("src/main/java/com/example/controller"));
    Files.createDirectories(root.resolve("src/main/java/com/example/service"));
    Files.createDirectories(root.resolve("src/main/java/com/example/repository"));
    Files.writeString(root.resolve("src/main/java/com/example/controller/Order.java"),
        "package com.example.controller;\npublic class Order {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/service/Order.java"),
        "package com.example.service;\npublic class Order {}\n");
    Files.writeString(root.resolve("src/main/java/com/example/repository/Order.java"),
        "package com.example.repository;\npublic interface Order {}\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals("Controller", graph.files().stream().filter(f -> f.path().contains("/controller/")).findFirst().get().kind());
    assertEquals("Service", graph.files().stream().filter(f -> f.path().contains("/service/")).findFirst().get().kind());
    assertEquals("Repository", graph.files().stream().filter(f -> f.path().contains("/repository/")).findFirst().get().kind());
  }

  @Test
  void classifiesTypeScriptFilesByConvention() throws Exception {
    Path root = Files.createTempDirectory("akegp-ts-classify");
    Files.createDirectories(root.resolve("src/controllers"));
    Files.createDirectories(root.resolve("src/services"));
    Files.createDirectories(root.resolve("src/components"));
    Files.writeString(root.resolve("src/controllers/user.controller.ts"), "export class UserController {}\n");
    Files.writeString(root.resolve("src/services/user.service.ts"), "export class UserService {}\n");
    Files.writeString(root.resolve("src/components/UserCard.tsx"), "import { useState } from 'react';\nexport function UserCard() { const [x] = useState(0); return null; }\n");

    CodeGraph graph = new RepositoryGraphExtractor().extract(root);

    assertEquals("Controller", graph.files().stream().filter(f -> f.path().contains("user.controller")).findFirst().get().kind());
    assertEquals("Service", graph.files().stream().filter(f -> f.path().contains("user.service")).findFirst().get().kind());
    assertEquals("Component", graph.files().stream().filter(f -> f.path().contains("UserCard")).findFirst().get().kind());
  }
}
