package com.imsa.backend.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AlertStatus {
    PENDING_SMS("準備發出簡訊"),
    SMS_SENT("告警簡訊已寄出"),
    SMS_FAILED("告警簡訊發送失敗"),
    NO_CONTACT_PHONE("未設定緊急聯絡人");

    private final String description;
}
