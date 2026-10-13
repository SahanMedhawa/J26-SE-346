package akegp.rules.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RulesFlywayConfig {

  @Bean(name = "rulesFlyway", initMethod = "migrate")
  Flyway rulesFlyway(@Qualifier("rulesDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/rules")
        .load();
  }
}
