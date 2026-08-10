package com.zain.almksazain.services;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zain.almksazain.model.ExportJob;
import com.zain.almksazain.repo.ExportJobRepository;

// Removes finished/failed export jobs (file + DB row) once they're old enough that nobody is
// still going to download them, and resolves jobs that got stuck RUNNING/PENDING (e.g. because
// the app was redeployed mid-export) so they don't sit reporting "in progress" forever.
//
// No distributed lock here (unlike AgingEmailSchedulerService): deleting an already-deleted DB
// row is a no-op, and file deletion is per-instance local disk, so a second instance running
// this at the same time is harmless - there's no non-idempotent side effect like sending an
// email to protect against.
@Service
public class ExportJobCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ExportJobCleanupService.class);

    private static final List<String> FINISHED_STATUSES =
            Arrays.asList(ExportJob.STATUS_DONE, ExportJob.STATUS_FAILED);
    private static final List<String> UNFINISHED_STATUSES =
            Arrays.asList(ExportJob.STATUS_PENDING, ExportJob.STATUS_RUNNING);

    @Value("${app.export.retention-hours:48}")
    private int retentionHours;

    @Value("${app.export.stuck-threshold-hours:2}")
    private int stuckThresholdHours;

    private final ExportJobRepository exportJobRepository;

    public ExportJobCleanupService(ExportJobRepository exportJobRepository) {
        this.exportJobRepository = exportJobRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExportJobs() {
        resolveStuckJobs();
        removeExpiredFinishedJobs();
    }

    private void resolveStuckJobs() {
        LocalDateTime stuckCutoff = LocalDateTime.now().minusHours(stuckThresholdHours);
        List<ExportJob> stuck = exportJobRepository.findByStatusInAndCreatedAtBefore(UNFINISHED_STATUSES, stuckCutoff);
        for (ExportJob job : stuck) {
            logger.warn("Export job {} has been {} since {} - marking FAILED (stuck/orphaned)",
                    job.getJobId(), job.getStatus(), job.getCreatedAt());
            job.setStatus(ExportJob.STATUS_FAILED);
            job.setErrorMessage("Export did not complete within the expected time and was marked failed by cleanup.");
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
        }
        if (!stuck.isEmpty()) {
            logger.info("Resolved {} stuck export job(s)", stuck.size());
        }
    }

    private void removeExpiredFinishedJobs() {
        LocalDateTime expiryCutoff = LocalDateTime.now().minusHours(retentionHours);
        List<ExportJob> expired = exportJobRepository.findByStatusInAndCompletedAtBefore(FINISHED_STATUSES, expiryCutoff);
        int deletedFiles = 0;
        for (ExportJob job : expired) {
            if (job.getFilePath() != null) {
                File file = new File(job.getFilePath());
                if (file.exists() && file.delete()) {
                    deletedFiles++;
                } else if (file.exists()) {
                    logger.warn("Could not delete export file for job {}: {}", job.getJobId(), job.getFilePath());
                }
            }
        }
        if (!expired.isEmpty()) {
            exportJobRepository.deleteAll(expired);
            logger.info("Removed {} expired export job(s) ({} file(s) deleted)", expired.size(), deletedFiles);
        }
    }
}
