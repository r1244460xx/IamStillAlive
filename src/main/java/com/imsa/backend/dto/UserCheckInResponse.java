package com.imsa.backend.dto;

import com.imsa.backend.entity.enums.SafetyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCheckInResponse {
    private UUID userId;
    private UUID loginRecordId;
    private LocalDateTime checkInTime;
    private SafetyStatus safetyStatus;
    private LocalDateTime nextCheckInDeadline;
    private String message;
}
