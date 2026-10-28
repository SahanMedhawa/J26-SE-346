package akegp.users;

import akegp.users.auth.service.*;
import akegp.users.profile.domain.*;
import akegp.users.profile.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthIdentityServiceTest {
  final JdbcUserRepository users = mock(JdbcUserRepository.class);
  final JdbcOAuthAccountRepository accounts = mock(JdbcOAuthAccountRepository.class);
  final OAuthIdentityService service = new OAuthIdentityService(users, accounts);
  final UUID id = UUID.randomUUID();
  final User user = new User(id, "Name", "user@gmail.com", null, null, Instant.EPOCH, Instant.EPOCH);

  @BeforeEach void defaults() {
    when(users.createIfAbsent(any())).thenAnswer(call -> call.getArgument(0));
    when(accounts.insertIfAbsent(any())).thenAnswer(call -> Optional.of(call.getArgument(0)));
  }
  OAuthIdentity identity(String provider, String email, boolean verified, boolean trusted) {
    return new OAuthIdentity(provider, "123", email, verified, trusted, "Name", null);
  }
  OAuthAccount linked(String provider, String accountId, boolean trusted) {
    return new OAuthAccount(UUID.randomUUID(), id, provider, accountId, user.email(), Instant.EPOCH, true, trusted);
  }
  void existingUser(String provider) {
    when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
    when(accounts.findByUserId(id)).thenReturn(List.of(linked(provider, "original", true)));
  }

  @Test void verifiedGoogleEmailLinksToExistingGitHubUserWithoutChangingId() {
    existingUser("github");
    assertEquals(user, service.login(identity("google", user.email(), true, true), null));
    verify(accounts).insertIfAbsent(argThat(a -> a.userId().equals(id) && a.provider().equals("google") && a.emailVerified()));
    verify(users, never()).createIfAbsent(any());
  }
  @Test void verifiedGitHubEmailLinksToExistingGoogleUser() {
    existingUser("google");
    assertEquals(id, service.login(identity("github", user.email(), true, true), null).id());
    verify(accounts).insertIfAbsent(argThat(a -> a.userId().equals(id) && a.provider().equals("github")));
  }
  @Test void normalizedEmailMatchesExistingUser() {
    existingUser("github");
    assertEquals(id, service.login(identity("google", " USER@GMAIL.COM ", true, true), null).id());
    verify(users).findByEmail("user@gmail.com");
  }
  @Test void unverifiedEmailsAreRejectedBeforeAnyDatabaseChange() {
    for (String provider : List.of("google", "github"))
      assertEquals("email_unverified", assertThrows(OAuthSignInException.class,
          () -> service.login(identity(provider, user.email(), false, true), null)).code());
    verifyNoInteractions(users, accounts);
  }
  @Test void missingEmailIsRejected() {
    assertThrows(OAuthSignInException.class, () -> service.login(identity("github", null, false, false), null));
    verifyNoInteractions(users, accounts);
  }
  @Test void externalGoogleEmailRequiresExplicitLinkWhenUserAlreadyExists() {
    existingUser("github");
    assertEquals("link_required", assertThrows(OAuthSignInException.class,
        () -> service.login(identity("google", user.email(), true, false), null)).code());
    verify(accounts, never()).insertIfAbsent(any());
  }
  @Test void existingUntrustedEmailCannotEnableAccountPreHijacking() {
    when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
    when(accounts.findByUserId(id)).thenReturn(List.of(linked("google", "original", false)));
    assertEquals("link_required", assertThrows(OAuthSignInException.class,
        () -> service.login(identity("github", user.email(), true, true), null)).code());
    verify(accounts, never()).insertIfAbsent(any());
  }
  @Test void newVerifiedEmailCreatesOneUserAndOneProviderAccount() {
    var created = service.login(identity("github", "new@example.com", true, true), null);
    assertNotEquals(id, created.id());
    assertEquals("new@example.com", created.email());
    verify(users).createIfAbsent(any());
    verify(accounts).insertIfAbsent(argThat(a -> a.userId().equals(created.id())));
  }
  @Test void concurrentFirstLoginUsesTheExistingCanonicalUser() {
    when(users.createIfAbsent(any())).thenReturn(user);
    when(accounts.findByUserId(id)).thenReturn(List.of(linked("github", "original", true)));
    assertEquals(id, service.login(identity("google", user.email(), true, true), null).id());
    verify(accounts).insertIfAbsent(argThat(a -> a.userId().equals(id)));
  }
  @Test void returningProviderUsesStableIdEvenAfterEmailChanges() {
    var account = linked("github", "123", true);
    when(accounts.findByProviderAndAccountId("github", "123")).thenReturn(Optional.of(account));
    when(users.findById(id)).thenReturn(Optional.of(user));
    assertEquals(user, service.login(identity("github", "new@example.com", true, true), null));
    verify(users, never()).findByEmail(any());
    verify(accounts).updateEmailEvidence(account.id(), "new@example.com", true, true);
    verify(accounts, never()).insertIfAbsent(any());
  }
  @Test void cannotLinkProviderOwnedByAnotherUser() {
    var other = new OAuthAccount(UUID.randomUUID(), UUID.randomUUID(), "github", "123", user.email(), Instant.EPOCH, true, true);
    when(accounts.findByProviderAndAccountId("github", "123")).thenReturn(Optional.of(other));
    assertThrows(OAuthSignInException.class, () -> service.login(identity("github", user.email(), true, true), id));
  }
  @Test void doesNotReplaceAnExistingDifferentIdentityForTheSameProvider() {
    existingUser("github");
    assertEquals("account_conflict", assertThrows(OAuthSignInException.class,
        () -> service.login(identity("github", user.email(), true, true), null)).code());
    verify(accounts, never()).insertIfAbsent(any());
  }
  @Test void explicitlyLinkingFromProfileAllowsDifferentVerifiedEmail() {
    when(users.findById(id)).thenReturn(Optional.of(user));
    assertEquals(user, service.login(identity("google", "other@example.com", true, false), id));
    verify(accounts).insertIfAbsent(argThat(a -> a.userId().equals(id) && a.providerEmail().equals("other@example.com")));
    verify(users, never()).createIfAbsent(any());
  }
  @Test void conflictingConcurrentProviderInsertDoesNotTransferOwnership() {
    existingUser("google");
    doReturn(Optional.of(new OAuthAccount(UUID.randomUUID(), UUID.randomUUID(), "github", "123", user.email(), Instant.EPOCH, true, true))).when(accounts).insertIfAbsent(any());
    assertThrows(OAuthSignInException.class, () -> service.login(identity("github", user.email(), true, true), null));
  }
  @Test void lastLoginProviderCannotBeUnlinked() {
    when(accounts.countByUserId(id)).thenReturn(1L);
    assertThrows(ResponseStatusException.class, () -> service.unlink(id, "google"));
    verify(users).lock(id);
    verify(accounts, never()).deleteByUserIdAndProvider(any(), any());
  }
}
