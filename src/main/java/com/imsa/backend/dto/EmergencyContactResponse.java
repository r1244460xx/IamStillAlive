package com.imsa.backend.dto;

import com.imsa.backend.entity.EmergencyContact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContactResponse {

    private UUID id;
    private UUID userId;
    private String name;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EmergencyContactResponse fromEntity(EmergencyContact contact) {
        if (contact == null) return null;
        return EmergencyContactResponse.builder()
                .id(contact.getId())
                .userId(contact.getUser() != null ? contact.getUser().getId() : null)
                .name(contact.getName())
                .phone(contact.getPhone())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }
}
