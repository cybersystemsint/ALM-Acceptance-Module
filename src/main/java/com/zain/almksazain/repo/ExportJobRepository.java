package com.zain.almksazain.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zain.almksazain.model.ExportJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists ExportJob status as a JSON sidecar file per job under {app.export.dir}/.jobs/,
 * instead of a row in the app's main MySQL database. The export servers are read-only
 * replicas synced from the write-primary - writing job bookkeeping there would require
 * disabling super_read_only instance-wide, risking the replication sync. Job files live
 * entirely on local disk instead, alongside the exported workbooks they describe.
 */
@Component
public class ExportJobRepository {

    private static final Logger logger = LogManager.getLogger(ExportJobRepository.class);

    private static final FilenameFilter JSON_FILTER = (dir, name) -> name.endsWith(".json");

    @Value("${app.export.dir:/data/app/logs/ALM/Exports/}")
    private String exportDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private File jobsDir() {
        File dir = new File(exportDir, ".jobs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File jobFile(String jobId) {
        return new File(jobsDir(), jobId + ".json");
    }

    /** Writes to a temp file then atomically renames over the target, so a concurrent
     *  status read never observes a partially-written file. */
    public void save(ExportJob job) {
        File target = jobFile(job.getJobId());
        File tmp = new File(target.getParentFile(), target.getName() + "." + UUID.randomUUID() + ".tmp");
        try {
            objectMapper.writeValue(tmp, job);
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            tmp.delete();
            logger.error("Failed to persist export job {}", job.getJobId(), ex);
            throw new RuntimeException("Failed to persist export job " + job.getJobId(), ex);
        }
    }

    public Optional<ExportJob> findById(String jobId) {
        File file = jobFile(jobId);
        if (!file.exists()) {
            return Optional.empty();
        }
        return readJobFile(file);
    }

    public List<ExportJob> findByStatusInAndCreatedAtBefore(Collection<String> statuses, LocalDateTime cutoff) {
        List<ExportJob> result = new ArrayList<>();
        for (ExportJob job : readAllJobs()) {
            if (statuses.contains(job.getStatus())
                    && job.getCreatedAt() != null && job.getCreatedAt().isBefore(cutoff)) {
                result.add(job);
            }
        }
        return result;
    }

    public List<ExportJob> findByStatusInAndCompletedAtBefore(Collection<String> statuses, LocalDateTime cutoff) {
        List<ExportJob> result = new ArrayList<>();
        for (ExportJob job : readAllJobs()) {
            if (statuses.contains(job.getStatus())
                    && job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff)) {
                result.add(job);
            }
        }
        return result;
    }

    public void deleteAll(Collection<ExportJob> jobs) {
        for (ExportJob job : jobs) {
            File file = jobFile(job.getJobId());
            if (file.exists() && !file.delete()) {
                logger.warn("Could not delete export job file for {}: {}", job.getJobId(), file.getAbsolutePath());
            }
        }
    }

    private List<ExportJob> readAllJobs() {
        File[] files = jobsDir().listFiles(JSON_FILTER);
        List<ExportJob> jobs = new ArrayList<>();
        if (files == null) {
            return jobs;
        }
        for (File file : files) {
            readJobFile(file).ifPresent(jobs::add);
        }
        return jobs;
    }

    private Optional<ExportJob> readJobFile(File file) {
        try {
            return Optional.of(objectMapper.readValue(file, ExportJob.class));
        } catch (IOException ex) {
            logger.error("Failed to read export job file {}", file.getAbsolutePath(), ex);
            return Optional.empty();
        }
    }
}
