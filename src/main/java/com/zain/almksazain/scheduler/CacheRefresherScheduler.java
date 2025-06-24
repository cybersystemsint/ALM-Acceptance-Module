package com.zain.almksazain.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

@Service
public class CacheRefresherScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CacheRefresherScheduler.class);

    @PersistenceContext
    private EntityManager entityManager;
    /**
     * Refresh the dccPOCombinedCache table every 5 minutes.
     * Adjust the fixedRate value as needed (milliseconds).
     */
    @Transactional
    @Scheduled(fixedRate = 5 * 60 * 1000) // Every 5 minutes
    public void refreshDccPOCombinedCache() {
        logger.info("Starting refresh of dccPOCombinedCache at {}", java.time.LocalDateTime.now());
        entityManager.createNativeQuery("TRUNCATE TABLE dccPOCombinedCache").executeUpdate();
        int inserted = entityManager.createNativeQuery("INSERT INTO dccPOCombinedCache SELECT * FROM dccPOCombinedview").executeUpdate();
        logger.info("Finished refresh of dccPOCombinedCache at {}. Rows inserted: {}", java.time.LocalDateTime.now(), inserted);
    }
}
