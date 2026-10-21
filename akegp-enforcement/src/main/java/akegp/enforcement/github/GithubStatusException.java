package akegp.enforcement.github;

public class GithubStatusException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public GithubStatusException(String message) { super(message); }
  public GithubStatusException(String message, Throwable cause) { super(message, cause); }
}
