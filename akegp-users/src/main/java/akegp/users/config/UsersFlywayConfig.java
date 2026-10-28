package akegp.users.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UsersFlywayConfig {

  @Bean(name = "usersFlyway", initMethod = "migrate")
  Flyway usersFlyway(@Qualifier("usersDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .table("users_flyway_schema_history")
        .baselineOnMigrate(true).baselineVersion("0")
        .locations("classpath:db/migration/users")
        .load();
  }
}
