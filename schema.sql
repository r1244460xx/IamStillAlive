-- ==========================================
-- IMSA (單身人士健在監測應用) 核心資料庫 Schema
-- 適用資料庫：PostgreSQL 13+
-- ==========================================

-- 1. 啟用 UUID 產生器的擴充套件 (PostgreSQL 預設提供)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. 建立使用者資料表 (users)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(50) NOT NULL UNIQUE,                       -- 電話號碼 (登入主憑證，必填、唯一)
    email VARCHAR(255) NULL,                                 -- 電子信箱 (可選)
    national_id VARCHAR(50) NULL,                            -- 身分證字號 (可選，防重複註冊)
    emergency_contact_phone VARCHAR(50) NULL,                -- 緊急聯絡電話 (可選，手機格式)
    password_hash VARCHAR(255) NOT NULL,                     -- 雜湊加密密碼
    nickname VARCHAR(100) NOT NULL,                          -- 暱稱
    gender VARCHAR(20) NOT NULL,                             -- 性別 (MALE, FEMALE, OTHER)
    birthdate DATE NOT NULL,                                 -- 生日 (用以判定年齡)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 帳號創立時間
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 帳號更新時間
    status VARCHAR(30) NOT NULL,                             -- 帳號狀態 (ACTIVE, PENDING, SUSPENDED)
    safety_status VARCHAR(30) NOT NULL DEFAULT 'SAFE',       -- 安全狀態 (SAFE, ALERTED)
    last_active_at TIMESTAMP NULL                            -- 最後活躍/打卡時間 (冗餘欄位優化排程查詢)
);

-- 3. 建立使用者活躍登入紀錄表 (login_records)
CREATE TABLE IF NOT EXISTS login_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                                   -- 關聯的使用者 ID (外鍵)
    login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 登入/活躍打卡時間
    location VARCHAR(255) NULL,                              -- 手機上傳地點 (可選)
    ip_address VARCHAR(45) NULL,                             -- 登入 IP 來源 (可選)
    device_info VARCHAR(255) NULL,                           -- 手機型號與系統版本 (可選)
    network_type VARCHAR(50) NULL,                           -- 連線方式 Wi-Fi/5G 等 (可選)
    remark VARCHAR(255) NULL,                                -- 自訂備註 (可選)
    
    -- 設定外鍵關聯，當使用者帳號被刪除時，其相關活躍紀錄會一併刪除 (Cascade)
    CONSTRAINT fk_login_records_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- 4. 建立索引 (Index) 優化查詢效能
-- 排程器專用複合索引 (快速撈出超時未打卡之用戶)
CREATE INDEX IF NOT EXISTS idx_users_safety_check ON users(status, safety_status, last_active_at);

-- 打卡歷史紀錄索引
CREATE INDEX IF NOT EXISTS idx_login_records_user_id ON login_records(user_id);
CREATE INDEX IF NOT EXISTS idx_login_records_login_time ON login_records(login_time DESC);
