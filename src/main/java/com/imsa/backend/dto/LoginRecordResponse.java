package com.imsa.backend.dto;

import com.imsa.backend.entity.LoginRecord;
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
public class LoginRecordResponse {
    private UUID id;
    private UUID userId;
    private LocalDateTime loginTime;
    private String location;
    private String ipAddress;
    private String deviceInfo;
    private String networkType;
    private String remark;

    public static LoginRecordResponse fromEntity(LoginRecord record) {
        if (record == null) return null;
        return LoginRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUser().getId())
                .loginTime(record.getLoginTime())
                .location(record.getLocation())
                .ipAddress(record.getIpAddress())
                .deviceInfo(record.getDeviceInfo())
                .networkType(record.getNetworkType())
                .remark(record.getRemark())
                .build();
    }
}
