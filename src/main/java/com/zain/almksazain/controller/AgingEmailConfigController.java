package com.zain.almksazain.controller;

import com.zain.almksazain.model.AgingEmailConfig;
import com.zain.almksazain.model.departmentsdata;
import com.zain.almksazain.repo.AgingEmailConfigRepository;
import com.zain.almksazain.repo.deptsrepo;
import com.zain.almksazain.services.AgingEmailSchedulerService;
import com.zain.almksazain.specs.AgingEmailConfigSpecifications;
import com.zain.almksazain.utlities.CronUtils;
import com.zain.almksazain.dto.AgingEmailConfigRequest;
import com.zain.almksazain.dto.FilterRequestDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
public class AgingEmailConfigController {

    @Autowired private AgingEmailConfigRepository configRepo;
    @Autowired private AgingEmailSchedulerService schedulerService;
    @Autowired private deptsrepo deptsRepo;

    @PostMapping("/aging-email-configs")
    public ResponseEntity<?> fetch(@RequestBody FilterRequestDto req) {
        int page = Optional.ofNullable(req.getPage()).orElse(0);
        int size = Optional.ofNullable(req.getSize()).orElse(100);

        var spec = AgingEmailConfigSpecifications.buildFromRequest(req);
        Page<AgingEmailConfig> result = configRepo.findAll(spec, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    //create aging email config
    @PostMapping("/aging-email-config")
    public ResponseEntity<?> create(@RequestBody AgingEmailConfigRequest req, Principal principal) {
        String cron = null;
        if (req.getTime() != null && !req.getTime().isBlank()) {
            cron = CronUtils.timeToCronExpression(req.getTime());
            if (cron == null) {
                return ResponseEntity.badRequest().body("Invalid time format. Use HH:mm or HH:mm:ss (24-hour).");
            }
        } else {
            cron = req.getCronExpression();
        }

        if (!CronUtils.isValidCron(cron)) {
            return ResponseEntity.badRequest().body("Invalid cron expression");
        }

        // Normalize and validate targetType: default to "approver" if missing
        String rawTarget = req.getTargetType();
        String targetType = (rawTarget == null || rawTarget.isBlank()) ? "approver" : rawTarget.trim().toLowerCase();
        if (!isValidTargetType(targetType)) {
            return ResponseEntity.badRequest().body("Invalid targetType. Allowed values: approver, manager");
        }

        // check duplicate: do not allow same cron under same target_type
        if (configRepo.existsByTargetTypeAndCronExpression(targetType, cron)) {
            return ResponseEntity.status(409).body("A configuration with the same cron expression already exists for targetType=" + targetType);
        }

        // timezone validation and create
        String tzInput = (req.getTimezone() == null || req.getTimezone().isBlank()) ? "UTC" : req.getTimezone();
        if (!CronUtils.isValidTimezone(tzInput)) {
            return ResponseEntity.badRequest().body("Invalid timezone");
        }
        ZoneId zone = CronUtils.normalizeZone(tzInput);
        LocalDateTime now = LocalDateTime.now(zone);

        // validate departments if provided and not "all"
        List<String> reqDepts = null;
        if (req.getDepartments() != null && !req.getDepartments().isEmpty()) {
            reqDepts = req.getDepartments();
        } else if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            // single legacy value provided
            reqDepts = List.of(req.getDepartment());
        }

        if (reqDepts != null) {
            // if any value is "all" -> treat as ALL
            for (String d : reqDepts) {
                if (d == null) continue;
                if ("all".equalsIgnoreCase(d.trim())) {
                    reqDepts = List.of("ALL");
                    break;
                }
            }
            if (!(reqDepts.size() == 1 && "ALL".equalsIgnoreCase(reqDepts.get(0)))) {
                // validate each named department
                for (String d : reqDepts) {
                    if (d == null || d.isBlank()) {
                        return ResponseEntity.badRequest().body("Invalid department entry (blank)");
                    }
                    departmentsdata dept = deptsRepo.findByDeptName(d);
                    if (dept == null) {
                        return ResponseEntity.badRequest().body("Invalid department: " + d);
                    }
                }
            }
        }

        // validate userAging if provided
        if (req.getUserAging() != null && req.getUserAging() < 0) {
            return ResponseEntity.badRequest().body("userAging must be >= 0 when provided");
        }

        AgingEmailConfig cfg = new AgingEmailConfig();
        cfg.setJobName(req.getJobName());
        cfg.setCronExpression(cron);
        cfg.setTimezone(zone.getId());
        cfg.setEnabled(req.getEnabled() == null ? true : req.getEnabled());
        cfg.setDescription(req.getDescription());
        cfg.setCreatedAt(now);
        cfg.setTargetType(targetType);

        // store departments (null -> all)
        if (reqDepts == null || reqDepts.isEmpty()) {
            cfg.setDepartment(null);
        } else if (reqDepts.size() == 1 && "ALL".equalsIgnoreCase(reqDepts.get(0))) {
            cfg.setDepartment("ALL");
        } else {
            cfg.setDepartments(reqDepts);
        }

        cfg.setUserAging(req.getUserAging()); // may be null => service defaults

        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            cfg.setCreatedBy(principal.getName());
        } else {
            cfg.setCreatedBy(req.getCreatedBy());
        }

        AgingEmailConfig saved = configRepo.save(cfg);

        if (saved.isEnabled()) {
            schedulerService.scheduleConfig(saved, false);
        }
        return ResponseEntity.ok(saved);
    }

     //Update aging email config
    @PutMapping("/aging-email-config/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AgingEmailConfigRequest req) {
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) return ResponseEntity.notFound().build();

        AgingEmailConfig existing = o.get();

        if (req.getJobName() != null) existing.setJobName(req.getJobName());

        // convert/validate cron
        String cron = existing.getCronExpression();
        if (req.getTime() != null && !req.getTime().isBlank()) {
            String converted = CronUtils.timeToCronExpression(req.getTime());
            if (converted == null) {
                return ResponseEntity.badRequest().body("Invalid time format. Use HH:mm or HH:mm:ss (24-hour).");
            }
            cron = converted;
        } else if (req.getCronExpression() != null && !req.getCronExpression().isBlank()) {
            cron = req.getCronExpression();
        }

        if (!CronUtils.isValidCron(cron)) {
            return ResponseEntity.badRequest().body("Invalid cron expression");
        }

        // handle targetType update or keep existing; normalize
        String currentTarget = existing.getTargetType() == null ? "approver" : existing.getTargetType();
        String newTarget = currentTarget;
        if (req.getTargetType() != null && !req.getTargetType().isBlank()) {
            newTarget = req.getTargetType().trim().toLowerCase();
        }
        if (!isValidTargetType(newTarget)) {
            return ResponseEntity.badRequest().body("Invalid targetType. Allowed values: approver, manager");
        }

        // duplicate check excluding this id
        if (configRepo.existsByTargetTypeAndCronExpressionAndIdNot(newTarget, cron, id)) {
            return ResponseEntity.status(409).body("A configuration with the same cron expression already exists for targetType=" + newTarget);
        }

        existing.setCronExpression(cron);
        existing.setTargetType(newTarget);

        if (req.getTimezone() != null) {
            if (!CronUtils.isValidTimezone(req.getTimezone())) {
                return ResponseEntity.badRequest().body("Invalid timezone");
            }
            existing.setTimezone(req.getTimezone());
        }

        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getUpdatedBy() != null) existing.setUpdatedBy(req.getUpdatedBy());

        // department update + validation
        if (req.getDepartments() != null || req.getDepartment() != null) {
            List<String> reqDepts = null;
            if (req.getDepartments() != null) reqDepts = req.getDepartments();
            else if (req.getDepartment() != null && !req.getDepartment().isBlank()) reqDepts = List.of(req.getDepartment());

            if (reqDepts == null || reqDepts.isEmpty()) {
                existing.setDepartment(null);
            } else {
                // check for explicit ALL
                for (String d : reqDepts) {
                    if (d != null && "all".equalsIgnoreCase(d.trim())) {
                        existing.setDepartment("ALL");
                        reqDepts = List.of("ALL");
                        break;
                    }
                }
                if (!(reqDepts.size() == 1 && "ALL".equalsIgnoreCase(reqDepts.get(0)))) {
                    for (String d : reqDepts) {
                        if (d == null || d.isBlank()) {
                            return ResponseEntity.badRequest().body("Invalid department entry (blank)");
                        }
                        departmentsdata dept = deptsRepo.findByDeptName(d);
                        if (dept == null) {
                            return ResponseEntity.badRequest().body("Invalid department: " + d);
                        }
                    }
                    existing.setDepartments(reqDepts);
                }
            }
        }

        // userAging update + validation
        if (req.getUserAging() != null) {
            if (req.getUserAging() < 0) {
                return ResponseEntity.badRequest().body("userAging must be >= 0 when provided");
            }
            existing.setUserAging(req.getUserAging());
        }

        existing.setUpdatedAt(java.time.LocalDateTime.now());

        AgingEmailConfig saved = configRepo.save(existing);

        if (saved.isEnabled()) {
            schedulerService.scheduleConfig(saved);
        } else {
            schedulerService.cancelScheduled(saved.getId());
        }

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/aging-email-config/{id}/enable")
    public ResponseEntity<?> enable(@PathVariable Long id) {
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) return ResponseEntity.notFound().build();
        AgingEmailConfig cfg = o.get();
        cfg.setEnabled(true);
        cfg.setUpdatedAt(java.time.LocalDateTime.now());
        configRepo.save(cfg);
        schedulerService.scheduleConfig(cfg);
        return ResponseEntity.ok(cfg);
    }

    @PostMapping("/aging-email-config/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable Long id) {
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) return ResponseEntity.notFound().build();
        AgingEmailConfig cfg = o.get();
        cfg.setEnabled(false);
        cfg.setUpdatedAt(java.time.LocalDateTime.now());
        configRepo.save(cfg);
        schedulerService.cancelScheduled(cfg.getId());
        return ResponseEntity.ok(cfg);
    }

    @DeleteMapping("/aging-email-config/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) return ResponseEntity.notFound().build();
        schedulerService.cancelScheduled(id);
        configRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------
    private boolean isValidTargetType(String t) {
        if (t == null) return false;
        String n = t.trim().toLowerCase();
        return "approver".equals(n) || "manager".equals(n);
    }
}