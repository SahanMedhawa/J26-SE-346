package akegp.enforcement.conformance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The sample rules of the stand-in scanner, grouped by architecture style. The style names are the same as the ones a
 * project is registered with (LAYERED, MICROSERVICES). Component 1 will provide the real rules.
 */
public final class RuleCatalog {
  private RuleCatalog() {}

  public static final String LAYERED = "LAYERED";
  public static final String MICROSERVICES = "MICROSERVICES";
  public static final Set<String> ALL_STYLES = Set.of(LAYERED, MICROSERVICES);

  /**
   * The business services of the demo repository: folder name -> package segment (kebab-case folder, camelCase package).
   * The shared library (lib-common) and the infrastructure servers are not listed, so depending on them is allowed.
   */
  public static final Map<String, String> SERVICES = serviceMap();

  private static Map<String, String> serviceMap() {
    Map<String, String> services = new LinkedHashMap<>();
    services.put("account-management", "accountManagement");
    services.put("card-management", "cardManagement");
    services.put("loan-management", "loanManagement");
    return services;
  }

  /**
   * The services of the HealthMate demo repository (folders below microservices/). They are TypeScript (Express) except
   * telemedicine-service, which is Java. The rules below apply to both languages.
   */
  public static final List<String> HEALTHMATE_SERVICES = List.of("patient-service", "doctor-service",
      "appointment-service", "admin-service", "telemedicine-service", "notification-service", "AI-service", "payment-service");

  private static final Pattern HARD_CODED_SERVICE_ADDRESS =
      Pattern.compile("[\"']https?://(?:localhost|127\\.0\\.0\\.1|[A-Za-z0-9.-]+):\\d{2,5}");

  /** The original rule. It is the only one that still flags by file path when the pipeline sent no code lines. */
  public static ArchitectureRule controllerMustNotUseRepository() {
    return ArchitectureRule.dependsOnLayer("R-014", "critical", LAYERED, List.of(), List.of("controller"),
        List.of("repository"), true, "Controller imports the repository layer directly; it should call the service layer");
  }

  public static List<ArchitectureRule> layered() {
    return List.of(
        controllerMustNotUseRepository(),
        ArchitectureRule.dependsOnPackage("R-015", "critical", LAYERED, List.of(), List.of("controller"),
            List.of("jakarta.persistence.", "javax.persistence.", "org.springframework.jdbc.", "org.hibernate."), false,
            "Controller uses a persistence API directly; data access belongs in the repository layer"),
        ArchitectureRule.dependsOnLayer("R-016", "high", LAYERED, List.of(), List.of("service"), List.of("controller"), false,
            "Service imports the controller layer; dependencies must point downwards"),
        ArchitectureRule.dependsOnLayer("R-017", "high", LAYERED, List.of(), List.of("repository"),
            List.of("service", "controller"), false,
            "Repository imports a higher layer (service or controller); dependencies must point downwards"),
        ArchitectureRule.dependsOnLayer("R-018", "medium", LAYERED, List.of(), List.of("model", "entity", "domain"),
            List.of("service", "repository", "controller"), false,
            "Domain class imports an application layer; domain classes should not depend on them"),
        ArchitectureRule.dependsOnPackage("R-019", "medium", LAYERED, List.of(), List.of("service"),
            List.of("jakarta.servlet.", "javax.servlet.", "org.springframework.web.bind.annotation.",
                "org.springframework.http.ResponseEntity"), false,
            "Service uses web types (HTTP); the web layer should stay in the controller"));
  }

  public static List<ArchitectureRule> microservices() { return microservices(SERVICES); }

  /** @param services folder name -> package segment of each business service */
  public static List<ArchitectureRule> microservices(Map<String, String> services) {
    List<ArchitectureRule> rules = new ArrayList<>();
    for (String folder : services.keySet()) {
      List<String> otherServices = services.entrySet().stream()
          .filter(entry -> !entry.getKey().equals(folder)).map(Map.Entry::getValue).toList();
      if (otherServices.isEmpty()) continue;
      rules.add(ArchitectureRule.dependsOnLayer("R-020", "high", MICROSERVICES, List.of(folder), List.of(),
          otherServices, false,
          "Service imports another service's code; services should communicate through their APIs, not shared code"));
    }
    List<String> addressFolders = new ArrayList<>(services.keySet());
    addressFolders.addAll(HEALTHMATE_SERVICES);
    rules.add(ArchitectureRule.matchingLine("R-021", "medium", MICROSERVICES, addressFolders, List.of(),
        HARD_CODED_SERVICE_ADDRESS, false,
        "Hard-coded service address; use service discovery or configuration instead"));
    for (String folder : HEALTHMATE_SERVICES) {
      List<String> others = HEALTHMATE_SERVICES.stream().filter(f -> !f.equals(folder)).toList();
      rules.add(ArchitectureRule.importsFromServiceFolder("R-020", "high", MICROSERVICES, List.of(folder), others,
          "Service imports another service's code; services should communicate through their APIs, not shared code"));
    }
    return rules;
  }

  /** Both styles, most severe first. */
  public static List<ArchitectureRule> all() {
    List<ArchitectureRule> rules = new ArrayList<>(layered());
    rules.addAll(microservices());
    rules.sort(Comparator.comparingInt(rule -> severityRank(rule.severity())));
    return List.copyOf(rules);
  }

  private static int severityRank(String severity) {
    return switch (severity) {
      case "critical" -> 0;
      case "high" -> 1;
      case "medium" -> 2;
      default -> 3;
    };
  }
}