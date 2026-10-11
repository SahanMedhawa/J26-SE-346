package akegp;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
class ModularMonolithArchitectureTest {
  private final JavaClasses classes = new ClassFileImporter().importPackages("akegp");
  @Test void modulesDoNotDependOnEachOther() {
    noClasses().that().resideInAnyPackage("akegp.rules..").should().dependOnClassesThat().resideInAnyPackage("akegp.conformance..", "akegp.enforcement..", "akegp.analytics..", "akegp.app..").check(classes);
    noClasses().that().resideInAnyPackage("akegp.conformance..").should().dependOnClassesThat().resideInAnyPackage("akegp.rules..", "akegp.enforcement..", "akegp.analytics..", "akegp.app..").check(classes);
    noClasses().that().resideInAnyPackage("akegp.enforcement..").should().dependOnClassesThat().resideInAnyPackage("akegp.rules..", "akegp.conformance..", "akegp.analytics..", "akegp.app..").check(classes);
    noClasses().that().resideInAnyPackage("akegp.analytics..").should().dependOnClassesThat().resideInAnyPackage("akegp.rules..", "akegp.conformance..", "akegp.enforcement..", "akegp.app..").check(classes);
  }
  @Test void sharedKernelDoesNotDependOnModulesOrFrameworks() {
    noClasses().that().resideInAnyPackage("akegp.shared..").should().dependOnClassesThat().resideInAnyPackage("akegp.rules..", "akegp.conformance..", "akegp.enforcement..", "akegp.analytics..", "akegp.app..", "org.springframework..", "jakarta.persistence..").check(classes);
    assertTrue(classes.stream().filter(c -> c.getPackageName().startsWith("akegp.shared")).allMatch(c -> c.getAnnotations().isEmpty()));
  }
}
