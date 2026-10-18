package akegp.rules.config;

import akegp.rules.github.GitHubRepositoryClient;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class GitHubBrowsingConfig {
  @Bean
  public GitHubRepositoryClient gitHubRepositoryClient(
      @Value("${AKEGP_GITHUB_TOKEN:}") String token,
      @Value("${AKEGP_GITHUB_CONNECT_TIMEOUT_MS:5000}") int connectTimeout,
      @Value("${AKEGP_GITHUB_READ_TIMEOUT_MS:15000}") int readTimeout,
      @Value("${AKEGP_GITHUB_MAX_RESPONSE_BYTES:8388608}") int maxResponseBytes,
      @Value("${AKEGP_GITHUB_MAX_FILES:20000}") int maxFiles) {
    return new GitHubRepositoryClient(clientBuilder(token, connectTimeout, readTimeout).build(),
        maxResponseBytes, maxFiles);
  }

  public static RestClient.Builder clientBuilder(String token, int connectTimeout, int readTimeout) {
    var builder = RestClient.builder().requestFactory(requestFactory(connectTimeout, readTimeout))
        .defaultHeader("Accept", "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .defaultHeader("User-Agent", "AKEGP-Repository-Browser");
    if (!token.isBlank()) builder.defaultHeader("Authorization", "Bearer " + token.strip());
    return builder;
  }

  public static SimpleClientHttpRequestFactory requestFactory(int connectTimeout, int readTimeout) {
    if (connectTimeout <= 0 || readTimeout <= 0) {
      throw new IllegalArgumentException("GitHub timeouts must be positive");
    }
    var factory = new SimpleClientHttpRequestFactory() {
      @Override
      protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
        super.prepareConnection(connection, method);
        // Never forward the optional token to a redirect destination.
        connection.setInstanceFollowRedirects(false);
      }
    };
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }
}
