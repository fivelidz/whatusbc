package com.fivelidz.usbcid

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fivelidz.usbcid.ui.CableViewModel
import com.fivelidz.usbcid.ui.MainScreen
import com.fivelidz.usbcid.ui.OnboardingOverlay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: CableViewModel by viewModels()
    private val SHIZUKU_REQ = 4001

    private val permResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        vm.refreshBackend()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(permResult) }

        val prefs = getSharedPreferences("usbc", Context.MODE_PRIVATE)

        setContent {
            var showOnboarding by remember {
                mutableStateOf(!prefs.getBoolean("onboarded", false))
            }
            if (showOnboarding) {
                OnboardingOverlay(onFinish = {
                    prefs.edit().putBoolean("onboarded", true).apply()
                    showOnboarding = false
                })
                return@setContent
            }

            val assessment by vm.state.collectAsStateWithLifecycle()
            val verdict by vm.verdict.collectAsStateWithLifecycle()
            val wizard by vm.wizard.collectAsStateWithLifecycle()
            val backend by vm.backend.collectAsStateWithLifecycle()
            val plugEvent by vm.plugEvents.collectAsStateWithLifecycle()

            // Transient toast on connect/disconnect.
            LaunchedEffect(plugEvent) {
                when (plugEvent) {
                    CableViewModel.PlugEvent.CONNECTED ->
                        android.widget.Toast.makeText(this@MainActivity, "Cable connected", android.widget.Toast.LENGTH_SHORT).show()
                    CableViewModel.PlugEvent.DISCONNECTED ->
                        android.widget.Toast.makeText(this@MainActivity, "Cable disconnected", android.widget.Toast.LENGTH_SHORT).show()
                    null -> {}
                }
                if (plugEvent != null) vm.consumePlugEvent()
            }

            MainScreen(
                assessment = assessment,
                verdict = verdict,
                wizard = wizard,
                backend = backend,
                onRefresh = { vm.refreshBackend() },
                onRequestShizuku = { requestShizuku() },
                onWizard = { vm.updateWizard(it) },
            )
        }
    }

    private fun requestShizuku() {
        fun toast(msg: String) =
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

        val pinged = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!pinged) {
            // Either Shizuku app isn't installed, or its service isn't started.
            val installed = runCatching {
                packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
            }.getOrDefault(false)
            if (installed) {
                toast("Open the Shizuku app and tap Start (it must be running).")
                runCatching {
                    startActivity(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api"))
                }
            } else {
                toast("Shizuku isn't installed. Install the free Shizuku app, then start it.")
            }
            return
        }
        runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                toast("Shizuku connected — reading system data…")
                vm.refreshBackend()
            } else {
                Shizuku.requestPermission(SHIZUKU_REQ)
            }
        }.onFailure { toast("Couldn't talk to Shizuku: ${it.message}") }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshBackend()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permResult) }
        super.onDestroy()
    }
}
