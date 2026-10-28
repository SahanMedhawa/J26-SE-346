package akegp.users.auth.config;
import akegp.users.auth.service.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
@Configuration(proxyBeanMethods=false)
public class UsersSecurityConfig {
 public static final String COOKIE="AKEGP_SESSION";
 private final JwtTokenService tokens;private final OAuthIdentityService identities;private final boolean secure;private final String frontend;
 public UsersSecurityConfig(JwtTokenService t,OAuthIdentityService i,@Value("${akegp.auth.secure-cookie:true}") boolean s,@Value("${akegp.auth.frontend-url:http://localhost:5173}") String f){tokens=t;identities=i;secure=s;frontend=f;}
 public ResponseCookie cookie(String token,long age){return ResponseCookie.from(COOKIE,token).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build();}
 @Bean SecurityFilterChain security(HttpSecurity http,OAuth2AuthorizedClientService clients,OAuthIdentityResolver identityResolver) throws Exception {
   var resolver=new DefaultBearerTokenResolver();
   org.springframework.security.web.util.matcher.RequestMatcher publisher=q->q.getMethod().equals("POST") && q.getRequestURI().matches("/api/analytics/projects/[^/]+/snapshots");
   http.securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()));
   // Resource Server normally exempts bearer requests from CSRF. Cookie tokens must not
   // inherit that exemption; install the final matcher on the constructed CSRF filter.
   http.csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
     .withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<CsrfFilter>() {
       @Override public <O extends CsrfFilter> O postProcess(O filter) {
         filter.setRequireCsrfProtectionMatcher(q->CsrfFilter.DEFAULT_CSRF_MATCHER.matches(q)
           && !publisher.matches(q) && !q.getRequestURI().equals("/api/enforcement/pipeline-events"));
         return filter;
       }
     }));
   http.authorizeHttpRequests(a->a.requestMatchers(publisher).permitAll().requestMatchers("/api/users/csrf","/api/users/providers","/oauth2/**","/login/**","/error","/api/*/health","/api/enforcement/pipeline-events").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll());
   http.exceptionHandling(e->e.authenticationEntryPoint((q,s,x)->{s.setStatus(401);s.setContentType("application/json");s.getWriter().write("{\"status\":401,\"message\":\"Sign in required\"}");}));
   http.oauth2ResourceServer(o->o.bearerTokenResolver(request->{
     String bearer=resolver.resolve(request);if(bearer!=null)return bearer;
     if(request.getCookies()!=null)for(var c:request.getCookies())if(c.getName().equals(COOKIE))return c.getValue();return null;
   }).jwt(j->{}));
   http.oauth2Login(o->o.successHandler((request,response,authentication)->{
     try {
       var auth=(OAuth2AuthenticationToken)authentication;String provider=auth.getAuthorizedClientRegistrationId();
       String accessToken=null;
       if(provider.equals("github")) {
         var client=clients.loadAuthorizedClient(provider,auth.getName());
         if(client!=null)accessToken=client.getAccessToken().getTokenValue();
       }
       var identity=identityResolver.resolve(provider,auth.getPrincipal(),accessToken);
       var session=request.getSession(false);UUID link=session==null?null:(UUID)session.getAttribute("linkUser");
       if(link!=null && (!provider.equals(session.getAttribute("linkProvider"))
           || !(session.getAttribute("linkExpiresAt") instanceof Long expires) || expires<System.currentTimeMillis()))
         throw new OAuthSignInException(org.springframework.http.HttpStatus.BAD_REQUEST,"link_expired","Start a new provider link from Profile");
       var user=identities.login(identity,link);
       if(session!=null)session.invalidate();
       response.addHeader(HttpHeaders.SET_COOKIE,cookie(tokens.issue(user),tokens.lifetimeSeconds()).toString());
       response.sendRedirect(frontend+(link==null?"/auth/callback":"/profile?linked="+provider));
     } catch(Exception ex) {
       var session=request.getSession(false);boolean linking=session!=null&&session.getAttribute("linkUser")!=null;if(session!=null)session.invalidate();
       String error=ex instanceof OAuthSignInException failure?failure.code():"oauth";
       response.sendRedirect(frontend+(linking?"/profile?linkError=":"/login?error=")+error);
     } finally {
       var auth=(OAuth2AuthenticationToken)authentication;
       clients.removeAuthorizedClient(auth.getAuthorizedClientRegistrationId(),auth.getName());
     }
   }).failureHandler((q,s,e)->{var session=q.getSession(false);boolean linking=session!=null&&session.getAttribute("linkUser")!=null;if(session!=null)session.invalidate();s.sendRedirect(frontend+(linking?"/profile?linkError=oauth":"/login?error=oauth"));}));
   http.logout(l->l.disable());
   return http.build();
 }
}
