package com.imsa.backend.entity.enums;

public enum SafetyStatus {
    SAFE,       // 正常 / 安全狀態 (有按時打卡)
    ALERTED     // 已觸發過警報 (超過 24 小時未打卡，已通知緊急聯絡人)
}
