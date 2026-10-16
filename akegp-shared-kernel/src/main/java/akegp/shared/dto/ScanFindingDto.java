package akegp.shared.dto;

/** One violation found by a scan. severity is text (for example critical | major | minor); tag is "new" or "existing". */
public record ScanFindingDto(String ruleId, String severity, String file, Integer line, String message, String tag) {}