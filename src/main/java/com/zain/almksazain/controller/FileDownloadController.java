package com.zain.almksazain.controller;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileDownloadController {

    private static final Logger logger = LogManager.getLogger(FileDownloadController.class);

    @Value("${alm.file.upload-dir:/home/almksa/POUPL/POUPL/}")
    private String uploadDir;

    private Path uploadRoot;

    @PostConstruct
    void init() {
        uploadRoot = Paths.get(normalizeDir(uploadDir)).normalize().toAbsolutePath();
        logger.info("File downloads configured from uploadDir={} (resolved={})", uploadDir, uploadRoot);
    }

    @GetMapping("/files-health")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> filesHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("uploadDir", uploadDir);
        health.put("resolvedUploadDir", uploadRoot.toString());
        health.put("uploadDirExists", Files.exists(uploadRoot));
        health.put("uploadDirReadable", Files.isReadable(uploadRoot));
        return health;
    }

    @GetMapping("/files/**")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request) {
        String fileName = extractFileName(request);
        if (isUnsafeFileName(fileName)) {
            logger.warn("Rejected unsafe file download request: {}", fileName);
            return ResponseEntity.badRequest().build();
        }

        Path filePath = uploadRoot.resolve(fileName).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            logger.warn("Path traversal blocked for fileName={}", fileName);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            logger.warn("File does not exist: fileName={}, resolvedPath={}, uploadDir={}",
                    fileName, filePath, uploadRoot);
            return ResponseEntity.notFound().build();
        }

        if (!Files.isRegularFile(filePath)) {
            logger.warn("Path is not a regular file: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        if (!Files.isReadable(filePath)) {
            logger.error("File exists but is not readable by the application process: {}", filePath);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = filePath.toFile();
        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    private String extractFileName(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/files/";
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix) || uri.length() <= prefix.length()) {
            return null;
        }
        String encoded = uri.substring(prefix.length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return "";
        }
        return dir.endsWith("/") ? dir : dir + "/";
    }

    private static boolean isUnsafeFileName(String fileName) {
        return fileName == null
                || fileName.isBlank()
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\");
    }
}
