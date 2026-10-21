package akegp.enforcement.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "akegp.enforcement" in application.yml. */
@ConfigurationProperties("akegp.enforcement")
public class EnforcementProperties {
  /** The GitHub pipeline sends this in the x-api-key header. Change it outside development. */
  private String apiKey = "dev-key";
  /** Project used for repositories that are not listed in "projects" (empty = such repositories are held). */
  private String defaultProjectId;
  /** Repository (owner/name) -> Rules project id. */
  private Map<String, String> projects = new LinkedHashMap<>();
  /** memory | jdbc */
  private String persistence = "memory";
  /** mock | real */
  private String conformance = "mock";
  private final Github github = new Github();

  public static class Github {
    /** Empty token = dry run: statuses are only logged, nothing is sent to GitHub. */
    private String token = "";
    private String apiUrl = "https://api.github.com";
    private String statusContext = "architecture-enforcement";
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getStatusContext() { return statusContext; }
    public void setStatusContext(String statusContext) { this.statusContext = statusContext; }
  }

  public String getApiKey() { return apiKey; }
  public void setApiKey(String apiKey) { this.apiKey = apiKey; }
  public String getDefaultProjectId() { return defaultProjectId; }
  public void setDefaultProjectId(String defaultProjectId) { this.defaultProjectId = defaultProjectId; }
  public Map<String, String> getProjects() { return projects; }
  public void setProjects(Map<String, String> projects) { this.projects = projects; }
  public String getPersistence() { return persistence; }
  public void setPersistence(String persistence) { this.persistence = persistence; }
  public String getConformance() { return conformance; }
  public void setConformance(String conformance) { this.conformance = conformance; }
  public Github getGithub() { return github; }
}