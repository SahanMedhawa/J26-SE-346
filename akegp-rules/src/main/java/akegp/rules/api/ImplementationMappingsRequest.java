package akegp.rules.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImplementationMappingsRequest(
    @NotNull List<String> sourceFiles, @NotNull List<String> targetFiles) {}
