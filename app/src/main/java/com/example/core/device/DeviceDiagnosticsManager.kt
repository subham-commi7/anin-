package com.example.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

data class BatteryDiagnostics(
    val percentage: Int,
    val isCharging: Boolean,
    val chargePlug: String,
    val temperatureCelsius: Float,
    val health: String,
    val voltageMv: Int
)

data class NetworkDiagnostics(
    val isConnected: Boolean,
    val networkType: String,
    val isMetered: Boolean,
    val hasInternetCapability: Boolean
)

data class DeviceSystemDiagnostics(
    val battery: BatteryDiagnostics,
    val network: NetworkDiagnostics,
    val model: String = "iQOO Neo 10R (Snapdragon)",
    val androidVersion: String = "Android 16 (API ${Build.VERSION.SDK_INT})",
    val memoryInfo: String = "8 GB RAM / 128 GB Storage"
)

class DeviceDiagnosticsManager(private val context: Context) {

    fun getBatteryDiagnostics(): BatteryDiagnostics {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugStr = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_AC -> "Fast AC Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> if (isCharging) "Charging" else "Unplugged (Battery Power)"
        }

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 310
        val tempCelsius = tempTenths / 10.0f

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
        val healthStr = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Optimal (Good)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4100) ?: 4100

        return BatteryDiagnostics(
            percentage = batteryPct,
            isCharging = isCharging,
            chargePlug = plugStr,
            temperatureCelsius = tempCelsius,
            health = healthStr,
            voltageMv = voltage
        )
    }

    fun getNetworkDiagnostics(): NetworkDiagnostics {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkDiagnostics(false, "Unknown", false, false)

        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val typeStr = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "High-Speed Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "5G / Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline (Local Only)"
        }

        val isMetered = cm.isActiveNetworkMetered

        return NetworkDiagnostics(
            isConnected = isConnected,
            networkType = typeStr,
            isMetered = isMetered,
            hasInternetCapability = isConnected
        )
    }

    fun getCompleteDiagnostics(): DeviceSystemDiagnostics {
        return DeviceSystemDiagnostics(
            battery = getBatteryDiagnostics(),
            network = getNetworkDiagnostics()
        )
    }
}
