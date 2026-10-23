package com.vidyasetu.masterdata;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MasterDataController {
    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @GetMapping("/institutions")
    @PreAuthorize("hasAuthority('institution:read')")
    public List<MasterDataService.InstitutionSummary> listInstitutions() {
        return masterDataService.listInstitutions();
    }

    @GetMapping("/institutions/{id}")
    @PreAuthorize("hasAuthority('institution:read')")
    public Map<String, Object> getInstitutionDetail(@PathVariable UUID id) {
        return masterDataService.getInstitutionDetail(id);
    }

    @GetMapping("/institutions/{id}/programs")
    @PreAuthorize("hasAuthority('institution:read')")
    public List<MasterDataService.ProgramDto> getPrograms(@PathVariable UUID id) {
        return masterDataService.getPrograms(id);
    }

    @PutMapping("/programs/{id}")
    @PreAuthorize("hasAuthority('institution:manage')")
    public MasterDataService.ProgramDto updateProgram(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Integer approvedIntake = body.get("approvedIntake") != null ? ((Number) body.get("approvedIntake")).intValue() : null;
        Integer approvedLateralIntake = body.get("approvedLateralIntake") != null ? ((Number) body.get("approvedLateralIntake")).intValue() : null;
        Boolean tfwEnabled = (Boolean) body.get("tfwEnabled");
        Boolean fnOciEnabled = (Boolean) body.get("fnOciEnabled");
        return masterDataService.updateProgram(id, approvedIntake, approvedLateralIntake, tfwEnabled, fnOciEnabled);
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAuthority('policy:manage') or hasAuthority('institution:read')")
    public List<MasterDataService.PolicyDto> listPolicy() {
        return masterDataService.listPolicy();
    }

    @PutMapping("/policy/{key}")
    @PreAuthorize("hasAuthority('policy:manage')")
    public MasterDataService.PolicyDto updatePolicy(@PathVariable String key, @RequestBody Map<String, String> body) {
        return masterDataService.updatePolicy(key, body.get("value"));
    }
}
