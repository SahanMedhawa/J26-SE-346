package akegp.enforcement.policy;

import java.util.Optional;

/** Finds which project (and therefore which READY rules) a GitHub repository belongs to. */
public interface ProjectLinkResolver {
  /** @param repo "owner/name"; matched case-insensitively */
  Optional<String> projectIdFor(String repo);

  /** Canonical registered repository URL, used when older enforcement records did not store one. */
  default Optional<String> repositoryUrlFor(String repo) { return Optional.empty(); }
}
