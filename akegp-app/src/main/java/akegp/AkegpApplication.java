package akegp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages = "akegp")
@EnableScheduling
public class AkegpApplication {
  public static void main(String[] args) { SpringApplication.run(AkegpApplication.class, args); }
}
