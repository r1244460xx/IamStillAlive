package com.imsa.backend.dto;

import lombok.Data;

@Data
public class UserCheckInRequest {
    private String phone;
    private String location;
    private String ipAddress;
    private String deviceInfo;
    private String networkType;
    private String remark;
}
