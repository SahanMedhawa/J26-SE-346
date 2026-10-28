package akegp.users.auth.config;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;
@Configuration(proxyBeanMethods=false)
public class OAuthProviders {
 @Bean ClientRegistrationRepository clientRegistrationRepository(Environment env) {
   var registrations=new ArrayList<ClientRegistration>();
   // Use the public browser origin, never the internal Docker/proxy request host.
   String frontend=env.getProperty("akegp.auth.frontend-url",
       env.getProperty("AKEGP_FRONTEND_URL","http://localhost:5173")).replaceAll("/+$","");
   for(var provider:List.of("google","github")) {
     String id=env.getProperty(provider.toUpperCase()+"_CLIENT_ID","");
     String secret=env.getProperty(provider.toUpperCase()+"_CLIENT_SECRET","");
     if(!id.isBlank() && !secret.isBlank()) {
       var builder=(provider.equals("google")?CommonOAuth2Provider.GOOGLE:CommonOAuth2Provider.GITHUB).getBuilder(provider).clientId(id).clientSecret(secret)
           .redirectUri(frontend+"/login/oauth2/code/{registrationId}");
       if(provider.equals("github")) builder.scope("read:user","user:email");
       registrations.add(builder.build());
     }
   }
   return new Registrations(registrations);
 }
 public static class Registrations implements ClientRegistrationRepository,Iterable<ClientRegistration> {
   private final List<ClientRegistration> values;
   Registrations(List<ClientRegistration> v){values=List.copyOf(v);}
   public ClientRegistration findByRegistrationId(String id){return values.stream().filter(v->v.getRegistrationId().equals(id)).findFirst().orElse(null);}
   public Iterator<ClientRegistration> iterator(){return values.iterator();}
 }
}
