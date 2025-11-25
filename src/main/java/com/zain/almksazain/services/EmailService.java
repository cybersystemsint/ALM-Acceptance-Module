package com.zain.almksazain.services;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
        sendEmail(to, subject, message, attachments, null, null, null, null);
    }

    @Async
    public void sendEmail(String to, String subject, String message, List<String> attachments,
                          String department, String userName, String role) {
        sendEmail(to, subject, message, attachments, department, userName, role, null);
    }

    /**
     * New overload that accepts an optional requestCount. Backwards compatible overloads call into this method.
     */
    @Async
    public void sendEmail(String to, String subject, String message, List<String> attachments,
                          String department, String userName, String role, Integer requestCount) {
        String emailHash = generateEmailHash(to, subject, message, department, userName, role);
        logger.debug("Computed emailHash={} for to={} subject={} dept={} user={} role={} requestCount={}",
                emailHash, to, subject, department, userName, role, requestCount);

        if (JSON_EMAIL_ENDPOINT == null || JSON_EMAIL_ENDPOINT.isBlank()) {
            logger.warn("JSON_EMAIL_ENDPOINT is not configured (value is null/blank)");
        } else {
            logger.debug("JSON_EMAIL_ENDPOINT={}", JSON_EMAIL_ENDPOINT);
        }
        if (MULTIPART_EMAIL_ENDPOINT == null || MULTIPART_EMAIL_ENDPOINT.isBlank()) {
            logger.debug("MULTIPART_EMAIL_ENDPOINT is not configured or blank");
        } else {
            logger.debug("MULTIPART_EMAIL_ENDPOINT={}", MULTIPART_EMAIL_ENDPOINT);
        }

        // Prevent duplicate emails within 2-minute window
        if (recentEmailHashes.contains(emailHash)) {
            logger.warn("DUPLICATE_EMAIL_PREVENTED: to={}, subject={}, dept={}, user={}, role={}, requestCount={}",
                    to, subject, department, userName, role, requestCount);
            return;
        }

        recentEmailHashes.add(emailHash);

        try {
            String emailId = generateEmailId();

            if (attachments != null && !attachments.isEmpty()) {
                sendMultipartEmail(to, subject, message, attachments, emailId, department, userName, role, requestCount);
            } else {
                sendJsonEmail(to, subject, message, emailId, department, userName, role, requestCount);
            }

        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        } finally {
            // Clean up hash after 2 minutes to prevent memory leak
            CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES)
                    .execute(() -> recentEmailHashes.remove(emailHash));
        }
    }

    private String generateEmailHash(String to, String subject, String message,
                                     String department, String userName, String role) {
        // Create hash with 2-minute time window to prevent duplicates
        long timeWindow = System.currentTimeMillis() / (2 * 60 * 1000);
        return String.valueOf(Objects.hash(to, subject, message, department, userName, role, timeWindow));
    }

    private String generateEmailId() {
        return "EMAIL_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    private void sendMultipartEmail(String to, String subject, String message, List<String> filePaths,
                                    String emailId, String department, String userName, String role, Integer requestCount) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("to", to);
            requestBody.add("subject", subject);
            requestBody.add("message", message);

            // add metadata fields
            if (department != null) requestBody.add("department", department);
            if (userName != null) requestBody.add("userName", userName);
            if (role != null) requestBody.add("role", role);
            if (requestCount != null) requestBody.add("requestCount", String.valueOf(requestCount));

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

    private void sendJsonEmail(String to, String subject, String message, String emailId,
                               String department, String userName, String role, Integer requestCount) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("to", to);
            requestBody.put("subject", subject);
            requestBody.put("message", message);

            // add metadata
            if (department != null) requestBody.put("department", department);
            if (userName != null) requestBody.put("userName", userName);
            if (role != null) requestBody.put("role", role);
            if (requestCount != null) requestBody.put("requestCount", String.valueOf(requestCount));

            logger.info("Sending email to endpoint: {}", JSON_EMAIL_ENDPOINT);
            logger.debug("Email payload: to={}, subject={}, message={}, department={}, userName={}, role={}, requestCount={}",
                    to, subject, message, department, userName, role, requestCount);

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