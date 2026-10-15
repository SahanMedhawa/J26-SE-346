package akegp.rules.api;

import akegp.rules.domain.ADRStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateADRRequest(
    @NotBlank(message = "ADR ID is required")
        @Pattern(regexp = "^ADR-[0-9]{3,}$", message = "ADR ID must follow the format ADR-001")
        String adrId,
    @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
        String title,
    @NotNull(message = "Status is required") ADRStatus status,
    @NotBlank(message = "Context is required")
        @Size(min = 10, max = 5000, message = "Context must be between 10 and 5000 characters")
        String context,
    @NotBlank(message = "Decision is required")
        @Size(min = 10, max = 5000, message = "Decision must be between 10 and 5000 characters")
        String decision,
    @NotBlank(message = "Consequences are required")
        @Size(min = 10, max = 5000, message = "Consequences must be between 10 and 5000 characters")
        String consequences,
    @NotNull(message = "Version is required") @jakarta.validation.constraints.Min(value = 1, message = "Version must be at least 1")
        Integer version) {}
