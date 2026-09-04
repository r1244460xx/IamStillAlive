package com.imsa.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LoginRecordCreateRequest {

    @NotNull(message = "使用者ID不能為空")
    private UUID userId;

    private String location;

    private String ipAddress;

    private String deviceInfo;

    private String networkType;

    private String remark;
}
