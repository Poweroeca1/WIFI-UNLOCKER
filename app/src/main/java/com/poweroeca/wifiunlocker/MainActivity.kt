package com.poweroeca.wifiunlocker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var statusText: TextView
    private lateinit var resultsLayout: LinearLayout
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            showScanResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        buildUi()
        registerScanReceiver()
        statusText.text = "Készen áll a keresésre."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFF101114.toInt())
        }

        val title = TextView(this).apply {
            text = "WIFI UNLOCKER"
            textSize = 26f
            setTextColor(0xFFFFD400.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Közeli hálózatok keresése"
            textSize = 15f
            setTextColor(0xFFE6E6E6.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }
        root.addView(subtitle, matchWrap())

        val scanButton = Button(this).apply {
            text = "WI-FI KERESÉSE"
            setTextColor(0xFF101114.toInt())
            setBackgroundColor(0xFFFFD400.toInt())
            setOnClickListener { startWifiScan() }
        }
        root.addView(scanButton, matchWrap())

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFD400.toInt())
            setPadding(0, 16, 0, 12)
        }
        root.addView(statusText, matchWrap())

        val scroll = ScrollView(this)
        resultsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(resultsLayout, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val note = TextView(this).apply {
            text = "A jelszóval védett hálózatokhoz érvényes hozzáférés szükséges. Ez az alkalmazás nem töri fel a jelszavakat."
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 12, 0, 0)
        }
        root.addView(note, matchWrap())

        setContentView(root)
    }

    private fun startWifiScan() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        if (!isLocationEnabled()) {
            statusText.text = "Kapcsold be a helyszolgáltatást a Wi-Fi-kereséshez."
            Toast.makeText(this, "A helyszolgáltatás bekapcsolása szükséges lehet.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        try {
            if (!wifiManager.isWifiEnabled) {
                statusText.text = "Kapcsold be a Wi-Fi-t, majd próbáld újra."
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                return
            }

            statusText.text = "Hálózatok keresése…"
            val started = wifiManager.startScan()
            if (!started) {
                statusText.text = "A rendszer korlátozta a keresést. Korábbi eredmények betöltése…"
                showScanResults()
            }
        } catch (_: SecurityException) {
            statusText.text = "Hiányzó jogosultság. Engedélyezd a szükséges hozzáféréseket."
        } catch (e: Exception) {
            statusText.text = "A keresés nem indítható: ${e.localizedMessage ?: "ismeretlen hiba"}"
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearbyGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        return fineLocation && nearbyGranted
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasRequiredPermissions()) startWifiScan()
            else statusText.text = "A kereséshez engedélyezd a kért jogosultságokat."
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun showScanResults() {
        if (!hasRequiredPermissions()) return
        try {
            val results = wifiManager.scanResults
                .filter { it.SSID.isNotBlank() }
                .sortedByDescending { it.level }

            resultsLayout.removeAllViews()
            if (results.isEmpty()) {
                statusText.text = "Nem található hálózat. Próbáld újra, és ellenőrizd a Wi-Fi-t és a helyszolgáltatást."
                return
            }

            statusText.text = "Talált hálózatok: ${results.size}"
            results.forEach { result ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundColor(0xFF202126.toInt())
                }

                val name = TextView(this).apply {
                    text = result.SSID
                    textSize = 18f
                    setTextColor(0xFFFFD400.toInt())
                }
                card.addView(name, matchWrap())

                val security = result.capabilities.ifBlank { "Biztonsági adatok nem érhetők el" }
                val details = TextView(this).apply {
                    text = "Jelerősség: ${result.level} dBm  •  ${WifiManager.calculateSignalLevel(result.level, 5) + 1}/5\nFrekvencia: ${result.frequency} MHz\nBiztonság: $security"
                    textSize = 12f
                    setTextColor(0xFFE6E6E6.toInt())
                    setPadding(0, 6, 0, 0)
                }
                card.addView(details, matchWrap())

                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
                resultsLayout.addView(card, params)
            }
        } catch (_: SecurityException) {
            statusText.text = "Az Android nem engedélyezte a hálózati eredmények olvasását."
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(scanReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 4102
    }
}
