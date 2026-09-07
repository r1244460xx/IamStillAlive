# IMSA Android 健在守護 App

IMSA Android 是為單身獨居人士量身打造的守護客戶端應用，採用現代化 Android 架構（Jetpack Compose + Material3 + MVVM + 雙軌背景守護），能在使用者完全無感、無需手動開啟 App 的情況下，透過日常螢幕解鎖與背景排程自動向守護中心報平安。

---

## 1. 技術棧 (Tech Stack)

* **開發語言**：Kotlin 2.0.21
* **UI 框架**：Jetpack Compose (Material3)
* **架構模式**：MVVM (Model-View-ViewModel) + StateFlow
* **網路連線**：Retrofit 2 + OkHttp 3 (Gson 序列化)
* **背景排程**：AndroidX WorkManager
* **系統級守護**：Android AccessibilityService (無障礙服務架構)
* **資料持久化**：SharedPreferences (`SessionManager`)
* **目標版本**：Min SDK 26 (Android 8.0) / Target SDK 35 (Android 15)

---

## 2. 核心功能與架構設計

> **核心業務守護規則 (重要需求記錄)**：
> * **無手動打卡負擔**：不需要使用者透過大圓按鈕主動進 App 打卡。
> * **無固定週期心跳**：不需要背後運行固定時長（如 12 小時）定時主動打卡。
> * **唯一打卡時機**：無論使用者是否使用密碼解鎖螢幕，**只要螢幕被解鎖點亮進入使用狀態，系統就必須嘗試執行一次打卡報平安**。

### 2.1 系統級無障礙常駐守護進程 (`SafetyGuardianAccessibilityService`)

為落實「使用者日常完全無感、不需開 App、不需手動打卡、移除任務卡片依舊生效」的核心目標：
* **系統直接綁定**：由 Android 系統伺服器（`system_server`）直接綁定，進程狀態為 `PROC_STATE_PERSISTENT`。
* **抗殺進程 (Survive Task Kill)**：使用者手動從 Recent Tasks 滑掉 App 卡片後，守護進程依舊持續在系統底層運作。
* **解鎖自動打卡 (唯一核心觸發源)**：動態監聽 `ACTION_USER_PRESENT`（密碼/圖形解鎖）與 `ACTION_SCREEN_ON`（無密碼點亮解鎖），使用者每次解鎖手機時自動向後端打卡報平安。
* **15 秒防洗版節流**：15,000ms 內頻繁開關螢幕自動過濾，不造成伺服器負擔與流量消耗。
* **零通知欄干擾**：完全不佔用通知欄（無 Ongoing Notification 卡片），不影響使用者觀感。
* **連線發送輔助 (`SafetyCheckInWorker`)**：由守護進程派發單次背景任務處理連線與打卡請求。原 12 小時定時排程已標記為非核心/已棄用。

### 2.2 電池最佳化豁免 (`GuardianPermissionHelper`)
* 宣告 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
* App 首頁提供一鍵跳轉白名單授權，防止手機進入 Doze 深度睡眠時被 OEM 系統凍結。

### 2.3 預警提醒卡片機制 (`HeartbeatSyncManager` & `PreAlertNotificationReceiver`)
* **11 小時平安預警鬧鐘**：登入或每次手動解鎖成功後，使用 `AlarmManager.setExactAndAllowWhileIdle` 預約 11 小時後的精準喚醒鬧鐘。
* **解鎖無條件推延**：只要使用者手動解鎖（`ACTION_USER_PRESENT`），必定 100% 立即向後推遲預警鬧鐘並消除卡片（純本地作業，不受 15 秒網路打卡節流阻擋）。
* **被動點亮防誤判**：處於鎖定狀態時（`isKeyguardLocked == true`），螢幕被動點亮絕不誤判為解鎖，使提醒卡片穩定保留在鎖定螢幕上。
* **1 小時最後緩衝期**：手機端於第 11 小時在鎖定螢幕彈出高優先級通知卡片，提醒使用者確認平安；若持續未解鎖，後端將於第 12 小時正式向緊急聯絡人發送緊急通報。

---

## 3. 目錄結構

```text
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml        # 權限、元件與無障礙服務宣告
│   │   ├── res/                       # UI 字串與 XML 設定
│   │   └── java/com/imsa/app/
│   │       ├── MainActivity.kt        # 單一 Activity 主入口
│   │       ├── data/                  # API 客戶端 (ApiService) 與 Models
│   │       ├── service/               # 背景守護 (SafetyGuardianAccessibilityService)
│   │       ├── ui/                    # Compose UI 畫面 (HomeScreen, RegisterScreen)
│   │       ├── util/                  # 權限檢測輔助 (GuardianPermissionHelper)
│   │       └── worker/                # WorkManager 任務 (SafetyCheckInWorker)
│   └── build.gradle.kts
├── gradle/libs.versions.toml          # 依賴版本控制
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 4. 本地開發與建置

### 4.1 在 Android Studio 中開啟
直接使用 Android Studio 開啟本專案的 `IMSA/android` 資料夾即可，Gradle 會自動解析並同步依賴。

### 4.2 後端連線設定
Android 模擬器默認透過 `http://10.0.2.2:8080/` 訪問本機電腦的 Spring Boot 後端服務。相關連線定義於 `com.imsa.app.data.ApiService.kt`。

### 4.3 終端機編譯指令
```bash
cd android
./gradlew assembleDebug
```

---

## 5. ADB 測試指令

* **測試螢幕鎖定與解鎖觸發打卡**：
  ```bash
  # 關閉螢幕
  adb shell input keyevent 26
  # 喚醒並解鎖螢幕
  adb shell input keyevent 224 && adb shell input keyevent 82
  ```

* **檢視守護打卡日誌**：
  ```bash
  adb logcat -s SafetyGuardianAcc:I HeartbeatSyncMgr:I GuardianAudit:D
  ```

* **一鍵查詢守護狀態與審計軌跡 (Audit Log Dump)**：
  ```bash
  adb shell am broadcast -n com.imsa.app/.receiver.GuardianDebugReceiver -a com.imsa.app.action.DUMP_STATUS
  ```
  *(將手機目前預警鬧鐘排程、最近 30 筆解鎖動作與推延歷史、伺服器心跳結果完整傾印於終端機)*

* **清空手機端審計日誌**：
  ```bash
  adb shell am broadcast -n com.imsa.app/.receiver.GuardianDebugReceiver -a com.imsa.app.action.DUMP_STATUS --ez clear true
  ```

---

## 6. 審計軌跡報告 (Diagnostics Report) 解讀指引

執行 `DUMP_STATUS` 指令後，終端機會由新到舊輸出審計軌跡，格式解讀如下：

### 6.1 事件類型與圖示代碼表

| 圖示與事件代碼 | 代表含意 | 正常行為判讀 |
| :--- | :--- | :--- |
| 📱 `UNLOCK_DETECTED` | 系統無障礙守護進程偵測到手機解鎖 | 標註廣播動作（如 `USER_PRESENT`）與鎖屏狀態（`鎖屏=false` 代表真解鎖）。 |
| ⏰ `ALARM_SCHEDULED` | `AlarmManager` 預警鬧鐘向後推延 | **關鍵指標**！每次解鎖後必須出現，目標時間應精準等於「解鎖時間 + 11 小時」。 |
| 🔕 `CARD_DISMISSED` | 消除鎖定螢幕上的預警卡片 | 解鎖當下即時觸發，確保卡片不會滯留在畫面上。 |
| 💚 `CHECK_IN_SUCCESS` | 心跳 API 成功送達後端伺服器 | 附帶後端回傳之安全截止期（Server Deadline = 當下時間 + 12 小時）。 |
| ⏱️ `API_THROTTLED` | 15 秒網路防抖生效 | 短時間頻繁開關螢幕時出現。**注意：此時鬧鐘已被成功推延，僅略過 HTTP 請求**。 |
| 🔔 `PRE_ALERT_TRIGGERED` | 11 小時無解鎖，預警鬧鐘時間抵達 | 點亮螢幕並彈出卡片。若使用者隨後解鎖，將接續觸發消除與推延。 |
| ❌ `CHECK_IN_FAILED` / `CHECK_IN_ERROR` | 網路不通或伺服器異常 | 進入離線斷線保護狀態，心跳自動留置於本地暫存（`getPendingCheckIn`）待自動補傳。 |

### 6.2 標準健康解鎖時序範例 (由舊到新讀取)
每次您解鎖手機時，報告中應呈現如下標準鏈路：
```text
#03 [13:31:10.990] 📱 UNLOCK_DETECTED    | 廣播=USER_PRESENT, 鎖屏=false, 觸發推延鬧鐘與消除卡片
#02 [13:31:11.008] ⏰ ALARM_SCHEDULED    | 鬧鐘設定於 11 小時 後觸發 (目標: 2026-09-08 00:31:11)
#01 [13:31:12.351] 💚 CHECK_IN_SUCCESS   | 📱 [解鎖即時] 成功上傳心跳 (伺服器截止期: 2026-09-08T01:31:11)
```
- 說明：`13:31:10` 解鎖手機 ➡️ 本地鬧鐘瞬間推延至隔日 `00:31:11`（+11hr）➡️ 網路心跳上傳成功，後端截止期展延至隔日 `01:31:11`（+12hr）。兩者皆完整推進，守護安全無虞。
