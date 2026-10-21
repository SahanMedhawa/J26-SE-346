package akegp.conformance.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Neo4jConfig.Properties.class)
public class Neo4jConfig {
  @Bean(destroyMethod = "close")
  Driver neo4jDriver(Properties properties) {
    return GraphDatabase.driver(properties.uri(), AuthTokens.basic(properties.username(), properties.password()));
  }

  @ConfigurationProperties(prefix = "akegp.neo4j")
  public record Properties(String uri, String username, String password) {}
}