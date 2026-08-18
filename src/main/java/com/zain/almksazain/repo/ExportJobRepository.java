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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists ExportJob status as a JSON sidecar file per job under {app.export.dir}/.jobs/,
 * instead of a row in the app's main MySQL database. The export service (server .69) is a
 * read-only replica synced from .67 - writing job bookkeeping there would require disabling
 * super_read_only instance-wide, risking the replication sync. Job files live entirely on
 * local disk instead, alongside the exported workbooks they describe.
 */
@Component
public class ExportJobRepository {

    private static final Logger logger = LogManager.getLogger(ExportJobRepository.class);

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
        try {
            return Optional.of(objectMapper.readValue(file, ExportJob.class));
        } catch (IOException ex) {
            logger.error("Failed to read export job {}", jobId, ex);
            return Optional.empty();
        }
    }
}
