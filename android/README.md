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

### 2.1 雙軌背景守護系統 (Two-tier Background Heartbeat)

為確保使用者即使將 App 從多工任務列表（Recents）手動滑除殺掉，依然能持續受到保護，應用採用了雙軌背景機制：

1. **軌道一：WorkManager 週期性排程心跳 (`SafetyCheckInWorker`)**
   * 每 12 小時在背景靜默喚醒並向後端發送打卡心跳。
   * 具備網路連線約束條件 (`NetworkType.CONNECTED`)。

2. **軌道二：無障礙系統級常駐守護進程 (`SafetyGuardianAccessibilityService`)**
   * 由 Android 系統伺服器（`system_server`）直接綁定，進程狀態為 `PROC_STATE_PERSISTENT`。
   * **抗殺進程**：使用者手動滑掉 App 卡片後，守護進程依舊持續在系統底層運作。
   * **解鎖自動打卡**：動態監聽 `ACTION_USER_PRESENT` 與 `ACTION_SCREEN_ON`，使用者每次解鎖手機時自動向後端打卡報平安。
   * **15 秒防洗版節流**：15,000ms 內頻繁開關螢幕自動過濾，不造成伺服器負擔與流量消耗。
   * **零通知欄干擾**：完全不佔用通知欄（無 Ongoing Notification 卡片），不影響使用者觀感。

### 2.2 電池最佳化豁免 (`GuardianPermissionHelper`)
* 宣告 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
* App 首頁提供一鍵跳轉白名單授權，防止手機進入 Doze 深度睡眠時被 OEM 系統凍結。

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
