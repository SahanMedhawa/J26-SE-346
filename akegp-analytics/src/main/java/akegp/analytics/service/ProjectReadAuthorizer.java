package akegp.analytics.service;

import java.security.Principal;
import java.util.UUID;

/** Host-provided membership check. No identity is accepted from a query/header string. */
@FunctionalInterface
public interface ProjectReadAuthorizer {
  void requireRead(Principal principal, UUID projectId);
}
