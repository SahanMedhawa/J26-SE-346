package akegp.analytics;

import akegp.analytics.api.AnalyticsController;
import akegp.analytics.config.AnalyticsQueryConfig;
import akegp.analytics.ingestion.ConfiguredAnalyticsCommand;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

/** Read-only loopback smoke server; does not start other modules, migrate or seed. */
public final class AnalyticsPreviewServer {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Explicit environment file required");
    var env = ConfiguredAnalyticsCommand.environment(Path.of(args[0]));
    ConfiguredAnalyticsCommand.requireNeon(env.getProperty("AKEGP_ANALYTICS_DB_URL"));
    env.getPropertySources().addFirst(new MapPropertySource("preview-settings", Map.of(
        "server.address", "127.0.0.1", "server.port", "8087", "akegp.analytics.demo-enabled", "true",
        "spring.config.location", "optional:classpath:/analytics-preview.properties",
        "logging.level.root", "OFF", "spring.main.banner-mode", "off")));
    var app = new SpringApplication(PreviewConfig.class);
    app.setEnvironment(env);
    app.run();
    System.out.println("Analytics read-only demo preview ready on loopback port 8087.");
  }
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
  @Import({AnalyticsQueryConfig.class, AnalyticsController.class})
  static class PreviewConfig {}
}
