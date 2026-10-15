package akegp.rules.api;

import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.service.ADRService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects/{projectId}/adrs")
public class ADRController {
  private final ADRService adrService;

  public ADRController(ADRService adrService) {
    this.adrService = adrService;
  }

  @PostMapping
  public ResponseEntity<ADRResponse> create(
      @PathVariable("projectId") UUID projectId, @Valid @RequestBody CreateADRRequest request) {
    ArchitectureDecisionRecord adr = adrService.create(projectId, request);
    return ResponseEntity.created(URI.create("/api/rules/projects/" + projectId + "/adrs/" + adr.id()))
        .body(toResponse(adr));
  }

  @GetMapping
  public List<ADRResponse> findAll(
      @PathVariable("projectId") UUID projectId,
      @RequestParam(name = "status", required = false) ADRStatus status,
      @RequestParam(name = "search", required = false) String search) {
    return adrService.findAll(projectId, status, search).stream().map(ADRController::toResponse).toList();
  }

  @GetMapping("/{adrUuid}")
  public ADRResponse findById(
      @PathVariable("projectId") UUID projectId, @PathVariable("adrUuid") UUID adrUuid) {
    return toResponse(adrService.findById(projectId, adrUuid));
  }

  @PutMapping("/{adrUuid}")
  public ADRResponse update(
      @PathVariable("projectId") UUID projectId,
      @PathVariable("adrUuid") UUID adrUuid,
      @Valid @RequestBody UpdateADRRequest request) {
    return toResponse(adrService.update(projectId, adrUuid, request));
  }

  @DeleteMapping("/{adrUuid}")
  public ResponseEntity<Void> delete(
      @PathVariable("projectId") UUID projectId, @PathVariable("adrUuid") UUID adrUuid) {
    adrService.delete(projectId, adrUuid);
    return ResponseEntity.noContent().build();
  }

  private static ADRResponse toResponse(ArchitectureDecisionRecord adr) {
    return new ADRResponse(
        adr.id(), adr.adrId(), adr.projectId(), adr.title(), adr.status(), adr.context(),
        adr.decision(), adr.consequences(), adr.version(), adr.createdAt(), adr.updatedAt(),
        null, List.of());
  }
}
