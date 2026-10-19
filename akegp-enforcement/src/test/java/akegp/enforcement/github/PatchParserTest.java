package akegp.enforcement.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.domain.LineChange;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatchParserTest {
  @Test void numbersAddedLinesInTheNewFileAndRemovedLinesInTheOldFile() {
    String patch = "@@ -3,4 +3,5 @@ class A {\n context one\n-old import\n+new import\n+another new\n context two\n context three";
    PatchParser.Lines lines = PatchParser.parse(patch);
    assertEquals(List.of(new LineChange(4, "new import"), new LineChange(5, "another new")), lines.added());
    assertEquals(List.of(new LineChange(4, "old import")), lines.removed());
  }

  @Test void handlesSeveralHunks() {
    String patch = "@@ -1,2 +1,2 @@\n-a\n+b\n same\n@@ -10,1 +10,2 @@\n x\n+y";
    PatchParser.Lines lines = PatchParser.parse(patch);
    assertEquals(List.of(new LineChange(1, "b"), new LineChange(11, "y")), lines.added());
    assertEquals(List.of(new LineChange(1, "a")), lines.removed());
  }

  @Test void ignoresNoNewlineMarkerAndKeepsPlusSigns() {
    String patch = "@@ -1 +1 @@\n-old\n\\ No newline at end of file\n+++ counter\n\\ No newline at end of file";
    PatchParser.Lines lines = PatchParser.parse(patch);
    assertEquals(List.of(new LineChange(1, "++ counter")), lines.added());
  }

  @Test void missingOrEmptyPatchGivesNoLines() {
    assertTrue(PatchParser.parse(null).added().isEmpty());
    assertTrue(PatchParser.parse("").added().isEmpty());
  }
}
