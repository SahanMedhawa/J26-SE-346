package akegp.users.config;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class UsersDataSourceConfig {

  @Bean(name = "usersDataSourceProperties")
  @ConfigurationProperties("akegp.datasources.users")
  DataSourceProperties usersDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "usersDataSource")
  DataSource usersDataSource(
      @Qualifier("usersDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().build();
  }

  @Bean(name = "usersJdbcTemplate")
  JdbcTemplate usersJdbcTemplate(@Qualifier("usersDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean(name = "usersTransactionManager")
  JdbcTransactionManager usersTransactionManager(@Qualifier("usersDataSource") DataSource dataSource) {
    return new JdbcTransactionManager(dataSource);
  }
}
