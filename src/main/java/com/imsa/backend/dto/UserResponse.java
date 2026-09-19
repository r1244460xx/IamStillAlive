package com.imsa.backend.dto;

import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String phone;
    private String email;
    private String nationalId;
    @Builder.Default
    private List<EmergencyContactResponse> emergencyContacts = Collections.emptyList();
    private String nickname;
    private Gender gender;
    private LocalDate birthdate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UserStatus status;
    private SafetyStatus safetyStatus;
    private LocalDateTime lastActiveAt;

    public static UserResponse fromEntity(User user) {
        return fromEntity(user, Collections.emptyList());
    }

    public static UserResponse fromEntity(User user, List<EmergencyContactResponse> emergencyContacts) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .nationalId(user.getNationalId())
                .emergencyContacts(emergencyContacts != null ? emergencyContacts : Collections.emptyList())
                .nickname(user.getNickname())
                .gender(user.getGender())
                .birthdate(user.getBirthdate())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .status(user.getStatus())
                .safetyStatus(user.getSafetyStatus())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }
}
