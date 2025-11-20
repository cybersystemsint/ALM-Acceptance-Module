package com.zain.almksazain.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zain.almksazain.services.SlaNotificationService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SlaAdminController {

    @Autowired
    private SlaNotificationService slaNotificationService;

    // Note: secure this endpoint (e.g., @PreAuthorize("hasRole('ADMIN')")) in production
    @PostMapping("/stage1")
    public ResponseEntity<?> triggerStage1(@RequestBody(required = false) Map<String, Object> filters) {
        slaNotificationService.runStage1RemindersWithFilters(filters == null ? Collections.emptyMap() : filters);
        return ResponseEntity.ok(Map.of("status", "triggered", "stage", 1));
    }

    // Convenient endpoint to trigger stage1 for a single recordNo
    @PostMapping("/stage1/record/{recordNo}")
    public ResponseEntity<?> triggerStage1ForRecord(@PathVariable String recordNo) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("recordNo", recordNo);
        slaNotificationService.runStage1RemindersWithFilters(filters);
        return ResponseEntity.ok(Map.of("status", "triggered", "stage", 1, "recordNo", recordNo));
    }

    @PostMapping("/stage2")
    public ResponseEntity<?> triggerStage2(@RequestBody(required = false) Map<String, Object> filters) {
        slaNotificationService.runStage2EscalationsWithFilters(filters == null ? Collections.emptyMap() : filters);
        return ResponseEntity.ok(Map.of("status", "triggered", "stage", 2));
    }

    @PostMapping("/stage2/record/{recordNo}")
    public ResponseEntity<?> triggerStage2ForRecord(@PathVariable String recordNo) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("recordNo", recordNo);
        slaNotificationService.runStage2EscalationsWithFilters(filters);
        return ResponseEntity.ok(Map.of("status", "triggered", "stage", 2, "recordNo", recordNo));
    }
}