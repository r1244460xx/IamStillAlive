package com.imsa.backend.controller;

import com.imsa.backend.dto.EmergencyContactRequest;
import com.imsa.backend.dto.EmergencyContactResponse;
import com.imsa.backend.service.EmergencyContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/emergency-contacts")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    @GetMapping
    public ResponseEntity<List<EmergencyContactResponse>> getContacts(@PathVariable UUID userId) {
        List<EmergencyContactResponse> contacts = emergencyContactService.getContacts(userId);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping
    public ResponseEntity<EmergencyContactResponse> addContact(
            @PathVariable UUID userId,
            @Valid @RequestBody EmergencyContactRequest request) {
        EmergencyContactResponse response = emergencyContactService.addContact(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{contactId}")
    public ResponseEntity<EmergencyContactResponse> updateContact(
            @PathVariable UUID userId,
            @PathVariable UUID contactId,
            @Valid @RequestBody EmergencyContactRequest request) {
        EmergencyContactResponse response = emergencyContactService.updateContact(userId, contactId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Map<String, String>> deleteContact(
            @PathVariable UUID userId,
            @PathVariable UUID contactId) {
        emergencyContactService.deleteContact(userId, contactId);
        return ResponseEntity.ok(Map.of("message", "緊急聯絡人已成功刪除"));
    }
}
