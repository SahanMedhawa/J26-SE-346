package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;

import akegp.rules.github.GitHubRepository;
import akegp.rules.service.RepositoryBrowsingException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubRepositoryTest {
  @ParameterizedTest
  @ValueSource(strings = {"https://github.com/shop/orders", "https://github.com/shop/orders/",
      "https://github.com/shop/orders.git", " https://GITHUB.COM/shop/orders.git/ "})
  void parsesSupportedPublicRepositoryUrls(String url) {
    assertEquals(new GitHubRepository("shop", "orders"), GitHubRepository.parse(url));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not a url", "http://github.com/shop/orders",
      "https://gitlab.com/shop/orders", "https://github.com.evil.test/shop/orders",
      "https://github.com@evil.test/shop/orders", "https://user:secret@github.com/shop/orders",
      "https://github.com:443/shop/orders", "https://github.com/shop", "https://github.com/shop/orders/tree/main",
      "https://github.com/shop/orders?url=http://localhost", "https://github.com/shop/orders#main",
      "https://github.com/shop/..", "https://github.com/shop/.", "https://github.com/shop/.git",
      "https://github.com/shop/%2e%2e", "https://github.com/shop/orders%2fother",
      "https://github.com//shop/orders", "file:///etc/passwd", "http://127.0.0.1/repo",
      "git@github.com:shop/orders.git", "https://github.com/shop\\orders"})
  void rejectsMissingUnsupportedAndUnsafeUrls(String url) {
    var error = assertThrows(RepositoryBrowsingException.class, () -> GitHubRepository.parse(url));
    assertEquals(400, error.status().value());
    assertFalse(error.getMessage().contains("secret"));
  }
}
