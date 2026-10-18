package akegp.enforcement.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.Violation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockConformanceScannerTest {
  private static final String CONTROLLER = "account/src/main/java/com/shop/controller/AccountController.java";
  private static final String REPO_IMPORT = "import com.shop.repository.AccountRepository;";

  private final MockConformanceScanner scanner = new MockConformanceScanner();

  private static LineChange line(int number, String text) { return new LineChange(number, text); }

  /** A file change with the given added lines (counts follow the lines, as the pipeline sends them). */
  private static FileChange modified(String path, LineChange... added) {
    return new FileChange(path, "modified", null, added.length, 0, false, Arrays.asList(added), null, null);
  }

  private List<Violation> scan(FileChange... changes) {
    List<String> files = Arrays.stream(changes).map(FileChange::path).toList();
    return scanner.scan(new ScanRequest("https://github.com/o/r", "abc1234", "def5678", 1, "proj-1", files,
        Arrays.asList(changes))).violations();
  }

  @Test void commentOnlyControllerChangePasses() {
    assertTrue(scan(modified(CONTROLLER, line(40, "// just a comment"))).isEmpty());
  }

  @Test void controllerImportingARepositoryIsAViolationOnThatLine() {
    List<Violation> found = scan(modified(CONTROLLER, line(7, REPO_IMPORT)));
    assertEquals(1, found.size());
    assertEquals("R-014", found.get(0).ruleId());
    assertEquals("critical", found.get(0).severity());
    assertEquals(CONTROLLER, found.get(0).file());
    assertEquals(7, found.get(0).line());
    assertTrue(found.get(0).message().contains(REPO_IMPORT));
  }

  @Test void serviceImportingARepositoryIsFine() {
    String service = "account/src/main/java/com/shop/service/AccountService.java";
    assertTrue(scan(modified(service, line(5, REPO_IMPORT))).isEmpty());
  }

  @Test void controllerImportingAServiceIsFine() {
    assertTrue(scan(modified(CONTROLLER, line(5, "import com.shop.service.AccountService;"))).isEmpty());
  }

  @Test void filesOutsideJavaAreIgnored() {
    assertTrue(scan(modified("web/controller/accounts.js", line(1, REPO_IMPORT))).isEmpty());
  }

  @Test void onlyTheBadControllerInAMixedPrIsFlagged() {
    List<Violation> found = scan(
        modified("account/src/main/java/com/shop/service/AccountService.java", line(3, REPO_IMPORT)),
        modified(CONTROLLER, line(9, REPO_IMPORT)),
        modified("README.md", line(1, "# hello")));
    assertEquals(1, found.size());
    assertEquals(CONTROLLER, found.get(0).file());
  }

  // --- the pipeline did not send the added lines (INCLUDE_LINES off): flag the controller by its path ---

  @Test void noLinesSentFlagsAControllerByPath() {
    // FileChange turns a missing line list into an empty one; the counts show something was added.
    FileChange noLines = new FileChange(CONTROLLER, "modified", null, 1, 1, false, null, null, null);
    List<Violation> found = scan(noLines);
    assertEquals(1, found.size());
    assertEquals(0, found.get(0).line());
    assertTrue(found.get(0).message().contains("flagged by file path"));
  }

  @Test void synthesizedEntryWithoutAnyDetailFlagsAControllerByPath() {
    // What PipelineEventValidator builds when the event has no "changes" at all.
    FileChange bare = new FileChange(CONTROLLER, "modified", null, 0, 0, false, null, null, null);
    assertEquals(1, scan(bare).size());
  }

  @Test void noChangesAtAllFlagsAControllerByPath() {
    ScanRequest request = new ScanRequest("u", "abc1234", null, 1, "p", List.of(CONTROLLER), List.of());
    assertEquals(1, scanner.scan(request).violations().size());
  }

  @Test void nonControllerWithoutLinesIsNotFlagged() {
    FileChange noLines = new FileChange("src/Service.java", "modified", null, 1, 1, false, null, null, null);
    assertTrue(scan(noLines).isEmpty());
  }

  // --- changes that cannot add an import are never flagged ---

  @Test void deletedControllerIsFine() {
    FileChange deleted = new FileChange(CONTROLLER, "deleted", null, 0, 30, false, null, List.of(line(1, REPO_IMPORT)), null);
    assertTrue(scan(deleted).isEmpty());
  }

  @Test void pureDeletionEditIsFine() {
    FileChange onlyRemoved = new FileChange(CONTROLLER, "modified", null, 0, 3, false, null, null, null);
    assertTrue(scan(onlyRemoved).isEmpty());
  }

  @Test void pureRenameIsFine() {
    FileChange renamed = new FileChange(CONTROLLER, "renamed", "old/AccountController.java", 0, 0, false, null, null, null);
    assertTrue(scan(renamed).isEmpty());
  }

  @Test void lineDetailMissingKnowsTheCases() {
    assertTrue(MockConformanceScanner.lineDetailMissing(new FileChange("A.java", "added", null, 5, 0, false, null, null, null)));
    assertFalse(MockConformanceScanner.lineDetailMissing(new FileChange("A.java", "added", null, 1, 0, false, List.of(line(1, "x")), null, null)));
    assertFalse(MockConformanceScanner.lineDetailMissing(new FileChange("A.png", "modified", null, 0, 0, true, null, null, null)));
  }

  // --- the wider rule set ---

  private static final String SERVICE = "account/src/main/java/com/shop/service/AccountService.java";
  private static final String REPOSITORY = "account/src/main/java/com/shop/repository/AccountRepository.java";
  private static final String MODEL = "account/src/main/java/com/shop/model/Account.java";

  private static void assertOneViolation(List<Violation> found, String ruleId, String file, int line) {
    assertEquals(1, found.size());
    assertEquals(ruleId, found.get(0).ruleId());
    assertEquals(file, found.get(0).file());
    assertEquals(line, found.get(0).line());
  }

  @Test void controllerUsingAPersistenceApiDirectlyIsCritical() {
    List<Violation> found = scan(modified(CONTROLLER, line(4, "import jakarta.persistence.EntityManager;")));
    assertOneViolation(found, "R-015", CONTROLLER, 4);
    assertEquals("critical", found.get(0).severity());
    assertOneViolation(scan(modified(CONTROLLER, line(6, "import org.springframework.jdbc.core.JdbcTemplate;"))),
        "R-015", CONTROLLER, 6);
  }

  @Test void serviceImportingAControllerIsFlagged() {
    List<Violation> found = scan(modified(SERVICE, line(8, "import com.shop.controller.AccountController;")));
    assertOneViolation(found, "R-016", SERVICE, 8);
    assertEquals("high", found.get(0).severity());
  }

  @Test void repositoryImportingAServiceOrControllerIsFlagged() {
    assertOneViolation(scan(modified(REPOSITORY, line(3, "import com.shop.service.AccountService;"))), "R-017", REPOSITORY, 3);
    assertOneViolation(scan(modified(REPOSITORY, line(3, "import com.shop.controller.AccountController;"))), "R-017", REPOSITORY, 3);
  }

  @Test void domainClassImportingAnApplicationLayerIsFlagged() {
    List<Violation> found = scan(modified(MODEL, line(2, REPO_IMPORT)));
    assertOneViolation(found, "R-018", MODEL, 2);
    assertEquals("medium", found.get(0).severity());
  }

  @Test void serviceUsingWebTypesIsFlagged() {
    assertOneViolation(scan(modified(SERVICE, line(5, "import org.springframework.http.ResponseEntity;"))), "R-019", SERVICE, 5);
    assertOneViolation(scan(modified(SERVICE, line(5, "import jakarta.servlet.http.HttpServletRequest;"))), "R-019", SERVICE, 5);
  }

  @Test void allowedDependenciesAreNotFlagged() {
    assertTrue(scan(modified(CONTROLLER, line(3, "import com.shop.service.AccountService;"),
        line(4, "import com.shop.model.Account;"), line(5, "import org.springframework.web.bind.annotation.GetMapping;"))).isEmpty());
    assertTrue(scan(modified(SERVICE, line(3, REPO_IMPORT), line(4, "import com.shop.model.Account;"))).isEmpty());
    assertTrue(scan(modified(REPOSITORY, line(3, "import com.shop.model.Account;"),
        line(4, "import org.springframework.data.jpa.repository.JpaRepository;"))).isEmpty());
    assertTrue(scan(modified(MODEL, line(3, "import jakarta.persistence.Entity;"))).isEmpty());
  }

  @Test void testSourcesAreNeverChecked() {
    String controllerTest = "account/src/test/java/com/shop/controller/AccountControllerTest.java";
    assertTrue(scan(modified(controllerTest, line(3, REPO_IMPORT))).isEmpty());
    assertTrue(scan(new FileChange(controllerTest, "modified", null, 1, 1, false, null, null, null)).isEmpty());
  }

  @Test void anUnreadableControllerIsFlaggedOnceAndNotByTheOtherRules() {
    FileChange noLines = new FileChange(CONTROLLER, "modified", null, 3, 0, false, null, null, null);
    List<Violation> found = scan(noLines);
    assertEquals(1, found.size());
    assertEquals("R-014", found.get(0).ruleId());
  }

  @Test void unreadableNonControllerFilesAreNotFlagged() {
    for (String path : List.of(SERVICE, REPOSITORY, MODEL, "src/Service.java")) {
      assertTrue(scan(new FileChange(path, "modified", null, 3, 0, false, null, null, null)).isEmpty());
    }
  }

  @Test void oneLineBreakingTwoRulesIsReportedForBoth() {
    // A controller that imports a JPA repository class and an EntityManager on separate lines.
    List<Violation> found = scan(modified(CONTROLLER, line(3, REPO_IMPORT), line(4, "import jakarta.persistence.EntityManager;")));
    assertEquals(2, found.size());
  }

  @Test void layeredRuleIdsAreUnique() {
    assertEquals(RuleCatalog.layered().size(), RuleCatalog.layered().stream().map(ArchitectureRule::ruleId).distinct().toList().size());
  }

  // --- microservice rules ---

  private static final String ACCOUNT = "account-management/src/main/java/com/savindu/accountManagement/";
  private static final String ACCOUNT_SERVICE = ACCOUNT + "service/AccountService.java";

  @Test void importingAnotherServicesCodeIsFlagged() {
    List<Violation> found = scan(modified(ACCOUNT_SERVICE, line(6, "import com.savindu.cardManagement.dto.CardDto;")));
    assertOneViolation(found, "R-020", ACCOUNT_SERVICE, 6);
    assertEquals("high", found.get(0).severity());
    assertOneViolation(scan(modified(ACCOUNT_SERVICE, line(7, "import com.savindu.loanManagement.service.LoanService;"))),
        "R-020", ACCOUNT_SERVICE, 7);
  }

  @Test void importingOwnOrSharedCodeIsFine() {
    assertTrue(scan(modified(ACCOUNT_SERVICE, line(3, "import com.savindu.accountManagement.dto.AccountDto;"),
        line(4, "import com.savindu.libCommon.dto.ResponseDto;"))).isEmpty());
  }

  @Test void eachServiceIsCheckedAgainstTheOthers() {
    String cardController = "card-management/src/main/java/com/savindu/cardManagement/controller/CardController.java";
    assertOneViolation(scan(modified(cardController, line(5, "import com.savindu.accountManagement.dto.AccountDto;"))),
        "R-020", cardController, 5);
  }

  @Test void aFileOutsideTheServicesIsNotCheckedForServiceBoundaries() {
    String shared = "lib-common/src/main/java/com/savindu/libCommon/Util.java";
    assertTrue(scan(modified(shared, line(2, "import com.savindu.cardManagement.dto.CardDto;"))).isEmpty());
  }

  @Test void aHardCodedServiceAddressIsFlagged() {
    List<Violation> found = scan(modified(ACCOUNT_SERVICE,
        line(21, "    String url = \"http://localhost:8090/api/cards\";")));
    assertOneViolation(found, "R-021", ACCOUNT_SERVICE, 21);
    assertEquals("medium", found.get(0).severity());
  }

  @Test void configuredAddressesAndCommentsAreNotFlagged() {
    assertTrue(scan(modified(ACCOUNT_SERVICE,
        line(1, "    @Value(\"${cards.url}\") String cardsUrl;"),
        line(2, "    // old address: \"http://localhost:8090\""),
        line(3, "     * see \"http://localhost:8090/docs\""))).isEmpty());
  }

  // --- choosing the rules by the architecture style of the project ---

  private List<Violation> scanAs(Set<String> styles, FileChange... changes) {
    List<String> files = Arrays.stream(changes).map(FileChange::path).toList();
    MockConformanceScanner byStyle = new MockConformanceScanner(RuleCatalog.all(), projectId -> styles);
    return byStyle.scan(new ScanRequest("u", "abc1234", null, 1, "proj-1", files, Arrays.asList(changes))).violations();
  }

  @Test void aLayeredProjectOnlyGetsTheLayerRules() {
    Set<String> layered = Set.of(RuleCatalog.LAYERED);
    assertEquals(1, scanAs(layered, modified(CONTROLLER, line(7, REPO_IMPORT))).size());
    assertTrue(scanAs(layered, modified(ACCOUNT_SERVICE, line(6, "import com.savindu.cardManagement.dto.CardDto;"))).isEmpty());
  }

  @Test void aMicroservicesProjectOnlyGetsTheServiceRules() {
    Set<String> microservices = Set.of(RuleCatalog.MICROSERVICES);
    assertTrue(scanAs(microservices, modified(CONTROLLER, line(7, REPO_IMPORT))).isEmpty());
    assertEquals(1, scanAs(microservices, modified(ACCOUNT_SERVICE, line(6, "import com.savindu.cardManagement.dto.CardDto;"))).size());
  }

  @Test void aProjectWithBothStylesGetsBothRuleSets() {
    Set<String> both = Set.of(RuleCatalog.LAYERED, RuleCatalog.MICROSERVICES);
    assertEquals(2, scanAs(both, modified(ACCOUNT + "controller/AccountController.java",
        line(3, REPO_IMPORT), line(4, "import com.savindu.cardManagement.dto.CardDto;"))).size());
  }

  @Test void anUnknownStyleSetFallsBackToBothStyles() {
    assertEquals(1, scanAs(Set.of(), modified(CONTROLLER, line(7, REPO_IMPORT))).size());
  }

  @Test void theDefaultScannerAppliesBothStyles() {
    assertEquals(2, scan(modified(ACCOUNT + "controller/AccountController.java",
        line(3, REPO_IMPORT), line(4, "import com.savindu.cardManagement.dto.CardDto;"))).size());
  }
}