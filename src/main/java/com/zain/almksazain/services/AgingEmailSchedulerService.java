package com.zain.almksazain.services;

import java.time.Duration;
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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

@Service
public class AgingEmailSchedulerService implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(AgingEmailSchedulerService.class);

    private static final String INSTANCE_ID = System.getenv("HOSTNAME") != null
            ? System.getenv("HOSTNAME")
            : "unknown-instance";

    // These jobs run once a day (or a handful of times at most), so there is no benefit to releasing
    // the cluster lock early once the run completes - holding it for the full lockAtMostFor window
    // is what actually guards against a second scheduler instance/trigger picking up the same run
    // moments later. A short lockAtLeastFor previously let a second trigger re-acquire the lock (and
    // re-send the batch) seconds to over a minute after the first run finished.
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(5);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(5);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final AgingEmailConfigRepository configRepo;
    private final SlaNotificationService slaNotificationService;
    private final LockProvider lockProvider;

    // map configId -> ScheduledFuture
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();


    
  @Autowired
public AgingEmailSchedulerService(ThreadPoolTaskScheduler taskScheduler,
                                  AgingEmailConfigRepository configRepo,
                                  SlaNotificationService slaNotificationService,
                                  LockProvider lockProvider) {
    this.taskScheduler = taskScheduler;
    this.configRepo = configRepo;
    this.slaNotificationService = slaNotificationService;
    this.lockProvider = lockProvider;
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
    if (cfg == null || cfg.getId() == null) return;

    final Long configId = cfg.getId();

    // cancel any existing schedule
    cancelScheduled(configId);

    if (!cfg.isEnabled()) {
        logger.info("Config {} (id={}) is disabled; skipping schedule", cfg.getJobName(), configId);
        return;
    }

    if (!CronUtils.isValidCron(cfg.getCronExpression())) {
        logger.warn("Invalid cron expression for config id={} cron={}", configId, cfg.getCronExpression());
        return;
    }

    ZoneId zone = ZoneId.of(
            Optional.ofNullable(cfg.getTimezone()).filter(t -> !t.isBlank()).orElse("UTC")
    );

    CronTrigger cronTrigger = new CronTrigger(cfg.getCronExpression(), zone);

   Runnable task = () -> {
    // per-config lock name so each config has its own cluster lock
    String lockName = "aging-email-config-" + configId;

    LockConfiguration lockConfig = new LockConfiguration(
            lockName,
            java.time.Instant.now().plus(LOCK_AT_MOST_FOR),
            java.time.Instant.now().plus(LOCK_AT_LEAST_FOR)
    );

    Optional<SimpleLock> lock = Optional.empty();
    try {
        lock = lockProvider.lock(lockConfig);
        if (lock.isEmpty()) {
            logger.info("Could not acquire cluster lock for configId={} (instance={}) - skipping this run", configId, INSTANCE_ID);
            return;
        }

        // ---- BEGIN existing scheduled job logic ----
        AgingEmailConfig freshCfg = null;
        try {
            // Reload entity from DB every execution
            freshCfg = configRepo.findById(configId).orElse(null);
            if (freshCfg == null) {
                logger.info("Config id={} no longer exists. Cancelling scheduled task.", configId);
                cancelScheduled(configId);
                return;
            }

            freshCfg.setLastRunTime(LocalDateTime.now(zone));
            configRepo.save(freshCfg);

            String targetType = Optional.ofNullable(freshCfg.getTargetType())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .orElse("approver");

            logger.info("Executing scheduled config id={} jobName='{}' targetType='{}' (instance={})",
                    freshCfg.getId(), freshCfg.getJobName(), targetType, INSTANCE_ID);

            Map<String, Object> filters = new HashMap<>();
            if (freshCfg.getDepartment() != null && !freshCfg.getDepartment().isBlank() && !"ALL".equalsIgnoreCase(freshCfg.getDepartment().trim())) {
                List<String> depts = freshCfg.getDepartmentsList();
                if (depts != null && !depts.isEmpty()) {
                    filters.put("department", depts);
                    filters.put("departmentName", depts.get(0));
                }
            }
            if (freshCfg.getUserAging() != null) {
                filters.put("userAging", freshCfg.getUserAging());
                filters.put("minUserAging", freshCfg.getUserAging());
            }
            if (freshCfg.getCc() != null && !freshCfg.getCc().isBlank()) {
                filters.put("cc", freshCfg.getCc());
            }
            if (freshCfg.getBcc() != null && !freshCfg.getBcc().isBlank()) {
                filters.put("bcc", freshCfg.getBcc());
            }

            if ("approver".equals(targetType)) {
                slaNotificationService.runStage1RemindersWithFilters(filters);
            } else if ("manager".equals(targetType)) {
                slaNotificationService.runStage2EscalationsWithFilters(filters);
            } else {
                logger.warn("Unknown targetType='{}' for config id={}", targetType, configId);
            }

        } catch (Throwable t) {
            logger.error("Error while running scheduled job id=" + configId, t);
        } finally {
            try {
                if (freshCfg == null) return;
                Date next = cronTrigger.nextExecutionTime(new SimpleTriggerContextWrapper());
                freshCfg.setNextRunTime(next != null ? LocalDateTime.ofInstant(next.toInstant(), zone) : null);
                freshCfg.setUpdatedAt(LocalDateTime.now(zone));
                configRepo.save(freshCfg);
            } catch (Exception e) {
                logger.warn("Failed to compute nextRunTime for config id={}: {}", configId, e.getMessage());
            }
        }
        // ---- END existing scheduled job logic ----

    } finally {
        if (lock.isPresent()) {
            try {
                lock.get().unlock();
            } catch (Exception e) {
                logger.warn("Failed to release lock {}: {}", lockName, e.getMessage());
            }
        }
    }
};

    ScheduledFuture<?> future = taskScheduler.schedule(task, cronTrigger);
    scheduledTasks.put(configId, future);

    // persist initial nextRunTime safely
    try {
        Date next = cronTrigger.nextExecutionTime(new SimpleTriggerContextWrapper());
        cfg.setNextRunTime(
                next != null
                        ? LocalDateTime.ofInstant(next.toInstant(), zone)
                        : null
        );

        if (persistMetadata) {
            cfg.setUpdatedAt(LocalDateTime.now(zone));
            configRepo.save(cfg);
        }

    } catch (Exception e) {
        logger.warn("Failed to compute initial nextRunTime for config id={}", configId, e);
    }

    logger.info(
            "Scheduled config id={} jobName='{}' targetType='{}' cron={} tz={}",
            configId,
            cfg.getJobName(),
            Optional.ofNullable(cfg.getTargetType()).map(String::trim).map(String::toLowerCase).orElse("approver"),
            cfg.getCronExpression(),
            zone
    );
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

                if (cfg.getCc() != null && !cfg.getCc().isBlank()) {
                    filters.put("cc", cfg.getCc());
                }
                if (cfg.getBcc() != null && !cfg.getBcc().isBlank()) {
                    filters.put("bcc", cfg.getBcc());
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