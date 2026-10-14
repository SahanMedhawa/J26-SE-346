package akegp.rules.api;

import akegp.rules.domain.ProjectStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectStatusUpdateRequest(
    @NotNull(message = "Project status is required") ProjectStatus status) {}
