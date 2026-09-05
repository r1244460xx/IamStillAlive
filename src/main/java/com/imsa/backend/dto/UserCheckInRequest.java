package com.imsa.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCheckInRequest {
    private String phone;
    private String location;
    private String ipAddress;
    private String deviceInfo;
    private String networkType;
    private String remark;
    private LocalDateTime checkInTime;
}
