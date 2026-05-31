package com.fivelidz.usbcid.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Provides shell-level command execution for reading sysfs nodes that are
 * blocked to a normal app by SELinux. Two backends:
 *   1. Shizuku (no root) -- runs in the ADB `shell` SELinux domain.
 *   2. su (root)         -- full access.
 *
 * On heavily-locked OEMs (e.g. Xiaomi HyperOS) even the shell domain is
 * denied many power_supply / typec nodes, so root may be required. The reader
 * degrades gracefully and the app keeps working on Tier-1 alone.
 *
 * All process execution is bounded by a timeout and drains both stdout and
 * stderr to avoid the classic pipe-buffer deadlock.
 */
object ShellAccess {

    enum class Backend { NONE, SHIZUKU, ROOT }

    private const val TIMEOUT_SECONDS = 8L

    /** Cached backend so we don't fork `su` on every read. Call [probeBackend] to refresh. */
    @Volatile
    private var cachedBackend: Backend? = null

    fun shizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shizukuPinged(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun rootAvailable(): Boolean = runCatching {
        runRoot("id")?.contains("uid=0") == true
    }.getOrDefault(false)

    /** Determine the best backend once and cache it. */
    fun probeBackend(): Backend {
        val b = when {
            rootAvailable() -> Backend.ROOT
            shizukuAvailable() -> Backend.SHIZUKU
            else -> Backend.NONE
        }
        cachedBackend = b
        return b
    }

    /** Cached backend, probing once if not yet known. */
    fun bestBackend(): Backend = cachedBackend ?: probeBackend()

    /** Run a command via the chosen backend, returning trimmed stdout or null. */
    fun run(cmd: String, backend: Backend = bestBackend()): String? = when (backend) {
        Backend.ROOT -> runRoot(cmd)
        Backend.SHIZUKU -> runShizuku(cmd)
        Backend.NONE -> null
    }

    private fun runRoot(cmd: String): String? = runCatching {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        drainWithTimeout(p)
    }.getOrNull()

    private fun runShizuku(cmd: String): String? = runCatching {
        // Shizuku.newProcess(String[], String[], String) is hidden (private) in
        // the published API surface, so we invoke it via reflection. If R8 ever
        // renames it the call fails gracefully and we fall back to Tier-1.
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
        val process = method.invoke(
            null, arrayOf("sh", "-c", cmd), null, null
        ) as Process
        drainWithTimeout(process)
    }.getOrNull()

    /**
     * Read stdout (draining stderr concurrently to avoid deadlock) and wait for
     * the process with a timeout, killing it if it overruns.
     */
    private fun drainWithTimeout(p: Process): String? {
        // Drain stderr on a side thread so a chatty command can't block us.
        val errThread = Thread {
            runCatching { p.errorStream.bufferedReader().readText() }
        }.apply { isDaemon = true; start() }

        val out = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("")
        val finished = runCatching { p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!finished) {
            runCatching { p.destroyForcibly() }
            return null
        }
        runCatching { errThread.join(500) }
        return out.trim().ifBlank { null }
    }

    /** cat a single file, returning its trimmed contents or null. */
    fun cat(path: String, backend: Backend = bestBackend()): String? =
        run("cat '$path' 2>/dev/null", backend)?.takeIf { it.isNotBlank() }
}
