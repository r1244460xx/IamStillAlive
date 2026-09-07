package com.imsa.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.imsa.app.service.SafetyGuardianAccessibilityService

object GuardianPermissionHelper {

    /**
     * 檢查 IMSA 無障礙守護服務是否已開啟
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${SafetyGuardianAccessibilityService::class.java.canonicalName}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 開啟系統「無障礙設定」頁面
     * 優先精準跳轉至「已下載的應用程式 / 下載的服務」清單，
     * 讓使用者直接看見 IMSA 健在守護，省去在「通用/視覺/聽覺/肢體」等分頁與眾多項目中尋找的時間。
     */
    fun openAccessibilitySettings(context: Context) {
        val intents = listOf(
            // 1. 小米 HyperOS / MIUI 及原生 Android「下載的應用程式 (InstalledAccessibilityService)」頁面
            Intent().apply {
                component = android.content.ComponentName("com.android.settings", "com.android.settings.SubSettings")
                putExtra(":settings:show_fragment", "com.android.settings.accessibility.InstalledAccessibilityService")
                putExtra(":settings:show_fragment_title", "下載的應用程式")
            },
            // 2. 三星 One UI 專屬「已安裝的服務」頁面
            Intent().apply {
                component = android.content.ComponentName("com.android.settings", "com.android.settings.SubSettings")
                putExtra(":settings:show_fragment", "com.samsung.android.settings.accessibility.InstalledServicesPreferenceFragment")
                putExtra(":settings:show_fragment_title", "已安裝的服務")
            },
            // 3. 原生 AOSP 無障礙頂層設定
            Intent().apply {
                component = android.content.ComponentName("com.android.settings", "com.android.settings.SubSettings")
                putExtra(":settings:show_fragment", "com.android.settings.accessibility.AccessibilitySettings")
            },
            // 4. 系統標準通用 Action
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // 繼續嘗試下一個
            }
        }

        // 終極 Fallback
        try {
            val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            // 忽略
        }
    }

    /**
     * 檢查是否已豁免電池最佳化（Doze 白名單）
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * 請求使用者豁免電池最佳化
     * 若系統支援直接彈窗（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）則先嘗試彈窗，
     * 若拋出例外則直接開啟應用程式詳細資訊頁面。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * 開啟應用程式用電/電池最佳化設定頁
     * 優先直接跳至小米 HyperOS / MIUI 專屬「省電策略」頁面（可直接勾選「無限制」），
     * 若為其他品牌或呼叫失敗，則依序嘗試各廠牌電池設定、IMSA 應用程式資訊 (App Info)，
     * 避免跳至全系統所有 App 的浩瀚清單造成困惑。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intents = listOf(
            // 1. 小米 HyperOS / 新版 MIUI 專屬「省電策略 (電量詳情)」頁面（直接選擇「無限制」）
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.legacypowerrank.PowerDetailActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("title", context.applicationInfo.loadLabel(context.packageManager).toString())
            },
            // 2. 小米 舊版 MIUI 神隱模式 / 省電策略
            Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            },
            // 3. 華為 / 榮耀 電池管理 / 耗電詳情
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                )
            },
            // 4. 通用 Android：IMSA 應用程式詳細資訊頁面 (App Info)
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // 繼續嘗試下一個
            }
        }

        // 終極 Fallback：系統通用電池最佳化設定清單
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 忽略
        }
    }

    /**
     * 檢查應用是否具備自啟動權限
     * 支援小米 MIUI / HyperOS (OP_AUTO_START = 10008)，
     * 若為不支援或非定制 ROM 則回傳 null（由 UI 顯示為「前往確認」）。
     */
    fun isAutoStartEnabled(context: Context): Boolean? {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return null
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(appOps, 10008, android.os.Process.myUid(), context.packageName) as Int
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 開啟系統「自啟動」或「後台啟動」管理頁面
     * 依序嘗試各主流廠牌（小米 HyperOS/MIUI、華為、OPPO、vivo、三星）專屬的自啟動管理 Activity，
     * 若均無效則 Fallback 至 IMSA 應用程式詳細資訊頁面 (App Details)。
     */
    fun openAutoStartSettings(context: Context) {
        val intents = listOf(
            // 小米 HyperOS / MIUI 自啟動管理頁
            Intent("miui.intent.action.OP_AUTO_START"),
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            // 小米 應用權限管理（後台彈出介面 / 後台啟動）
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            },
            // 華為 / 榮耀 (EMUI / HarmonyOS) 自啟動
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            },
            // OPPO / Realme (ColorOS) 自啟動
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            },
            // vivo (OriginOS / FuntouchOS) 自啟動
            Intent().apply {
                component = android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            // 三星 (Samsung Smart Manager)
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
        )

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // 繼續嘗試下一個
            }
        }

        // 終極 Fallback：開啟該 App 的應用程式詳細資訊頁面
        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
        } catch (e: Exception) {
            // 忽略
        }
    }
}
