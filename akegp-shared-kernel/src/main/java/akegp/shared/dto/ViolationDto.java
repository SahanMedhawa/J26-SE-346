package akegp.shared.dto;
import akegp.shared.enums.*;
public record ViolationDto(String id, String ruleId, Severity severity, ViolationState state) {}
