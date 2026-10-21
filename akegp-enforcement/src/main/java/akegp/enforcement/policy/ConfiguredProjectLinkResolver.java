package akegp.enforcement.policy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Repo-to-project table from configuration. Later this can ask Component 1, whose project form will also hold the repo URL.
 */
public class ConfiguredProjectLinkResolver implements ProjectLinkResolver {
  private final Map<String, String> table = new HashMap<>();
  private final String defaultProjectId;

  public ConfiguredProjectLinkResolver(Map<String, String> repoToProject, String defaultProjectId) {
    if (repoToProject != null) repoToProject.forEach((repo, id) -> table.put(repo.toLowerCase(Locale.ROOT), id));
    this.defaultProjectId = defaultProjectId == null || defaultProjectId.isBlank() ? null : defaultProjectId;
  }

  @Override
  public Optional<String> projectIdFor(String repo) {
    String id = table.get(repo.toLowerCase(Locale.ROOT));
    return Optional.ofNullable(id != null ? id : defaultProjectId);
  }
}
