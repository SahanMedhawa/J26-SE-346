package akegp.users.profile.api;
import akegp.users.profile.domain.*;
import akegp.users.profile.repository.*;
import akegp.users.auth.service.OAuthIdentityService;
import akegp.users.auth.config.UsersSecurityConfig;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@RestController
@RequestMapping("/api/users")
public class ProfileController {
 private final JdbcUserRepository users;private final JdbcOAuthAccountRepository accounts;private final OAuthIdentityService identities;private final UsersSecurityConfig security;private final ClientRegistrationRepository providers;
 public ProfileController(JdbcUserRepository u,JdbcOAuthAccountRepository a,OAuthIdentityService i,UsersSecurityConfig s,ClientRegistrationRepository p){users=u;accounts=a;identities=i;security=s;providers=p;}
 static UUID id(Principal p){return UUID.fromString(p.getName());}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/providers") public List<String> providers(){return List.of("google","github").stream().filter(p->providers.findByRegistrationId(p)!=null).toList();}
 @GetMapping("/me") public User me(Principal p){return users.findById(id(p)).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED));}
 public record ProfileInput(@NotBlank @Size(max=255) String displayName,@Size(max=1000) String bio,@Size(max=2048) @Pattern(regexp="^(https?://.*)?$",message="Use an HTTP or HTTPS avatar URL") String avatarUrl){}
 @PutMapping("/me") public User update(Principal p,@Valid @RequestBody ProfileInput i){users.updateProfile(id(p),i.displayName(),i.bio(),i.avatarUrl());return me(p);}
 @GetMapping("/me/linked-accounts") public List<OAuthAccount> accounts(Principal p){return accounts.findByUserId(id(p));}
 @PostMapping("/me/link/{provider}") public Map<String,String> link(Principal p,HttpServletRequest request,@PathVariable("provider") String provider){if(providers.findByRegistrationId(provider)==null)throw new ResponseStatusException(BAD_REQUEST,"Provider is unavailable");request.getSession().setAttribute("linkUser",id(p));request.getSession().setAttribute("linkProvider",provider);request.getSession().setAttribute("linkExpiresAt",System.currentTimeMillis()+300000L);return Map.of("url","/oauth2/authorization/"+provider);}
 @DeleteMapping("/me/link/{provider}") public void unlink(Principal p,@PathVariable("provider") String provider){identities.unlink(id(p),provider);}
 @PostMapping("/logout") public void logout(HttpServletRequest q,HttpServletResponse s){var session=q.getSession(false);if(session!=null)session.invalidate();s.addHeader(HttpHeaders.SET_COOKIE,security.cookie("",0).toString());}
}
