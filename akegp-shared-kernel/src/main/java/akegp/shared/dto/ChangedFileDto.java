package akegp.shared.dto;

import java.util.List;

/** What happened to one file in a pull request. status: added | modified | deleted | renamed | copied. */
public record ChangedFileDto(
    String path,
    String status,
    String oldPath,
    int additions,
    int deletions,
    boolean binary,
    List<ChangedLineDto> addedLines,     // empty unless the pipeline sends lines
    List<ChangedLineDto> removedLines,
    String patch) {}                     // null unless the pipeline sends patches