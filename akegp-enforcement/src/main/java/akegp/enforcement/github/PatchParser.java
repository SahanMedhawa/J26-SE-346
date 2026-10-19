package akegp.enforcement.github;

import akegp.enforcement.domain.LineChange;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns the unified diff text GitHub gives for one file into added and removed lines with their line numbers. */
final class PatchParser {
  private PatchParser() {}

  record Lines(List<LineChange> added, List<LineChange> removed) {}

  private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

  /** Added lines use the line number in the new file, removed lines the number in the old file. */
  static Lines parse(String patch) {
    List<LineChange> added = new ArrayList<>();
    List<LineChange> removed = new ArrayList<>();
    if (patch == null) return new Lines(added, removed);
    int oldLine = 0;
    int newLine = 0;
    boolean inHunk = false;
    for (String line : patch.split("\r?\n", -1)) {
      Matcher h = HUNK.matcher(line);
      if (h.find()) {
        oldLine = Integer.parseInt(h.group(1));
        newLine = Integer.parseInt(h.group(2));
        inHunk = true;
        continue;
      }
      if (!inHunk || line.startsWith("\\")) continue;           // "\ No newline at end of file"
      if (line.startsWith("+")) added.add(new LineChange(newLine++, line.substring(1)));
      else if (line.startsWith("-")) removed.add(new LineChange(oldLine++, line.substring(1)));
      else { oldLine++; newLine++; }                             // context line
    }
    return new Lines(added, removed);
  }
}
