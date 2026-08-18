package com.zain.almksazain.services;

import java.io.File;
import java.util.LinkedHashMap;
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

/**
 * EmailService with improved diagnostic logging and endpoint preflight checks.
 */
@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // Deduplication tracking to prevent duplicate emails within 2-minute windows
    private final Set<String> recentEmailHashes = ConcurrentHashMap.newKeySet();

    @Value("${email.multipart.endpoint:}")
    private String MULTIPART_EMAIL_ENDPOINT;

    @Value("${email.json.endpoint:}")
    private String JSON_EMAIL_ENDPOINT;

    private final RestTemplate restTemplate;

    public EmailService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async
    public void sendEmail(String to, String subject, String message, List<String> attachments,
                          String department, String userName, String role, Integer requestCount, String cc, String bcc) {
        String emailHash = generateEmailHash(to, subject, message, department, userName, role, cc, bcc);
        logger.debug("Computed emailHash={} for to={} subject={} dept={} user={} role={} requestCount={} cc={} bcc={}",
                emailHash, to, subject, department, userName, role, requestCount, cc, bcc);


        if (!recentEmailHashes.add(emailHash)) {
            logger.warn("DUPLICATE_EMAIL_PREVENTED: to={}, subject={}, dept={}, user={}, role={}, requestCount={}, cc={}, bcc={}",
                    to, subject, department, userName, role, requestCount, cc, bcc);
            return;
        }

        // Validate endpoints AFTER adding hash to prevent retry storms on misconfiguration
        boolean wantsMultipart = attachments != null && !attachments.isEmpty();
        if (wantsMultipart) {
            if (MULTIPART_EMAIL_ENDPOINT == null || MULTIPART_EMAIL_ENDPOINT.isBlank()) {
                logger.error("Aborting email send: multipart attachments provided but MULTIPART_EMAIL_ENDPOINT is not configured. to={} subject={} attachments={}",
                        to, subject, attachments == null ? 0 : attachments.size());
                return;
            }
        } else {
            if (JSON_EMAIL_ENDPOINT == null || JSON_EMAIL_ENDPOINT.isBlank()) {
                logger.error("Aborting email send: no multipart attachments and JSON_EMAIL_ENDPOINT is not configured. to={} subject={}", to, subject);
                return;
            }
        }

        try {
            String emailId = generateEmailId();

            // Log payload summary for diagnostics (avoid printing very large bodies)
            logger.info("Sending emailId={} to={} cc={} bcc={} subject='{}' dept={} user={} role={} attachments={}",
                    emailId, to, cc, bcc, subject, department, userName, role, wantsMultipart ? attachments.size() : 0);

            if (wantsMultipart) {
                sendMultipartEmail(to, subject, message, attachments, emailId, department, userName, role, requestCount, cc, bcc);
            } else {
                sendJsonEmail(to, subject, message, emailId, department, userName, role, requestCount, cc, bcc);
            }

        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        } finally {
            CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES)
                    .execute(() -> recentEmailHashes.remove(emailHash));
        }
    }

    private String generateEmailHash(String to, String subject, String message,
                                     String department, String userName, String role, String cc, String bcc) {
        long timeWindow = System.currentTimeMillis() / (2 * 60 * 1000);
        return String.valueOf(Objects.hash(to, subject, message, department, userName, role, cc, bcc, timeWindow));
    }

    private String generateEmailId() {
        return "EMAIL_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }

    private void sendMultipartEmail(String to, String subject, String message, List<String> filePaths,
                                    String emailId, String department, String userName, String role, Integer requestCount, String cc, String bcc) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("to", to);
            requestBody.add("subject", subject);
            // multipart previously sent full message; keep that behavior
            requestBody.add("message", message);

            if (department != null) requestBody.add("department", department);
            if (userName != null) requestBody.add("userName", userName);
            if (role != null) requestBody.add("role", role);
            if (requestCount != null) requestBody.add("requestCount", String.valueOf(requestCount));
            if (cc != null) requestBody.add("cc", cc);
            if (bcc != null) requestBody.add("bcc", bcc);

            for (String filePath : filePaths) {
                File file = new File(filePath);
                if (file.exists() && file.length() > 0) {
                    requestBody.add("attachments", new FileSystemResource(file));
                } else {
                    logger.warn("Attachment not found or empty: {}", filePath);
                }
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            logger.debug("POST {} payload keys={}", MULTIPART_EMAIL_ENDPOINT, requestBody.keySet());
            ResponseEntity<String> response = restTemplate.postForEntity(MULTIPART_EMAIL_ENDPOINT, requestEntity, String.class);
            logger.info("Multipart email sent: status={} body={}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            logger.error("Failed to send multipart email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    private void sendJsonEmail(String to, String subject, String message, String emailId,
                               String department, String userName, String role, Integer requestCount, String cc, String bcc) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // prepare a short preview for logs only
            String preview = message == null ? "" : (message.length() > 200 ? message.substring(0,200) + "..." : message);
            logger.debug("Email message preview length={} preview='{}'", message == null ? 0 : message.length(), preview);

            // Put the full message into the payload (important: previously we put truncated preview here by mistake)
            Map<String, String> requestBody = new LinkedHashMap<>();
            requestBody.put("to", to);
            requestBody.put("subject", subject);
            requestBody.put("message", message == null ? "" : message); // send FULL HTML body

            if (department != null) requestBody.put("department", department);
            if (userName != null) requestBody.put("userName", userName);
            if (role != null) requestBody.put("role", role);
            if (requestCount != null) requestBody.put("requestCount", String.valueOf(requestCount));
            if (cc != null) requestBody.put("cc", cc);
            if (bcc != null) requestBody.put("bcc", bcc);

            logger.debug("POST {} payload keys={}", JSON_EMAIL_ENDPOINT, requestBody.keySet());
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(JSON_EMAIL_ENDPOINT, requestEntity, String.class);

            logger.info("JSON email sent: status={} body={}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            logger.error("Failed to send JSON email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }
}