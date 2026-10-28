package com.poweroeca.wifiunlocker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var summaryText: TextView
    private lateinit var resultsLayout: LinearLayout
    private var receiverRegistered = false
    private var openNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastResultsCount = 0

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (!updated) statusText.text = "A rendszer régebbi keresési adatokat adott vissza."
            showScanResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        buildUi()
        registerScanReceiver()
        refreshCurrentStatus()
        // Load any cached results so the list is useful immediately.
        if (hasRequiredPermissions()) showScanResults()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
            setBackgroundColor(0xFF0D0F14.toInt())
        }

        val title = TextView(this).apply {
            text = "WIFI UNLOCKER"
            textSize = 27f
            setTextColor(0xFFFFD400.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "WI-FI SCANNER  •  HÁLÓZATI ASSZISZTENS"
            textSize = 11f
            setTextColor(0xFF9DA3B4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 18)
        }
        root.addView(subtitle, matchWrap())

        val currentCard = panel()
        val currentTitle = label("KAPCSOLAT ÁLLAPOTA", 11f, 0xFF9DA3B4.toInt())
        currentCard.addView(currentTitle, matchWrap())
        summaryText = label("Wi-Fi állapotának ellenőrzése…", 16f, 0xFFFFFFFF.toInt())
        summaryText.setPadding(0, 5, 0, 0)
        currentCard.addView(summaryText, matchWrap())
        root.addView(currentCard, withBottomMargin())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val scanButton = Button(this).apply {
            text = "⌕  KERESÉS"
            setTextColor(0xFF101114.toInt())
            setBackgroundColor(0xFFFFD400.toInt())
            setOnClickListener { startWifiScan() }
        }
        actions.addView(scanButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(6)
        })
        val settingsButton = Button(this).apply {
            text = "WI-FI BEÁLLÍTÁSOK"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF303442.toInt())
            setOnClickListener { openWifiSettings() }
        }
        actions.addView(settingsButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(6)
        })
        root.addView(actions, withBottomMargin())

        statusText = label("Készen áll a keresésre.", 13f, 0xFFFFD400.toInt())
        statusText.setPadding(2, 4, 2, 10)
        root.addView(statusText, matchWrap())

        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listHeader.addView(label("KÖZELI HÁLÓZATOK", 14f, 0xFFFFFFFF.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
        val countText = label("ÉLŐ LISTA", 10f, 0xFF9DA3B4.toInt())
        listHeader.addView(countText, wrapWrap())
        root.addView(listHeader, withBottomMargin())

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

        val footer = TextView(this).apply {
            text = "A nyílt hálózatokhoz kérhetsz csatlakozást. Védett hálózatnál a telefon rendszerfelülete és a helyes jelszó szükséges. Jelszótörést nem végzünk."
            textSize = 11f
            setTextColor(0xFF858B9A.toInt())
            setPadding(0, 12, 0, 0)
        }
        root.addView(footer, matchWrap())
        setContentView(root)
    }

    private fun startWifiScan() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }
        if (!isLocationEnabled()) {
            statusText.text = "A hálózatlista megjelenítéséhez kapcsold be a helyszolgáltatást."
            Toast.makeText(this, "Kapcsold be a helyszolgáltatást, majd keresd újra a hálózatokat.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        try {
            if (!wifiManager.isWifiEnabled) {
                statusText.text = "A Wi-Fi ki van kapcsolva. Kapcsold be a beállításokban."
                openWifiSettings()
                return
            }
            statusText.text = "Keresés folyamatban…"
            val started = wifiManager.startScan()
            if (!started) {
                statusText.text = "A rendszer korlátozta az új keresést; a legutóbbi elérhető listát mutatom."
                showScanResults()
            }
        } catch (_: SecurityException) {
            statusText.text = "Nincs engedély a Wi-Fi-adatok olvasásához. Ellenőrizd az alkalmazás engedélyeit."
        } catch (e: Exception) {
            statusText.text = "A keresés nem indítható: ${e.localizedMessage ?: "ismeretlen hiba"}"
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        return fine && nearby
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
            else statusText.text = "A Wi-Fi-listához engedélyezd a hely- és közeli eszközök hozzáférését."
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled
        else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
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
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }

            resultsLayout.removeAllViews()
            lastResultsCount = results.size
            if (results.isEmpty()) {
                statusText.text = "Még nincs megjeleníthető hálózat. Kapcsold be a Wi-Fi-t és a helyszolgáltatást, majd nyomd meg a KERESÉS gombot."
                val empty = panel()
                empty.addView(label("Nem található hálózat", 17f, 0xFFFFFFFF.toInt()), matchWrap())
                empty.addView(label("Az Android időnként korlátozza a keresések gyakoriságát. Próbáld meg újra néhány másodperc múlva.", 12f, 0xFFB8BECC.toInt()), withTopMargin(8))
                resultsLayout.addView(empty, withBottomMargin())
                return
            }
            statusText.text = "Kész: $lastResultsCount hálózat található a közelben."
            results.forEach { result ->
                val card = panel()
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val name = label(result.SSID, 17f, 0xFFFFD400.toInt())
                name.typeface = android.graphics.Typeface.DEFAULT_BOLD
                top.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(label("${WifiManager.calculateSignalLevel(result.level, 5) + 1}/5", 13f, signalColor(result.level)), wrapWrap())
                card.addView(top, matchWrap())

                val isOpen = result.capabilities.isBlank() ||
                    (!result.capabilities.contains("WEP") &&
                     !result.capabilities.contains("PSK") &&
                     !result.capabilities.contains("EAP") &&
                     !result.capabilities.contains("SAE") &&
                     !result.capabilities.contains("OWE") &&
                     !result.capabilities.contains("WAPI"))
                val security = when {
                    result.capabilities.contains("SAE") -> "WPA3"
                    result.capabilities.contains("EAP") -> "WPA/WPA2-Enterprise"
                    result.capabilities.contains("PSK") -> "WPA/WPA2-Personal"
                    result.capabilities.contains("WEP") -> "WEP (elavult, nem biztonságos)"
                    result.capabilities.contains("OWE") -> "Enhanced Open"
                    isOpen -> "Nyílt hálózat"
                    else -> "Védett hálózat"
                }
                val channel = frequencyToChannel(result.frequency)
                val details = label(
                    "$security  •  ${result.level} dBm\n${result.frequency} MHz  •  csatorna $channel\n${result.BSSID ?: "BSSID nem érhető el"}",
                    12f, 0xFFD5D9E3.toInt()
                )
                details.setPadding(0, dp(7), 0, dp(10))
                card.addView(details, matchWrap())

                val connectButton = Button(this).apply {
                    text = if (isOpen) "CSATLAKOZÁS NYÍLT HÁLÓZATHOZ" else "CSATLAKOZÁS A RENDSZEREN KERESZTÜL"
                    setTextColor(if (isOpen) 0xFF101114.toInt() else 0xFFFFFFFF.toInt())
                    setBackgroundColor(if (isOpen) 0xFFFFD400.toInt() else 0xFF353A49.toInt())
                    setOnClickListener {
                        if (isOpen) requestOpenNetwork(result.SSID)
                        else {
                            Toast.makeText(this@MainActivity, "A védett hálózathoz a helyes jelszó szükséges.", Toast.LENGTH_LONG).show()
                            openWifiSettings()
                        }
                    }
                }
                card.addView(connectButton, matchWrap())
                resultsLayout.addView(card, withBottomMargin())
            }
        } catch (_: SecurityException) {
            statusText.text = "Az Android nem engedélyezte a hálózati eredmények olvasását. Ellenőrizd az alkalmazás engedélyeit."
        } catch (e: Exception) {
            statusText.text = "A lista nem tölthető be: ${e.localizedMessage ?: "ismeretlen hiba"}"
        }
    }

    private fun requestOpenNetwork(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            openWifiSettings()
            return
        }
        try {
            openNetworkCallback?.let {
                try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            }
            statusText.text = "Csatlakozási kérés: $ssid"
            val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid).build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        statusText.text = "A telefon csatlakozott ehhez a hálózathoz: $ssid. Az Android megerősítést kérhetett."
                        Toast.makeText(this@MainActivity, "Wi-Fi-csatlakozás létrejött.", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onUnavailable() {
                    runOnUiThread {
                        statusText.text = "A csatlakozás nem sikerült vagy elutasítottad a rendszerkérést."
                        openWifiSettings()
                    }
                }
                override fun onLost(network: Network) {
                    runOnUiThread { statusText.text = "A Wi-Fi-kapcsolat megszakadt." }
                }
            }
            openNetworkCallback = callback
            connectivityManager.requestNetwork(request, callback, 30000)
            Toast.makeText(this, "Fogadd el az Android csatlakozási kérését, ha megjelenik.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            statusText.text = "A közvetlen csatlakozás nem érhető el: ${e.localizedMessage ?: "ismeretlen hiba"}"
            openWifiSettings()
        }
    }

    private fun refreshCurrentStatus() {
        try {
            if (!wifiManager.isWifiEnabled) {
                summaryText.text = "Wi-Fi kikapcsolva"
                return
            }
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")?.takeUnless { it == "<unknown ssid>" || it.isBlank() }
            summaryText.text = if (ssid != null) "Csatlakozva: $ssid" else "Wi-Fi bekapcsolva • nincs aktív kapcsolat"
        } catch (_: Exception) {
            summaryText.text = "Wi-Fi állapota nem olvasható"
        }
    }

    private fun openWifiSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun frequencyToChannel(frequency: Int): String = when {
        frequency in 2412..2472 -> ((frequency - 2407) / 5).toString()
        frequency == 2484 -> "14"
        frequency in 5000..5895 -> ((frequency - 5000) / 5).toString()
        frequency in 5955..7115 -> ((frequency - 5950) / 5).toString()
        else -> "?"
    }

    private fun signalColor(level: Int): Int = when {
        level >= -55 -> 0xFF65E6A5.toInt()
        level >= -70 -> 0xFFFFD400.toInt()
        else -> 0xFFFF8A65.toInt()
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        setBackgroundColor(0xFF1B1E27.toInt())
    }

    private fun label(textValue: String, size: Float, color: Int) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun wrapWrap() = LinearLayout.LayoutParams(-2, -2)
    private fun withBottomMargin() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun withTopMargin(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(scanReceiver) } catch (_: IllegalArgumentException) {}
        }
        openNetworkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 4102
    }
}
