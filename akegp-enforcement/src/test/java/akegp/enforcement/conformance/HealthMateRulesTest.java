package akegp.enforcement.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.Violation;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The stand-in scanner on the HealthMate repository (TypeScript services plus one Java service). */
class HealthMateRulesTest {
  private static final String PATIENT_CONTROLLER = "microservices/patient-service/src/controllers/patient.controller.ts";
  private static final String TELE_CONTROLLER =
      "microservices/telemedicine-service/src/main/java/com/healthmate/telemedicine/controller/ConsultationController.java";

  private final MockConformanceScanner scanner = new MockConformanceScanner();

  private static FileChange modified(String path, String... lines) {
    LineChange[] added = new LineChange[lines.length];
    for (int i = 0; i < lines.length; i++) added[i] = new LineChange(i + 1, lines[i]);
    return new FileChange(path, "modified", null, added.length, 0, false, Arrays.asList(added), null, null);
  }

  private List<Violation> scan(FileChange change) {
    return scanner.scan(new ScanRequest("https://github.com/SahanMedhawa/HealthMate", "abc1234", "def5678", 5, "proj-1",
        List.of(change.path()), List.of(change))).violations();
  }

  @Test void importingAnotherServicesCodeIsAViolation() {
    List<Violation> v = scan(modified(PATIENT_CONTROLLER,
        "import { Doctor } from \"../../../doctor-service/src/models/doctor.model\";"));
    assertEquals(1, v.size());
    assertEquals("R-020", v.get(0).ruleId());
    assertEquals(PATIENT_CONTROLLER, v.get(0).file());
  }

  @Test void requireOfAnotherServiceIsAlsoCaught() {
    assertEquals("R-020", scan(modified(PATIENT_CONTROLLER,
        "const d = require('../../../AI-service/src/controllers/ai.controller');")).get(0).ruleId());
  }

  @Test void importingYourOwnModelsAndLibrariesIsFine() {
    assertTrue(scan(modified(PATIENT_CONTROLLER,
        "import { User } from \"../models/user.model\";",
        "import express from \"express\";",
        "import { Router } from '../routes/patient.routes';")).isEmpty());
  }

  @Test void hardCodedServiceAddressIsAViolation() {
    List<Violation> v = scan(modified(PATIENT_CONTROLLER, "const url = \"http://localhost:5002/api/doctors\";"));
    assertEquals(1, v.size());
    assertEquals("R-021", v.get(0).ruleId());
  }

  @Test void commentsAndTestFilesAreIgnored() {
    assertTrue(scan(modified(PATIENT_CONTROLLER, "// import x from \"../../doctor-service/a\";")).isEmpty());
    assertTrue(scan(modified("microservices/patient-service/src/controllers/patient.controller.test.ts",
        "import x from \"../../../doctor-service/a\";")).isEmpty());
  }

  @Test void javaControllerImportingItsRepositoryIsHeldInTheJavaService() {
    List<Violation> v = scan(modified(TELE_CONTROLLER,
        "import com.healthmate.telemedicine.repository.ConsultationRepository;"));
    assertEquals(1, v.size());
    assertEquals("R-014", v.get(0).ruleId());
    assertEquals("critical", v.get(0).severity());
  }

  @Test void javaControllerImportingItsServiceIsFine() {
    assertTrue(scan(modified(TELE_CONTROLLER, "import com.healthmate.telemedicine.service.ConsultationService;")).isEmpty());
  }

  @Test void typeScriptControllerPlainDocsStyleChangeIsFine() {
    assertTrue(scan(modified(PATIENT_CONTROLLER, "export const ping = () => 'ok';")).isEmpty());
  }
}
