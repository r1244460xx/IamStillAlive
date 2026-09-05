package com.imsa.backend.entity.enums;

public enum SafetyStatus {
    SAFE,       // 正常 / 安全狀態 (有按時打卡)
    WARNING,    // 預警中 (超過 22 小時未打卡，已向本人發送預警，尚未通知緊急聯絡人)
    ALERTED     // 已觸發過警報 (超過 24 小時未打卡，已通知緊急聯絡人)
}
