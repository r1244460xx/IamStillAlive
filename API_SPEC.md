# IMSA (單身人士健在監測應用) - Entity 與 API 規格文件

本文件詳細記錄 IMSA 後端系統的核心資料實體（Entity）結構、列舉型態（Enum）定義，以及目前已實作之所有 RESTful API 規格。

---

## 1. 資料模型規格 (Entity Specifications)

### 1.1 使用者實體 (`User`)
*   **資料庫表名**：`users`
*   **主鍵**：`id` (UUID，由系統隨機產生)

| 欄位名稱 | Java 型態 | 資料庫型態 | 必填/選填 | 說明與業務邏輯 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | 必填 | 唯一識別碼，主鍵。 |
| `phone` | `String` | `VARCHAR(50)` | 必填、唯一 | 主要登入憑證，須符合 09 開頭之 10 碼手機號碼格式。 |
| `email` | `String` | `VARCHAR(255)` | 選填 (Nullable) | 電子信箱，可作為次要聯絡管道。 |
| `nationalId` | `String` | `VARCHAR(50)` | 選填 (Nullable) | 身分證字號，用於防止重複註冊與實名驗證。 |
| `emergencyContactPhone` | `String` | `VARCHAR(50)` | 選填 (Nullable) | 緊急聯絡人手機號碼，超時未打卡警報時通知之對象。格式同為 09 開頭 10 碼。 |
| `passwordHash` | `String` | `VARCHAR(255)` | 必填 | 使用 Spring Security `BCrypt` 雜湊加密後的密碼。 |
| `nickname` | `String` | `VARCHAR(100)` | 必填 | 使用者暱稱/稱呼。 |
| `gender` | `Gender` (Enum) | `VARCHAR(20)` | 必填 | 性別 (`MALE`, `FEMALE`, `OTHER`)。 |
| `birthdate` | `LocalDate` | `DATE` | 必填 | 生日，格式 `yyyy-MM-dd`。 |
| `createdAt` | `LocalDateTime` | `TIMESTAMP` | 系統產生 | 帳號建立時間（不可修改）。 |
| `updatedAt` | `LocalDateTime` | `TIMESTAMP` | 系統產生 | 帳號最後更新時間。 |
| `status` | `UserStatus` (Enum) | `VARCHAR(30)` | 必填 | 帳號狀態 (`ACTIVE`, `PENDING`, `SUSPENDED`)。 |
| `safetyStatus` | `SafetyStatus` (Enum) | `VARCHAR(30)` | 必填 (預設 `SAFE`) | 健在安全狀態 (`SAFE`, `ALERTED`)，用於防重複警報。 |
| `lastActiveAt` | `LocalDateTime` | `TIMESTAMP` | 系統維護 | 最後活躍/打卡時間（冗餘欄位，供排程器單次批次查詢）。 |

---

### 1.2 登入與活躍紀錄實體 (`LoginRecord`)
*   **資料庫表名**：`login_records`
*   **主鍵**：`id` (UUID，由系統隨機產生)

| 欄位名稱 | Java 型態 | 資料庫型態 | 必填/選填 | 說明與業務邏輯 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | 必填 | 紀錄唯一識別碼，主鍵。 |
| `user` | `User` | `UUID` (FK) | 必填 | 關聯之使用者實體，多對一關聯，級聯刪除 (Cascade)。 |
| `loginTime` | `LocalDateTime` | `TIMESTAMP` | 系統產生 | 打卡/登入時間戳（預設當下時間）。 |
| `location` | `String` | `VARCHAR(255)` | 選填 | GPS 經緯度座標或地理位置描述。 |
| `ipAddress` | `String` | `VARCHAR(45)` | 選填 | 使用者登入時連線之 IP 位址。 |
| `deviceInfo` | `String` | `VARCHAR(255)` | 選填 | 裝置型號與作業系統版本 (如 `Pixel 8 Pro (Android 15)`)。 |
| `networkType` | `String` | `VARCHAR(50)` | 選填 | 連線方式 (如 `Wi-Fi`, `5G`, `4G`)。 |
| `remark` | `String` | `VARCHAR(255)` | 選填 | 使用者自訂備註或系統自動標註 (如「註冊冷啟動」、「一鍵打卡」)。 |

---

### 1.3 列舉型態定義 (Enums)

#### `Gender` (`com.imsa.backend.entity.enums.Gender`)
*   `MALE`：男性
*   `FEMALE`：女性
*   `OTHER`：其他

#### `UserStatus` (`com.imsa.backend.entity.enums.UserStatus`)
*   `ACTIVE`：正常活躍中（系統會納入定時安全監測）
*   `PENDING`：待驗證/審核中
*   `SUSPENDED`：已停權/停用

#### `SafetyStatus` (`com.imsa.backend.entity.enums.SafetyStatus`)
*   `SAFE`：安全/正常（按時打卡中）
*   `ALERTED`：已觸發警報（超過 24 小時未打卡，且系統已送出警報通報）

---

## 2. API 規格與端點一覽 (RESTful APIs)

### 2.1 使用者模組 (`/api/users`)

#### ① 註冊新使用者 (含冷啟動打卡)
*   **方法/路徑**：`POST /api/users/register`
*   **功能**：註冊帳號。密碼以 BCrypt 加密。註冊成功時自動將 `safetyStatus` 設為 `SAFE`、`lastActiveAt` 設為當前時間，並**自動建立第一筆初始打卡紀錄**（冷啟動防呆）。
*   **請求主體 (Request Body)**：
    ```json
    {
      "phone": "0912345678",
      "password": "mySecurePassword123",
      "nickname": "阿明",
      "gender": "MALE",
      "birthdate": "1995-05-20",
      "email": "aming@example.com",
      "nationalId": "A123456789",
      "emergencyContactPhone": "0987654321"
    }
    ```
*   **成功回應 (`201 Created`)**：
    ```json
    {
      "id": "8f4b5d6e-1c2a-4f8e-90ab-123456789abc",
      "phone": "0912345678",
      "email": "aming@example.com",
      "nationalId": "A123456789",
      "emergencyContactPhone": "0987654321",
      "nickname": "阿明",
      "gender": "MALE",
      "birthdate": "1995-05-20",
      "createdAt": "2026-09-04T23:50:00",
      "updatedAt": "2026-09-04T23:50:00",
      "status": "ACTIVE",
      "safetyStatus": "SAFE",
      "lastActiveAt": "2026-09-04T23:50:00"
    }
    ```

#### ② 查詢使用者個人檔案
*   **方法/路徑**：`GET /api/users/{id}`
*   **成功回應 (`200 OK`)**：同上述 `UserResponse` 格式。

#### ③ 更新使用者個人檔案
*   **方法/路徑**：`PUT /api/users/{id}`
*   **說明**：電話與密碼不可由本端點修改。
*   **請求主體 (Request Body)**：
    ```json
    {
      "nickname": "阿明 (已更新)",
      "gender": "MALE",
      "birthdate": "1995-05-20",
      "email": "new_email@example.com",
      "nationalId": "A123456789",
      "emergencyContactPhone": "0988776655"
    }
    ```
*   **成功回應 (`200 OK`)**：回傳更新後的 `UserResponse`。

#### ④ 語意化「一鍵打卡報平安」
*   **方法/路徑**：`POST /api/users/{id}/check-in`
*   **說明**：手機 App 開啟或點擊「報平安」專用端點。後端自動更新 `lastActiveAt`、新增打卡紀錄、將警報狀態重置為 `SAFE`，並精準計算下次截止時間。
*   **請求主體 (Request Body，所有欄位皆選填)**：
    ```json
    {
      "location": "25.0339, 121.5645 (台北市信義區)",
      "ipAddress": "192.168.1.105",
      "deviceInfo": "Pixel 8 Pro (Android 15)",
      "networkType": "Wi-Fi",
      "remark": "早安！今天身體狀況良好"
    }
    ```
*   **成功回應 (`200 OK`)**：
    ```json
    {
      "userId": "8f4b5d6e-1c2a-4f8e-90ab-123456789abc",
      "loginRecordId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "checkInTime": "2026-09-05T08:00:00",
      "safetyStatus": "SAFE",
      "nextCheckInDeadline": "2026-09-06T08:00:00",
      "message": "打卡成功！已為您更新安全狀態，祝您平安順心。"
    }
    ```

---

### 2.2 活躍與打卡紀錄模組 (`/api/login-records`)

#### ① 新增登入/打卡紀錄
*   **方法/路徑**：`POST /api/login-records`
*   **請求主體 (Request Body)**：
    ```json
    {
      "userId": "8f4b5d6e-1c2a-4f8e-90ab-123456789abc",
      "location": "台北市信義區",
      "ipAddress": "192.168.1.105",
      "deviceInfo": "Pixel 8 Pro",
      "networkType": "5G",
      "remark": "外出打卡"
    }
    ```
*   **成功回應 (`201 Created`)**：回傳包含 `id`、`loginTime` 等欄位之 `LoginRecordResponse`。

#### ② 查詢單筆登入紀錄
*   **方法/路徑**：`GET /api/login-records/{id}`
*   **成功回應 (`200 OK`)**。

#### ③ 查詢特定使用者的所有活躍歷史紀錄
*   **方法/路徑**：`GET /api/login-records/user/{userId}`
*   **說明**：按時間由新到舊 (`loginTime DESC`) 排序。
*   **成功回應 (`200 OK`)**：回傳 `List<LoginRecordResponse>`。

#### ④ 修改特定登入紀錄備註/地點
*   **方法/路徑**：`PUT /api/login-records/{id}`
*   **成功回應 (`200 OK`)**。

#### ⑤ 刪除特定登入紀錄
*   **方法/路徑**：`DELETE /api/login-records/{id}`
*   **成功回應 (`204 No Content`)**。

---

## 3. 定時監測與防重複瘋狂報警機制

### 3.1 排程器 (`UserSafetyMonitorScheduler`)
*   **頻率**：預設每 60 秒（`fixedRate = 60000`）觸發一次。
*   **觸發方法**：呼叫 `UserSafetyService.checkActiveUsersSafety()`。

### 3.2 高效單次批次查詢
排程器直接調用：
`userRepository.findByStatusAndSafetyStatusAndLastActiveAtBefore(ACTIVE, SAFE, now - 24小時)`
*   **效能保證**：透過資料庫複合索引 `idx_users_safety_check(status, safety_status, last_active_at)`，直接獲取逾期用戶，徹底解決 N+1 遍歷問題。

### 3.3 安全狀態機 (`SafetyStatus`) 防重複邏輯
```
     [新註冊 / 正常打卡]
              ↓
           ( SAFE )  ←────────────────────────┐
              │                               │ 使用者再次完成打卡
              │ 超時 ≥ 24 小時                 │ (自動重置為 SAFE)
              ↓                               │
       觸發警報 (印出 Log)                      │
              ↓                               │
          ( ALERTED ) ────────────────────────┘
              │
              ↓
```
1. **第一次逾期**：發送警報 Log（若有設定緊急電話則印出通知緊急聯絡人），並將狀態改為 `ALERTED`。
2. **持續逾期**：排程器再次掃描時，因為狀態為 `ALERTED`，直接略過通報，防止瘋狂洗版。
3. **重新打卡**：使用者呼叫一鍵打卡 (`check-in`) 或建立新紀錄時，系統自動將狀態重置回 `SAFE`。

---

## 4. Android 用戶端架構與核心守護規格 (Android Client Specifications)

> [!IMPORTANT]
> **核心業務守護規則 (重要需求記錄)**：
> 依據產品核心定位，用戶端**不需要使用者手動點擊（如首頁大圓形按鈕）來主動打卡**，也**不需要在背後設定固定間隔（如 12 小時）定時自動打卡**。
> **對 Android App 而言，唯一需要且必須嘗試執行打卡報平安的時機，就是「使用者無論是否使用密碼解鎖螢幕」的當下。**
> 只要使用者有日常使用手機的動作（點亮解鎖），系統便自動在背景完成活躍證明；若整天完全沒有解鎖手機，則視為潛在異常並依後端超時機制通報。

### 4.1 前後端 API 規格對接與覆蓋率矩陣

| API 端點 (依據章節 2) | HTTP 方法 | Android 接口方法 (`ImsaApiService`) | Android 端對接狀態與調用時機 |
| :--- | :--- | :--- | :--- |
| `/api/users/register` | `POST` | `register(request)` | ✅ **已對接**：註冊畫面填寫表單完成後呼叫，成功後即時儲存 Session 並啟動守護。 |
| `/api/users/{id}` | `GET` | `getUserProfile(id)` | ✅ **已對接**：進入首頁或手動重新整理時同步用戶最新資訊與狀態。 |
| `/api/users/{id}` | `PUT` | `updateUserProfile(id, request)` | ✅ **介面已就緒**：資料層支援修改個人檔案與緊急聯絡人。 |
| `/api/users/{id}/check-in` | `POST` | `checkIn(id, request)` | ✅ **核心打卡端點**：由螢幕解鎖守護服務自動調用（手動打卡僅為除錯保留）。 |
| `/api/login-records/user/{userId}` | `GET` | `getLoginRecords(userId)` | ✅ **已對接**：首頁「近期打卡紀錄」列表資料來源，按時間倒序顯示最近打卡。 |
| `/api/login-records/{id}` | `DELETE` | `deleteLoginRecord(id)` | ✅ **介面已就緒**：底層登入紀錄單筆刪除功能。 |
| `/api/login-records` (通用 CRUD) | `POST`/`PUT`/`GET` | *(由語意端點替代)* | 由專屬的 `/check-in` 語意端點封裝，自動更新 `lastActiveAt` 並重置為 `SAFE`。 |

---

### 4.2 核心解鎖守護機制 (`SafetyGuardianAccessibilityService`)

為落實「使用者日常完全無感、不需開 App、不需手動打卡、移除任務卡片依舊生效」的核心目標：

#### 系統級常駐無障礙守護進程
* **系統綁定機制**：註冊於 Android OS 無障礙服務架構（`android.permission.BIND_ACCESSIBILITY_SERVICE`），由 Android 系統伺服器（`system_server`）直接持有並管理 Service 生命週期。
* **抗殺進程 (Survive Task Kill)**：當使用者手動將主 App 從 Recent Tasks 卡片向上滑動銷毀時，系統僅銷毀 Activity 視圖，無障礙守護進程維持 `PROC_STATE_PERSISTENT` 常駐狀態，完全不受影響。
* **螢幕解鎖動態監聽 (唯一觸發源)**：動態註冊廣播接收器監聽系統級意圖：
  * `Intent.ACTION_USER_PRESENT`：有鎖定密碼/圖形時，使用者解鎖進入手機觸發。
  * `Intent.ACTION_SCREEN_ON` (搭配 `!keyguardManager.isKeyguardLocked`)：無設密碼時，點亮螢幕即觸發。
* **15 秒防洗版節流器 (Debounce Window)**：
  在廣播接收處設置 `15,000ms` 冷卻時間。若使用者頻繁開關螢幕（例如看時間、連續鎖屏解鎖），15 秒內的重疊事件直接攔截，絕不濫發打卡請求或浪費網路流量。
* **零通知欄干擾 (Zero Notification Footprint)**：
  捨棄傳統 Foreground Service 必須強行常駐的紙片卡片通知，通知欄完全乾淨，不干擾使用者日常視覺觀感。
* **輔助發送器 (`SafetyCheckInWorker`)**：
  由守護進程以單次任務（OneTimeWorkRequest）調度派發，確保在網路受約束時由系統安全執行連線打卡。原 12 小時定時排程已標記為非核心/已棄用。

---

### 4.3 電池最佳化豁免與權限指引 (`GuardianPermissionHelper`)

* **權限宣告**：`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
* **首頁狀態卡片**：
  * 「解鎖心跳守護 (無通知常駐)」：即時檢測無障礙是否啟用，未啟用時提供「前往開啟 ➔」按鈕直達系統無障礙設定頁。
  * 「電池最佳化豁免 (抗殺進程)」：即時檢測是否已加入 Doze 白名單，未豁免時提供「設定豁免 ➔」彈窗一鍵授權，防止手機深度休眠時進程被 OEM 系統凍結。

---

### 4.4 用戶端本地資料持久化 (`SessionManager`)

Android 端使用加密/私有 `SharedPreferences` 保存用戶狀態：
* `user_id`：UUID 唯一識別碼。
* `phone`、`nickname`、`emergency_contact`：用戶基本資料。
* `safety_status`：當前安全狀態 (`SAFE` / `ALERTED`)。
* `next_deadline`：下次打卡截止時間戳（ISO-8601 格式）。

