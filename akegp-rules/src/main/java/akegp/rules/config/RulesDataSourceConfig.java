package akegp.rules.config;

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
public class RulesDataSourceConfig {

  @Bean(name = "rulesDataSourceProperties")
  @ConfigurationProperties("akegp.datasources.rules")
  DataSourceProperties rulesDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "rulesDataSource")
  DataSource rulesDataSource(
      @Qualifier("rulesDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().build();
  }

  @Bean(name = "rulesJdbcTemplate")
  JdbcTemplate rulesJdbcTemplate(@Qualifier("rulesDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean(name = "rulesTransactionManager")
  JdbcTransactionManager rulesTransactionManager(@Qualifier("rulesDataSource") DataSource dataSource) {
    return new JdbcTransactionManager(dataSource);
  }
}
