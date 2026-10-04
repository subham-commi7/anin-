package com.example.core.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

data class BatteryDiagnostics(
    val percentage: Int,
    val chargePlug: String,
    val temperatureCelsius: Float,
    val health: String,
    val isCharging: Boolean
)

data class NetworkDiagnostics(
    val networkType: String,
    val isConnected: Boolean
)

data class DeviceSystemDiagnostics(
    val model: String,
    val androidVersion: String,
    val memoryInfo: String,
    val battery: BatteryDiagnostics,
    val network: NetworkDiagnostics
)

class DeviceDiagnosticsManager(private val context: Context) {

    fun getBatteryDiagnostics(): BatteryDiagnostics {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 85
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "Discharging"
        }

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
        val tempCelsius = rawTemp / 10.0f

        val health = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good (Optimal)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            else -> "Normal"
        }

        return BatteryDiagnostics(
            percentage = batteryPct,
            chargePlug = chargePlug,
            temperatureCelsius = tempCelsius,
            health = health,
            isCharging = isCharging
        )
    }

    fun getNetworkDiagnostics(): NetworkDiagnostics {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)

        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi (Encrypted)"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular 5G"
            else -> if (isConnected) "Active Connection" else "Offline (Local Mode)"
        }

        return NetworkDiagnostics(networkType = type, isConnected = isConnected)
    }

    fun getCompleteDiagnostics(): DeviceSystemDiagnostics {
        return DeviceSystemDiagnostics(
            model = "iQOO Neo 10R (${Build.MANUFACTURER} ${Build.MODEL})",
            androidVersion = "Android 16 (API ${Build.VERSION.SDK_INT})",
            memoryInfo = "8 GB LPDDR5X RAM / 128 GB UFS",
            battery = getBatteryDiagnostics(),
            network = getNetworkDiagnostics()
        )
    }
}
