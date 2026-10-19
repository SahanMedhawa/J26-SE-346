package akegp.rules.validation;

import static akegp.rules.domain.ArchitectureElementType.*;
import static akegp.rules.domain.ArchitectureStyle.*;
import static akegp.rules.domain.RelationshipType.*;

import akegp.rules.domain.ArchitectureStyle;
import java.util.List;
import org.springframework.stereotype.Component;

/** Proposed research catalogue; recognition does not imply architectural desirability. */
@Component
public class ArvmCatalogue {
  private static final List<ArvmSignature> SIGNATURES = List.of(
      new ArvmSignature("M01", MICROSERVICES, SERVICE, CALLS, SERVICE,
          "Recognizes SERVICE -> CALLS -> SERVICE."),
      new ArvmSignature("M02", MICROSERVICES, SERVICE, USES_API, SERVICE,
          "Recognizes SERVICE -> USES_API -> SERVICE."),
      new ArvmSignature("M03", MICROSERVICES, SERVICE, DEPENDS_ON, SERVICE,
          "Recognizes SERVICE -> DEPENDS_ON -> SERVICE."),
      new ArvmSignature("M04", MICROSERVICES, API_GATEWAY, CALLS, SERVICE,
          "Recognizes API_GATEWAY -> CALLS -> SERVICE."),
      new ArvmSignature("M05", MICROSERVICES, API_GATEWAY, USES_API, SERVICE,
          "Recognizes API_GATEWAY -> USES_API -> SERVICE."),
      new ArvmSignature("M06", MICROSERVICES, API_GATEWAY, DEPENDS_ON, SERVICE,
          "Recognizes API_GATEWAY -> DEPENDS_ON -> SERVICE."),
      new ArvmSignature("M07", MICROSERVICES, SERVICE, DATA_ACCESS, DATABASE,
          "Recognizes SERVICE -> DATA_ACCESS -> DATABASE."),
      new ArvmSignature("M08", MICROSERVICES, SERVICE, DIRECT_DATABASE_ACCESS, DATABASE,
          "Recognizes SERVICE -> DIRECT_DATABASE_ACCESS -> DATABASE."),
      new ArvmSignature("M09", MICROSERVICES, SERVICE, DEPENDS_ON, DATABASE,
          "Recognizes SERVICE -> DEPENDS_ON -> DATABASE."),
      new ArvmSignature("M10", MICROSERVICES, SERVICE, PUBLISHES_TO, MESSAGE_BROKER,
          "Recognizes SERVICE -> PUBLISHES_TO -> MESSAGE_BROKER."),
      new ArvmSignature("M11", MICROSERVICES, SERVICE, CONSUMES_FROM, MESSAGE_BROKER,
          "Recognizes SERVICE -> CONSUMES_FROM -> MESSAGE_BROKER."),
      new ArvmSignature("M12", MICROSERVICES, SERVICE, DEPENDS_ON, MESSAGE_BROKER,
          "Recognizes SERVICE -> DEPENDS_ON -> MESSAGE_BROKER."),
      new ArvmSignature("M13", MICROSERVICES, SERVICE, CALLS, EXTERNAL_SYSTEM,
          "Recognizes SERVICE -> CALLS -> EXTERNAL_SYSTEM."),
      new ArvmSignature("M14", MICROSERVICES, SERVICE, USES_API, EXTERNAL_SYSTEM,
          "Recognizes SERVICE -> USES_API -> EXTERNAL_SYSTEM."),
      new ArvmSignature("M15", MICROSERVICES, SERVICE, DEPENDS_ON, EXTERNAL_SYSTEM,
          "Recognizes SERVICE -> DEPENDS_ON -> EXTERNAL_SYSTEM."),
      new ArvmSignature("L01", LAYERED, CONTROLLER, CALLS, SERVICE_LAYER,
          "Recognizes CONTROLLER -> CALLS -> SERVICE_LAYER."),
      new ArvmSignature("L02", LAYERED, CONTROLLER, DEPENDS_ON, SERVICE_LAYER,
          "Recognizes CONTROLLER -> DEPENDS_ON -> SERVICE_LAYER."),
      new ArvmSignature("L03", LAYERED, SERVICE_LAYER, CALLS, REPOSITORY,
          "Recognizes SERVICE_LAYER -> CALLS -> REPOSITORY."),
      new ArvmSignature("L04", LAYERED, SERVICE_LAYER, DEPENDS_ON, REPOSITORY,
          "Recognizes SERVICE_LAYER -> DEPENDS_ON -> REPOSITORY."),
      new ArvmSignature("L05", LAYERED, REPOSITORY, DATA_ACCESS, DATABASE,
          "Recognizes REPOSITORY -> DATA_ACCESS -> DATABASE."),
      new ArvmSignature("L06", LAYERED, REPOSITORY, DEPENDS_ON, DATABASE,
          "Recognizes REPOSITORY -> DEPENDS_ON -> DATABASE."),
      new ArvmSignature("L07", LAYERED, CONTROLLER, CALLS, REPOSITORY,
          "Recognizes CONTROLLER -> CALLS -> REPOSITORY."),
      new ArvmSignature("L08", LAYERED, CONTROLLER, DEPENDS_ON, REPOSITORY,
          "Recognizes CONTROLLER -> DEPENDS_ON -> REPOSITORY."),
      new ArvmSignature("L09", LAYERED, CONTROLLER, DATA_ACCESS, DATABASE,
          "Recognizes CONTROLLER -> DATA_ACCESS -> DATABASE."),
      new ArvmSignature("L10", LAYERED, CONTROLLER, DIRECT_DATABASE_ACCESS, DATABASE,
          "Recognizes CONTROLLER -> DIRECT_DATABASE_ACCESS -> DATABASE."),
      new ArvmSignature("L11", LAYERED, SERVICE_LAYER, DATA_ACCESS, DATABASE,
          "Recognizes SERVICE_LAYER -> DATA_ACCESS -> DATABASE."),
      new ArvmSignature("L12", LAYERED, SERVICE_LAYER, DIRECT_DATABASE_ACCESS, DATABASE,
          "Recognizes SERVICE_LAYER -> DIRECT_DATABASE_ACCESS -> DATABASE."),
      new ArvmSignature("L13", LAYERED, SERVICE_LAYER, USES_API, EXTERNAL_SYSTEM,
          "Recognizes SERVICE_LAYER -> USES_API -> EXTERNAL_SYSTEM."),
      new ArvmSignature("L14", LAYERED, SERVICE_LAYER, CALLS, EXTERNAL_SYSTEM,
          "Recognizes SERVICE_LAYER -> CALLS -> EXTERNAL_SYSTEM."),
      new ArvmSignature("L15", LAYERED, SERVICE_LAYER, DEPENDS_ON, EXTERNAL_SYSTEM,
          "Recognizes SERVICE_LAYER -> DEPENDS_ON -> EXTERNAL_SYSTEM."));

  public List<ArvmSignature> signatures(ArchitectureStyle style) {
    requireSupportedStyle(style);
    return SIGNATURES.stream()
        .filter(signature -> style == null || signature.architectureStyle() == style)
        .toList();
  }

  public static void requireSupportedStyle(ArchitectureStyle style) {
    if (style != null && style != MICROSERVICES && style != LAYERED) {
      throw new RuleValidationException("Select MICROSERVICES or LAYERED for structural validation.");
    }
  }
}