package com.imsa.backend.controller;

import com.imsa.backend.dto.*;
import com.imsa.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegisterRequest request) {
        UserResponse response = userService.registerUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody UserLoginRequest request) {
        UserResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id, 
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UserPasswordChangeRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok(Map.of("message", "密碼已成功變更"));
    }

    @PutMapping("/{id}/emergency-contact")
    public ResponseEntity<UserResponse> updateEmergencyContact(
            @PathVariable UUID id,
            @Valid @RequestBody UserEmergencyContactUpdateRequest request) {
        UserResponse response = userService.updateEmergencyContact(id, request.getEmergencyContactPhone());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<UserCheckInResponse> checkIn(
            @PathVariable UUID id,
            @RequestBody(required = false) UserCheckInRequest request) {
        UserCheckInResponse response = userService.checkIn(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/simulate-overdue")
    public ResponseEntity<UserResponse> simulateOverdue(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "25") long hours) {
        UserResponse response = userService.simulateOverdue(id, hours);
        return ResponseEntity.ok(response);
    }
}
