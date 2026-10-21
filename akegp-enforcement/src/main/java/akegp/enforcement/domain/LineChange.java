package akegp.enforcement.domain;

/** One added or removed line. Added lines use the line number in the new file, removed lines in the old file. */
public record LineChange(int line, String text) {}
