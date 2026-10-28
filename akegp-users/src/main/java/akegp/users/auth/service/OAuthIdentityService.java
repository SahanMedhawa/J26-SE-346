package akegp.users.auth.service;

import akegp.users.profile.domain.OAuthAccount;
import akegp.users.profile.domain.User;
import akegp.users.profile.repository.JdbcOAuthAccountRepository;
import akegp.users.profile.repository.JdbcUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class OAuthIdentityService {
  private final JdbcUserRepository users;
  private final JdbcOAuthAccountRepository accounts;

  public OAuthIdentityService(JdbcUserRepository users, JdbcOAuthAccountRepository accounts) {
    this.users = users;
    this.accounts = accounts;
  }

  @Transactional("usersTransactionManager")
  public User login(OAuthIdentity identity, UUID linkTo) {
    if (!List.of("google", "github").contains(identity.provider())
        || identity.accountId() == null || identity.accountId().isBlank())
      throw new OAuthSignInException(BAD_REQUEST, "oauth", "Provider identity is incomplete");
    if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank())
      throw new OAuthSignInException(BAD_REQUEST, "email_unverified", "A verified email address is required");

    String email = identity.email().strip().toLowerCase(Locale.ROOT);
    var existing = accounts.findByProviderAndAccountId(identity.provider(), identity.accountId());
    if (existing.isPresent()) {
      if (linkTo != null && !linkTo.equals(existing.get().userId())) throw conflict();
      users.lock(existing.get().userId());
      accounts.updateEmailEvidence(existing.get().id(), email, true, identity.autoLinkAllowed());
      // Provider IDs remain authoritative even when the provider's email later changes.
      return users.findById(existing.get().userId()).orElseThrow();
    }

    var now = Instant.now();
    User user;
    boolean joiningExisting = false;
    if (linkTo != null) {
      user = users.findById(linkTo).orElseThrow();
    } else {
      var matching = users.findByEmail(email);
      if (matching.isPresent()) {
        user = matching.get();
        joiningExisting = true;
      } else {
        UUID candidateId = UUID.randomUUID();
        String name = identity.displayName() == null || identity.displayName().isBlank() ? email : identity.displayName();
        // The normalized email index and ON CONFLICT serialize concurrent first logins without duplicate users.
        user = users.createIfAbsent(new User(candidateId, name, email, identity.avatarUrl(), null, now, now));
        joiningExisting = !user.id().equals(candidateId);
      }
    }

    // Linking and unlinking share the same row lock so provider-count and uniqueness checks stay consistent.
    users.lock(user.id());
    var linked = accounts.findByUserId(user.id());
    if (joiningExisting && (!identity.autoLinkAllowed() || linked.stream().noneMatch(account ->
        account.emailVerified() && account.emailAuthoritative() && email.equalsIgnoreCase(account.providerEmail()))))
      throw linkRequired();
    var providerAccount = linked.stream()
        .filter(account -> account.provider().equals(identity.provider())).findFirst();
    if (providerAccount.isPresent()) {
      if (!providerAccount.get().providerAccountId().equals(identity.accountId())) throw conflict();
      return user;
    }
    var saved = accounts.insertIfAbsent(new OAuthAccount(UUID.randomUUID(), user.id(), identity.provider(),
        identity.accountId(), email, now, true, identity.autoLinkAllowed())).orElseThrow(OAuthIdentityService::conflict);
    if (!saved.userId().equals(user.id())) throw conflict();
    return user;
  }

  @Transactional("usersTransactionManager")
  public void unlink(UUID id, String provider) {
    users.lock(id);
    if (accounts.countByUserId(id) <= 1)
      throw new ResponseStatusException(CONFLICT, "Keep at least one linked sign-in provider");
    accounts.deleteByUserIdAndProvider(id, provider);
  }

  private static OAuthSignInException conflict() {
    return new OAuthSignInException(CONFLICT, "account_conflict", "This provider is already linked to a different account");
  }
  private static OAuthSignInException linkRequired() {
    return new OAuthSignInException(CONFLICT, "link_required", "Sign in with the original provider, then link this provider from Profile");
  }
}
