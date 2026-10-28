package akegp.users.auth.service;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OAuthIdentityResolverTest {
  DefaultOidcUser google(String email, boolean verified, String domain) {
    var claims = new HashMap<String, Object>();
    claims.put("sub", "google-subject"); claims.put("email", email); claims.put("email_verified", verified);
    if (domain != null) claims.put("hd", domain);
    var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(300), claims);
    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("USER")), token);
  }
  DefaultOAuth2User github() {
    return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")), Map.of("id", 123, "email", "public-but-unverified@example.com"), "id");
  }
  @Test void gmailAndManagedGoogleDomainsCanAutoLink() {
    var resolver = new OAuthIdentityResolver();
    assertTrue(resolver.resolve("google", google("user@gmail.com", true, null), null).autoLinkAllowed());
    assertTrue(resolver.resolve("google", google("user@company.com", true, "company.com"), null).autoLinkAllowed());
  }
  @Test void externalGoogleAddressesDoNotAutoLinkEvenIfVerified() {
    var identity = new OAuthIdentityResolver().resolve("google", google("user@example.com", true, null), null);
    assertTrue(identity.emailVerified()); assertFalse(identity.autoLinkAllowed());
  }
  @Test void googleUsesIdTokenVerificationInsteadOfMergedUserInfo() {
    var original = google("user@gmail.com", false, null);
    var merged = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("USER")), original.getIdToken(),
        new OidcUserInfo(Map.of("sub", "google-subject", "email", "other@gmail.com", "email_verified", true)));
    var identity = new OAuthIdentityResolver().resolve("google", merged, null);
    assertFalse(identity.emailVerified()); assertFalse(identity.autoLinkAllowed());
    assertEquals("user@gmail.com", identity.email());
  }
  @Test void githubUsesOnlyThePrivateVerifiedPrimaryEmail() {
    var builder = RestClient.builder().baseUrl("https://api.github.com");
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.github.com/user/emails"))
        .andExpect(header("Authorization", "Bearer server-token"))
        .andRespond(withSuccess("[{\"email\":\"secondary@example.com\",\"primary\":false,\"verified\":true},{\"email\":\"private@gmail.com\",\"primary\":true,\"verified\":true}]", MediaType.APPLICATION_JSON));
    var identity = new OAuthIdentityResolver(builder.build()).resolve("github", github(), "server-token");
    assertEquals("private@gmail.com", identity.email()); assertTrue(identity.emailVerified()); assertTrue(identity.autoLinkAllowed());
    server.verify();
  }
  @Test void unverifiedGithubPrimaryDoesNotFallBackToPublicOrSecondaryEmail() {
    var builder = RestClient.builder().baseUrl("https://api.github.com");
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.github.com/user/emails"))
        .andRespond(withSuccess("[{\"email\":\"secondary@gmail.com\",\"primary\":false,\"verified\":true},{\"email\":\"primary@gmail.com\",\"primary\":true,\"verified\":false}]", MediaType.APPLICATION_JSON));
    var identity = new OAuthIdentityResolver(builder.build()).resolve("github", github(), "server-token");
    assertNull(identity.email()); assertFalse(identity.emailVerified()); assertFalse(identity.autoLinkAllowed());
    server.verify();
  }
}
