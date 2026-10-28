package akegp.users;
import akegp.users.auth.config.*;
import akegp.users.auth.service.*;
import akegp.users.profile.api.ProfileController;
import akegp.users.profile.domain.User;
import akegp.users.profile.repository.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CookieSecurityTest {
 @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
 @Import({UsersSecurityConfig.class,OAuthProviders.class,JwtTokenService.class,OAuthIdentityResolver.class,ProfileController.class,TestApi.class})
 static class Config {
   @Bean JdbcUserRepository users(){return mock(JdbcUserRepository.class);}
   @Bean JdbcOAuthAccountRepository accounts(){return mock(JdbcOAuthAccountRepository.class);}
   @Bean OAuthIdentityService identities(){return mock(OAuthIdentityService.class);}
   @Bean OAuth2AuthorizedClientService clients(ClientRegistrationRepository r){return new InMemoryOAuth2AuthorizedClientService(r);}
 }
 @RestController static class TestApi {@PostMapping("/api/test/write") Map<String,String> write(Principal p){return Map.of("user",p.getName());}}
 @Test void jwtCookieAndCsrfAreBothRequiredForMutation(){
   new WebApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues("akegp.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","akegp.auth.secure-cookie=false").run(context->{
    assertNull(context.getStartupFailure());
    var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users/providers")).andExpect(status().isOk()).andExpect(content().json("[]"));
    UUID id=UUID.randomUUID();var user=new User(id,"Test","test@example.com",null,null,Instant.now(),Instant.now());
    when(context.getBean(JdbcUserRepository.class).findById(id)).thenReturn(Optional.of(user));
    var token=context.getBean(JwtTokenService.class).issue(user);var cookie=new Cookie(UsersSecurityConfig.COOKIE,token);
    mvc.perform(get("/api/users/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    mvc.perform(post("/api/test/write").cookie(cookie)).andExpect(status().isForbidden());
    var csrf=mvc.perform(get("/api/users/csrf")).andExpect(status().isOk()).andReturn();
    String csrfToken=JsonPath.read(csrf.getResponse().getContentAsString(),"$.token");String header=JsonPath.read(csrf.getResponse().getContentAsString(),"$.headerName");
    mvc.perform(post("/api/test/write").cookie(cookie).session((MockHttpSession)csrf.getRequest().getSession()).header(header,csrfToken)).andExpect(status().isOk()).andExpect(jsonPath("$.user").value(id.toString()));
    mvc.perform(get("/api/users/me").cookie(new Cookie(UsersSecurityConfig.COOKIE,token+"tampered"))).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/users/logout").cookie(cookie)
        .session((MockHttpSession)csrf.getRequest().getSession()).header(header,csrfToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("AKEGP_SESSION=;"),
            org.hamcrest.Matchers.containsString("Max-Age=0"),
            org.hamcrest.Matchers.containsString("HttpOnly"))));
    assertTrue(context.getBean(UsersSecurityConfig.class).cookie(token,60).isHttpOnly());
   });
 }
 @Test void oauthRedirectUsesPublicOriginInsteadOfDockerHost(){
   new WebApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(
     "akegp.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
     "akegp.auth.frontend-url=http://localhost:5173/",
     "GOOGLE_CLIENT_ID=test-google","GOOGLE_CLIENT_SECRET=test-secret",
     "GITHUB_CLIENT_ID=test-github","GITHUB_CLIENT_SECRET=test-secret").run(context->{
       assertNull(context.getStartupFailure());
       var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
       for(String provider:List.of("google","github")) {
         var result=mvc.perform(get("/oauth2/authorization/"+provider).with(request->{
           request.setServerName("app");request.setServerPort(8080);return request;
         })).andExpect(status().is3xxRedirection()).andReturn();
         String location=result.getResponse().getHeader("Location");
         var query=org.springframework.web.util.UriComponentsBuilder.fromUriString(location).build().getQueryParams();
         String callback=java.net.URLDecoder.decode(query.getFirst("redirect_uri"),java.nio.charset.StandardCharsets.UTF_8);
         assertEquals("http://localhost:5173/login/oauth2/code/"+provider,callback);
         assertNotNull(result.getRequest().getSession(false));
       }
     });
 }
 @Test void weakSecretsFailAtStartup(){assertThrows(IllegalArgumentException.class,()->new JwtTokenService("c2hvcnQ=",1000));}
}
