package akegp.shared.dto;

/** One added or removed line of a changed file (line number in the new / old version of the file). */
public record ChangedLineDto(int line, String text) {}