package akegp.rules.api;

import java.util.List;
import java.util.UUID;

public record RepositoryFilesResponse(
    UUID projectId, String repositoryUrl, String branch, List<String> files) {}
