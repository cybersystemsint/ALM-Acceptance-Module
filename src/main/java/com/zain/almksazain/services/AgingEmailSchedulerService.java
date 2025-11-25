package com.zain.almksazain.services;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import com.zain.almksazain.model.AgingEmailConfig;
import com.zain.almksazain.repo.AgingEmailConfigRepository;
import com.zain.almksazain.utlities.CronUtils;

@Service
public class AgingEmailSchedulerService implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(AgingEmailSchedulerService.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final AgingEmailConfigRepository configRepo;
    private final SlaNotificationService slaNotificationService;

    // map configId -> ScheduledFuture
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public AgingEmailSchedulerService(ThreadPoolTaskScheduler taskScheduler,
                                      AgingEmailConfigRepository configRepo,
                                      SlaNotificationService slaNotificationService) {
        this.taskScheduler = taskScheduler;
        this.configRepo = configRepo;
        this.slaNotificationService = slaNotificationService;
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing AgingEmailSchedulerService - scheduling enabled jobs from DB");
        List<AgingEmailConfig> all = configRepo.findAll();
        for (AgingEmailConfig cfg : all) {
            if (cfg.isEnabled()) {
                try {
                    scheduleConfig(cfg, true);
                } catch (Exception ex) {
                    logger.error("Failed to schedule config id={} jobName='{}': {}", cfg.getId(), cfg.getJobName(), ex.getMessage(), ex);
                }
            }
        }
    }


    @Transactional
    public synchronized void scheduleConfig(AgingEmailConfig cfg) {
        scheduleConfig(cfg, true);
    }


    @Transactional
    public synchronized void scheduleConfig(AgingEmailConfig cfg, boolean persistMetadata) {
        if (cfg == null) return;
        // cancel any existing
        cancelScheduled(cfg.getId());

        if (!cfg.isEnabled()) {
            logger.info("Config {} (id={}) is disabled; skipping schedule", cfg.getJobName(), cfg.getId());
            return;
        }
        if (!CronUtils.isValidCron(cfg.getCronExpression())) {
            logger.warn("Invalid cron expression for config id={} cron={}", cfg.getId(), cfg.getCronExpression());
            return;
        }

        ZoneId zone = ZoneId.of(Optional.ofNullable(cfg.getTimezone()).filter(t -> !t.isBlank()).orElse("UTC"));

        CronTrigger cronTrigger = new CronTrigger(cfg.getCronExpression(), zone);

        Runnable task = () -> {
            try {
                // record lastRunTime
                cfg.setLastRunTime(LocalDateTime.now(zone));
                configRepo.save(cfg);

                // route by targetType (approver/manager) instead of free-form jobName
                String rawTarget = cfg.getTargetType();
                String targetType = Optional.ofNullable(rawTarget).map(String::trim).map(String::toLowerCase).orElse("approver");

                logger.info("Executing scheduled config id={} jobName='{}' targetType='{}' department='{}' userAging='{}'",
                        cfg.getId(), cfg.getJobName(), targetType, cfg.getDepartment(), cfg.getUserAging());
                Map<String, Object> filters = new HashMap<>();
                if (cfg.getDepartment() != null && !cfg.getDepartment().isBlank() && !"ALL".equalsIgnoreCase(cfg.getDepartment().trim())) {
                    // pass the full departments list (may be single or multiple) so downstream can handle it
                    List<String> depts = cfg.getDepartmentsList();
                    if (depts != null && !depts.isEmpty()) {
                        filters.put("department", depts);
                        // also provide departmentName for compatibility (first entry)
                        filters.put("departmentName", depts.get(0));
                    }
                }
                if (cfg.getUserAging() != null) {
                    filters.put("userAging", cfg.getUserAging());
                    // provide alias minUserAging too (in case other code expects it)
                    filters.put("minUserAging", cfg.getUserAging());
                }

                if ("approver".equals(targetType)) {
                    slaNotificationService.runStage1RemindersWithFilters(filters);
                } else if ("manager".equals(targetType)) {
                    slaNotificationService.runStage2EscalationsWithFilters(filters);
                } else {
                    // fallback: unknown targetType
                    logger.warn("Unknown targetType='{}' for config id={}. No action taken.", rawTarget, cfg.getId());
                }
            } catch (Throwable t) {
                logger.error("Error while running scheduled job id=" + cfg.getId(), t);
            } finally {
                // compute & persist next run (when the job runs we persist the nextRunTime/updatedAt)
                try {
                    Date next = cronTrigger.nextExecutionTime(new SimpleTriggerContextWrapper());
                    if (next != null) {
                        LocalDateTime nextRun = LocalDateTime.ofInstant(next.toInstant(), zone);
                        cfg.setNextRunTime(nextRun);
                    } else {
                        cfg.setNextRunTime(null);
                    }
                    // Persist the computed nextRunTime so DB reflects last run's next schedule.
                    cfg.setUpdatedAt(LocalDateTime.now(zone));
                    configRepo.save(cfg);
                } catch (Exception e) {
                    logger.warn("Failed to compute nextRunTime for config id={}: {}", cfg.getId(), e.getMessage());
                }
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(task, cronTrigger);
        scheduledTasks.put(cfg.getId(), future);

        // compute and optionally persist nextRunTime now
        try {
            Date next = cronTrigger.nextExecutionTime(new SimpleTriggerContextWrapper());
            if (next != null) {
                cfg.setNextRunTime(LocalDateTime.ofInstant(next.toInstant(), zone));
            } else {
                cfg.setNextRunTime(null);
            }

            if (persistMetadata) {
                // Only update updatedAt and persist if requested.
                cfg.setUpdatedAt(LocalDateTime.now(zone));
                configRepo.save(cfg);
            } else {

                logger.debug("Scheduled job (no metadata persist) id={} jobName='{}' cron={} tz={}", cfg.getId(), cfg.getJobName(), cfg.getCronExpression(), zone);
            }
        } catch (Exception e) {
            logger.warn("Failed to compute next-run for config id={}", cfg.getId(), e);
        }

        logger.info("Scheduled config id={} jobName='{}' targetType='{}' cron={} tz={}", cfg.getId(), cfg.getJobName(),
                Optional.ofNullable(cfg.getTargetType()).map(String::trim).map(String::toLowerCase).orElse("approver"),
                cfg.getCronExpression(), zone);
    }

    public synchronized void cancelScheduled(Long configId) {
        if (configId == null) return;
        ScheduledFuture<?> f = scheduledTasks.remove(configId);
        if (f != null) {
            boolean canceled = f.cancel(false);
            logger.info("Canceled scheduled config id={} canceled={}", configId, canceled);
        }
    }

    public void runNow(Long configId) {
        AgingEmailConfig cfg = configRepo.findById(configId).orElse(null);
        if (cfg == null) throw new IllegalArgumentException("Config not found: " + configId);
        // run in scheduler thread pool to avoid blocking caller
        taskScheduler.execute(() -> {
            logger.info("Manually running config id={}", configId);
            try {
                String rawTarget = cfg.getTargetType();
                String targetType = Optional.ofNullable(rawTarget).map(String::trim).map(String::toLowerCase).orElse("approver");

                // Build filters for manual run as well
                Map<String, Object> filters = new HashMap<>();
                if (cfg.getDepartment() != null && !cfg.getDepartment().isBlank() && !"ALL".equalsIgnoreCase(cfg.getDepartment().trim())) {
                    List<String> depts = cfg.getDepartmentsList();
                    if (depts != null && !depts.isEmpty()) {
                        filters.put("department", depts);
                        filters.put("departmentName", depts.get(0));
                    }
                }
                if (cfg.getUserAging() != null) {
                    filters.put("userAging", cfg.getUserAging());
                    filters.put("minUserAging", cfg.getUserAging());
                }

                if ("approver".equals(targetType)) {
                    slaNotificationService.runStage1RemindersWithFilters(filters);
                } else if ("manager".equals(targetType)) {
                    slaNotificationService.runStage2EscalationsWithFilters(filters);
                } else {
                    logger.warn("Unknown targetType='{}' for manual run id={}", rawTarget, cfg.getId());
                }
                cfg.setLastRunTime(LocalDateTime.now(ZoneId.of(Optional.ofNullable(cfg.getTimezone()).filter(t -> !t.isBlank()).orElse("UTC"))));
                configRepo.save(cfg);
            } catch (Throwable t) {
                logger.error("Error running job now id=" + configId, t);
            }
        });
    }

    @Override
    public void destroy() {
        // cancel everything on shutdown
        for (Long id : new ArrayList<>(scheduledTasks.keySet())) {
            cancelScheduled(id);
        }
    }

    // A minimal TriggerContext to compute nextExecutionTime once relative to now.
    private static class SimpleTriggerContextWrapper implements TriggerContext {
        private final Date now = new Date();
        @Override public Date lastScheduledExecutionTime() { return null; }
        @Override public Date lastActualExecutionTime() { return null; }
        @Override public Date lastCompletionTime() { return now; }
    }
}