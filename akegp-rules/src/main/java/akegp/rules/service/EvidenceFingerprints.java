package akegp.rules.service;

import akegp.rules.domain.*;
import akegp.rules.validation.ArvmCatalogue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** SHA-256 over length-prefixed UTF-8 values; no timestamps, object identity, or hashCode. */
public final class EvidenceFingerprints {
  private EvidenceFingerprints() {}

  public static String hash(Object... values) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public static String structural(RegisteredProject project, ArchitectureRule rule,
      ArchitectureStyle selectedStyle, ArvmCatalogue catalogue) {
    var values = new ArrayList<Object>();
    values.add("ARVM structural inputs");
    values.add(project.architectureStyles().stream().map(Enum::name).sorted().toList().toString());
    values.add(selectedStyle);
    values.add(rule.sourceElementType());
    values.add(rule.relationshipType());
    values.add(rule.targetElementType());
    // The catalogue has no version. Hash its actual matching definitions to detect future changes.
    catalogue.signatures(selectedStyle).stream().sorted(java.util.Comparator.comparing(s -> s.signatureId()))
        .forEach(s -> {
          values.add(s.signatureId()); values.add(s.architectureStyle()); values.add(s.sourceElementType());
          values.add(s.relationshipType()); values.add(s.targetElementType());
        });
    return hash(values.toArray());
  }

  public static String rule(ArchitectureRule rule) {
    return hash(rule.id(), rule.projectId(), rule.ruleId(), rule.adrId(), rule.sourceElement(),
        rule.sourceElementType(), rule.relationshipType(), rule.targetElement(), rule.targetElementType(),
        rule.decision(), rule.description(), rule.severity());
  }

  public static String adr(ArchitectureDecisionRecord adr) {
    return adr == null ? null : hash(adr.id(), adr.adrId(), adr.version(), adr.title(), adr.status(),
        adr.context(), adr.decision(), adr.consequences());
  }

  public static String mappings(List<RuleImplementationMapping> mappings) {
    var values = new ArrayList<Object>();
    mappings.stream().sorted(java.util.Comparator.comparing((RuleImplementationMapping m) -> m.side().name())
        .thenComparing(RuleImplementationMapping::filePath)).forEach(m -> {
          values.add(m.side()); values.add(m.filePath());
        });
    return hash(values.toArray());
  }
}
