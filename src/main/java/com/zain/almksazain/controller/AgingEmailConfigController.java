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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
public class AgingEmailConfigController {
    private static final Logger logger = LoggerFactory.getLogger(AgingEmailConfigController.class);

    @Autowired private AgingEmailConfigRepository configRepo;
    @Autowired private AgingEmailSchedulerService schedulerService;
    @Autowired private deptsrepo deptsRepo;

    @PostMapping("/aging-email-configs")
    public ResponseEntity<?> fetch(@RequestBody FilterRequestDto req) {
        logger.info("Request to fetch AgingEmailConfigs with filter: {}", req);
        int page = Optional.ofNullable(req.getPage()).orElse(0);
        int size = Optional.ofNullable(req.getSize()).orElse(100);

        var spec = AgingEmailConfigSpecifications.buildFromRequest(req);
        Page<AgingEmailConfig> result = configRepo.findAll(spec, PageRequest.of(page, size));
        logger.debug("Fetched {} AgingEmailConfig records", result.getTotalElements());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/aging-email-config")
    public ResponseEntity<?> create(@RequestBody AgingEmailConfigRequest req, Principal principal) {
        logger.info("Creating AgingEmailConfig with request: {}, principal: {}", req, principal != null ? principal.getName() : null);
        String cron = null;
        if (req.getTime() != null && !req.getTime().isBlank()) {
            cron = CronUtils.timeToCronExpression(req.getTime());
            if (cron == null) {
                logger.warn("Invalid time format for request: {}", req.getTime());
                return ResponseEntity.badRequest().body("Invalid time format. Use HH:mm or HH:mm:ss (24-hour).");
            }
        } else {
            cron = req.getCronExpression();
        }

        if (!CronUtils.isValidCron(cron)) {
            logger.warn("Invalid cron expression: {}", cron);
            return ResponseEntity.badRequest().body("Invalid cron expression");
        }

        // Normalize and validate targetType
        String rawTarget = req.getTargetType();
        String targetType = (rawTarget == null || rawTarget.isBlank()) ? "approver" : rawTarget.trim().toLowerCase();
        if (!isValidTargetType(targetType)) {
            logger.warn("Invalid targetType provided: {}", targetType);
            return ResponseEntity.badRequest().body("Invalid targetType. Allowed values: approver, manager");
        }
        // check duplicate
        if (configRepo.existsByTargetTypeAndCronExpression(targetType, cron)) {
            logger.warn("Duplicate AgingEmailConfig detected for targetType={} and cron={}", targetType, cron);
            return ResponseEntity.status(409).body("A configuration with the same cron expression already exists for targetType=" + targetType);
        }

        // timezone validation and create
        String tzInput = (req.getTimezone() == null || req.getTimezone().isBlank()) ? "UTC" : req.getTimezone();
        if (!CronUtils.isValidTimezone(tzInput)) {
            logger.warn("Invalid timezone passed: {}", tzInput);
            return ResponseEntity.badRequest().body("Invalid timezone");
        }
        ZoneId zone = CronUtils.normalizeZone(tzInput);
        LocalDateTime now = LocalDateTime.now(zone);

        // validate departments
        List<String> reqDepts = null;
        if (req.getDepartments() != null && !req.getDepartments().isEmpty()) {
            reqDepts = req.getDepartments();
        } else if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            reqDepts = List.of(req.getDepartment());
        }

        if (reqDepts != null) {
            for (String d : reqDepts) {
                if (d == null) continue;
                if ("all".equalsIgnoreCase(d.trim())) {
                    reqDepts = List.of("ALL");
                    break;
                }
            }
            if (!(reqDepts.size() == 1 && "ALL".equalsIgnoreCase(reqDepts.get(0)))) {
                for (String d : reqDepts) {
                    if (d == null || d.isBlank()) {
                        logger.warn("Blank department entry in request: {}", reqDepts);
                        return ResponseEntity.badRequest().body("Invalid department entry (blank)");
                    }
                    departmentsdata dept = deptsRepo.findByDeptName(d);
                    if (dept == null) {
                        logger.warn("Invalid department name provided: {}", d);
                        return ResponseEntity.badRequest().body("Invalid department: " + d);
                    }
                }
            }
        }

        if (req.getUserAging() != null && req.getUserAging() < 0) {
            logger.warn("userAging value < 0: {}", req.getUserAging());
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

        // departments
        if (reqDepts == null || reqDepts.isEmpty()) {
            cfg.setDepartment(null);
        } else if (reqDepts.size() == 1 && "ALL".equalsIgnoreCase(reqDepts.get(0))) {
            cfg.setDepartment("ALL");
        } else {
            cfg.setDepartments(reqDepts);
        }

        cfg.setUserAging(req.getUserAging());
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            cfg.setCreatedBy(principal.getName());
        } else {
            cfg.setCreatedBy(req.getCreatedBy());
        }
        if (req.getCc() != null) cfg.setCc(req.getCc());
        if (req.getBcc() != null) cfg.setBcc(req.getBcc());

        AgingEmailConfig saved = configRepo.save(cfg);
        logger.info("AgingEmailConfig created: id={}, jobName={}", saved.getId(), saved.getJobName());

        if (saved.isEnabled()) {
            logger.info("Scheduling newly created AgingEmailConfig: id={}", saved.getId());
            schedulerService.scheduleConfig(saved, false);
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/aging-email-config/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AgingEmailConfigRequest req) {
        logger.info("Updating AgingEmailConfig id={} with request: {}", id, req);
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) {
            logger.warn("AgingEmailConfig not found for update: id={}", id);
            return ResponseEntity.notFound().build();
        }

        AgingEmailConfig existing = o.get();
        if (req.getJobName() != null) existing.setJobName(req.getJobName());

        // cron validation
        String cron = existing.getCronExpression();
        if (req.getTime() != null && !req.getTime().isBlank()) {
            String converted = CronUtils.timeToCronExpression(req.getTime());
            if (converted == null) {
                logger.warn("Invalid time format on update: {}", req.getTime());
                return ResponseEntity.badRequest().body("Invalid time format. Use HH:mm or HH:mm:ss (24-hour).");
            }
            cron = converted;
        } else if (req.getCronExpression() != null && !req.getCronExpression().isBlank()) {
            cron = req.getCronExpression();
        }

        if (!CronUtils.isValidCron(cron)) {
            logger.warn("Invalid cron expression update: {}", cron);
            return ResponseEntity.badRequest().body("Invalid cron expression");
        }

        String currentTarget = existing.getTargetType() == null ? "approver" : existing.getTargetType();
        String newTarget = currentTarget;
        if (req.getTargetType() != null && !req.getTargetType().isBlank()) {
            newTarget = req.getTargetType().trim().toLowerCase();
        }
        if (!isValidTargetType(newTarget)) {
            logger.warn("Invalid targetType on update: {}", newTarget);
            return ResponseEntity.badRequest().body("Invalid targetType. Allowed values: approver, manager");
        }

        if (configRepo.existsByTargetTypeAndCronExpressionAndIdNot(newTarget, cron, id)) {
            logger.warn("Duplicate config detected on update for id={}, targetType={}, cron={}", id, newTarget, cron);
            return ResponseEntity.status(409).body("A configuration with the same cron expression already exists for targetType=" + newTarget);
        }

        existing.setCronExpression(cron);
        existing.setTargetType(newTarget);

        if (req.getTimezone() != null) {
            if (!CronUtils.isValidTimezone(req.getTimezone())) {
                logger.warn("Invalid timezone on update: {}", req.getTimezone());
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
                            logger.warn("Blank department entry on update: {}", reqDepts);
                            return ResponseEntity.badRequest().body("Invalid department entry (blank)");
                        }
                        departmentsdata dept = deptsRepo.findByDeptName(d);
                        if (dept == null) {
                            logger.warn("Invalid department name on update: {}", d);
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
                logger.warn("userAging < 0 on update: {}", req.getUserAging());
                return ResponseEntity.badRequest().body("userAging must be >= 0 when provided");
            }
            existing.setUserAging(req.getUserAging());
        }

        if (req.getCc() != null) existing.setCc(req.getCc());
        if (req.getBcc() != null) existing.setBcc(req.getBcc());
        existing.setUpdatedAt(java.time.LocalDateTime.now());

        AgingEmailConfig saved = configRepo.save(existing);
        logger.info("AgingEmailConfig updated: id={}, jobName={}", saved.getId(), saved.getJobName());

        if (saved.isEnabled()) {
            logger.info("Rescheduling AgingEmailConfig: id={}", saved.getId());
            schedulerService.scheduleConfig(saved);
        } else {
            logger.info("Cancelling schedule for AgingEmailConfig: id={}", saved.getId());
            schedulerService.cancelScheduled(saved.getId());
        }
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/aging-email-config/{id}/enable")
    public ResponseEntity<?> enable(@PathVariable Long id) {
        logger.info("Enabling AgingEmailConfig id={}", id);
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) {
            logger.warn("AgingEmailConfig not found for enable: id={}", id);
            return ResponseEntity.notFound().build();
        }
        AgingEmailConfig cfg = o.get();
        cfg.setEnabled(true);
        cfg.setUpdatedAt(java.time.LocalDateTime.now());
        configRepo.save(cfg);
        schedulerService.scheduleConfig(cfg);
        logger.info("AgingEmailConfig enabled and scheduled: id={}", id);
        return ResponseEntity.ok(cfg);
    }

    @PostMapping("/aging-email-config/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable Long id) {
        logger.info("Disabling AgingEmailConfig id={}", id);
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) {
            logger.warn("AgingEmailConfig not found for disable: id={}", id);
            return ResponseEntity.notFound().build();
        }
        AgingEmailConfig cfg = o.get();
        cfg.setEnabled(false);
        cfg.setUpdatedAt(java.time.LocalDateTime.now());
        configRepo.save(cfg);
        schedulerService.cancelScheduled(cfg.getId());
        logger.info("AgingEmailConfig disabled and schedule cancelled: id={}", id);
        return ResponseEntity.ok(cfg);
    }

    @Transactional
    @DeleteMapping("/aging-email-config/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        logger.info("Deleting AgingEmailConfig id={}", id);
        Optional<AgingEmailConfig> o = configRepo.findById(id);
        if (o.isEmpty()) {
            logger.warn("AgingEmailConfig not found for delete: id={}", id);
            return ResponseEntity.notFound().build();
        }
        schedulerService.cancelScheduled(id);
        configRepo.deleteById(id);
        logger.info("AgingEmailConfig deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTargetType(String t) {
        if (t == null) return false;
        String n = t.trim().toLowerCase();
        return "approver".equals(n) || "manager".equals(n);
    }
}