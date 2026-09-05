package com.imsa.backend.controller;

import com.imsa.backend.dto.LoginRecordCreateRequest;
import com.imsa.backend.dto.LoginRecordResponse;
import com.imsa.backend.service.LoginRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/login-records")
@RequiredArgsConstructor
public class LoginRecordController {

    private final LoginRecordService loginRecordService;

    @PostMapping
    public ResponseEntity<LoginRecordResponse> createRecord(@Valid @RequestBody LoginRecordCreateRequest request) {
        LoginRecordResponse response = loginRecordService.createRecord(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

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

    @PutMapping("/{id}")
    public ResponseEntity<LoginRecordResponse> updateRecord(
            @PathVariable UUID id, 
            @Valid @RequestBody LoginRecordCreateRequest request) {
        LoginRecordResponse response = loginRecordService.updateRecord(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        loginRecordService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }
}
