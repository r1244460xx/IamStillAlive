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
  adb logcat -s SafetyGuardianAcc:I SafetyCheckInWorker:I
  ```
