package akegp.rules.service;

import akegp.rules.api.CreateADRRequest;
import akegp.rules.api.UpdateADRRequest;
import akegp.rules.domain.ArchitectureDecisionRecord;
import java.util.List;
import java.util.UUID;

public interface ADRService {
  ArchitectureDecisionRecord create(UUID projectId, CreateADRRequest request);

  List<ArchitectureDecisionRecord> findAll(
      UUID projectId, akegp.rules.domain.ADRStatus status, String search);

  ArchitectureDecisionRecord findById(UUID projectId, UUID id);

  ArchitectureDecisionRecord update(UUID projectId, UUID id, UpdateADRRequest request);

  void delete(UUID projectId, UUID id);
}
