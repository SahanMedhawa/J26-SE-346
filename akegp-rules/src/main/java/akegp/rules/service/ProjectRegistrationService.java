package akegp.rules.service;

import akegp.rules.api.ProjectRegistrationRequest;
import akegp.rules.api.ProjectStatusUpdateRequest;
import akegp.rules.api.ProjectUpdateRequest;
import akegp.rules.domain.RegisteredProject;
import java.util.List;
import java.util.UUID;

public interface ProjectRegistrationService {
  RegisteredProject register(ProjectRegistrationRequest request);

  List<RegisteredProject> findAll();

  RegisteredProject findById(UUID id);

  RegisteredProject update(UUID id, ProjectUpdateRequest request);

  RegisteredProject updateStatus(UUID id, ProjectStatusUpdateRequest request);
}
