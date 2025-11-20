package com.zain.almksazain.services;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // Deduplication tracking to prevent duplicate emails within 2-minute windows
    private final Set<String> recentEmailHashes = ConcurrentHashMap.newKeySet();

    @Value("${email.multipart.endpoint}")
    private String MULTIPART_EMAIL_ENDPOINT;

    @Value("${email.json.endpoint}")
    private String JSON_EMAIL_ENDPOINT;

    private final RestTemplate restTemplate;

    public EmailService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async
    public void sendEmail(String to, String subject, String message, List<String> attachments) {
        String emailHash = generateEmailHash(to, subject, message);

        // Prevent duplicate emails within 2-minute window
        if (recentEmailHashes.contains(emailHash)) {
            logger.warn("DUPLICATE_EMAIL_PREVENTED: to={}, subject={}", to, subject);
            return;
        }

        recentEmailHashes.add(emailHash);

        try {
            String emailId = generateEmailId();

            if (attachments != null && !attachments.isEmpty()) {
                sendMultipartEmail(to, subject, message, attachments, emailId);
            } else {
                sendJsonEmail(to, subject, message, emailId);
            }

        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        } finally {
            // Clean up hash after 2 minutes to prevent memory leak
            CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES)
                    .execute(() -> recentEmailHashes.remove(emailHash));
        }
    }

    private String generateEmailHash(String to, String subject, String message) {
        // Create hash with 2-minute time window to prevent duplicates
        long timeWindow = System.currentTimeMillis() / (2 * 60 * 1000);
        return String.valueOf(Objects.hash(to, subject, message, timeWindow));
    }

    private String generateEmailId() {
        return "EMAIL_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    private void sendMultipartEmail(String to, String subject, String message, List<String> filePaths, String emailId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("to", to);
            requestBody.add("subject", subject);
            requestBody.add("message", message);

            // Validate and attach files
            for (String filePath : filePaths) {
                File file = new File(filePath);
                if (file.exists() && file.length() > 0) {
                    requestBody.add("attachments", new FileSystemResource(file));
                } else {
                    logger.warn("File not found or empty: {}", filePath);
                }
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(MULTIPART_EMAIL_ENDPOINT, requestEntity, String.class);

            logger.info("Multipart email sent successfully: {}", response.getBody());

        } catch (Exception e) {
            logger.error("Failed to send multipart email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    private void sendJsonEmail(String to, String subject, String message, String emailId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("to", to);
            requestBody.put("subject", subject);
            requestBody.put("message", message);
          logger.info("Sending email to endpoint: {}", JSON_EMAIL_ENDPOINT);
logger.info("Email payload: to={}, subject={}, message={}", to, subject, message);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(JSON_EMAIL_ENDPOINT, requestEntity, String.class);
logger.info("Email response status: {}", response.getStatusCode());
            logger.info("JSON email sent successfully: {}", response.getBody());

        } catch (Exception e) {
            logger.error("Failed to send JSON email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }
}