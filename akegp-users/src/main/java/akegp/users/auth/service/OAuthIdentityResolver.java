package akegp.users.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OAuthIdentityResolver {
  private final RestClient github;

  public OAuthIdentityResolver() { this(RestClient.create("https://api.github.com")); }
  OAuthIdentityResolver(RestClient github) { this.github = github; }

  public record GitHubEmail(String email, boolean primary, boolean verified) {}

  public OAuthIdentity resolve(String provider, OAuth2User principal, String accessToken) {
    if (provider.equals("google")) {
      if (!(principal instanceof OidcUser oidc)) throw invalid("Google requires a validated OpenID identity");
      // Read identity and verification evidence from the validated ID token, not merged user-info attributes.
      Map<String, Object> claims = oidc.getIdToken().getClaims();
      String email = string(claims.get("email"));
      boolean verified = Boolean.TRUE.equals(claims.get("email_verified"));
      String domain = string(claims.get("hd"));
      String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
      // Google is not authoritative for arbitrary third-party email addresses, even when email_verified is true.
      boolean authoritative = normalized.endsWith("@gmail.com")
          || (domain != null && !domain.isBlank() && normalized.endsWith("@" + domain.toLowerCase(Locale.ROOT)));
      return new OAuthIdentity(provider, oidc.getIdToken().getSubject(), email, verified,
          verified && authoritative, string(claims.get("name")), string(claims.get("picture")));
    }
    if (provider.equals("github")) {
      String accountId = string(principal.getAttributes().get("id"));
      if (accountId == null || !accountId.matches("[1-9][0-9]*") || accessToken == null || accessToken.isBlank())
        throw invalid("GitHub identity is incomplete");
      // The public profile email is not verification evidence. Query the authenticated private emails API.
      var emails = github.get().uri("/user/emails")
          .headers(headers -> { headers.setBearerAuth(accessToken); headers.set("Accept", "application/vnd.github+json"); })
          .retrieve().body(new ParameterizedTypeReference<List<GitHubEmail>>() {});
      var email = emails == null ? null : emails.stream()
          .filter(row -> row.primary() && row.verified() && row.email() != null && !row.email().isBlank())
          .map(GitHubEmail::email).findFirst().orElse(null);
      return new OAuthIdentity(provider, accountId, email, email != null, email != null,
          string(principal.getAttributes().get("name")), string(principal.getAttributes().get("avatar_url")));
    }
    throw invalid("Unsupported sign-in provider");
  }

  private static String string(Object value) { return value == null ? null : value.toString(); }
  private static OAuthSignInException invalid(String reason) {
    return new OAuthSignInException(HttpStatus.BAD_REQUEST, "oauth", reason);
  }
}
