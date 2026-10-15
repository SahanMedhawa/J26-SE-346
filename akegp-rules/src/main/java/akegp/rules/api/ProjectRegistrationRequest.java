package akegp.rules.api;

import akegp.rules.domain.ArchitectureStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.hibernate.validator.constraints.URL;

public record ProjectRegistrationRequest(
    @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must not exceed 150 characters")
        String name,
    @Size(max = 2_000, message = "Project description must not exceed 2000 characters")
        String description,
    @NotBlank(message = "Git repository URL is required")
        @URL(message = "Git repository URL must be a valid URL")
        @Pattern(
            regexp = "^https?://.*$",
            message = "Git repository URL must use HTTP or HTTPS")
        String repositoryUrl,
    @NotEmpty(message = "At least one architecture style is required")
        Set<ArchitectureStyle> architectureStyles) {}
