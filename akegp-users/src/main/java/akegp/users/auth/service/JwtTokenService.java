package akegp.users.auth.service;
import akegp.users.profile.domain.User;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
@Configuration(proxyBeanMethods=false)
public class JwtTokenService {
 private final SecretKeySpec key; private final long expiration;
 public JwtTokenService(@Value("${akegp.jwt.secret}") String secret,@Value("${akegp.jwt.expiration-ms:86400000}") long expiry) {
   byte[] bytes=Base64.getDecoder().decode(secret);
   if(bytes.length<32) throw new IllegalArgumentException("AKEGP_JWT_SECRET must be Base64 encoding at least 32 random bytes");
   if(expiry<=0) throw new IllegalArgumentException("JWT expiration must be positive");
   key=new SecretKeySpec(bytes,"HmacSHA256");expiration=expiry;
 }
 @Bean public JwtEncoder jwtEncoder(){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
 @Bean public JwtDecoder jwtDecoder(){var d=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();d.setJwtValidator(JwtValidators.createDefaultWithIssuer("akegp"));return d;}
 public String issue(User user){var now=Instant.now();return jwtEncoder().encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("akegp").subject(user.id().toString()).issuedAt(now).expiresAt(now.plusMillis(expiration)).build())).getTokenValue();}
 public long lifetimeSeconds(){return expiration/1000;}
}
