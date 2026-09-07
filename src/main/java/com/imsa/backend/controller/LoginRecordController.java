package com.imsa.backend.controller;

import com.imsa.backend.dto.LoginRecordResponse;
import com.imsa.backend.service.LoginRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/login-records")
@RequiredArgsConstructor
public class LoginRecordController {

    private final LoginRecordService loginRecordService;

    @GetMapping("/{id}")
    public ResponseEntity<LoginRecordResponse> getRecordById(@PathVariable UUID id) {
        LoginRecordResponse response = loginRecordService.getRecordById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LoginRecordResponse>> getRecordsByUserId(
            @PathVariable UUID userId,
            @RequestParam(required = false) String phone) {
        List<LoginRecordResponse> responses = loginRecordService.getRecordsByUserId(userId, phone);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        loginRecordService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }
}
