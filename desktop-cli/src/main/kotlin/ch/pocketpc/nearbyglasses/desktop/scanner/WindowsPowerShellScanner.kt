package ch.pocketpc.nearbyglasses.desktop.scanner

import ch.pocketpc.nearbyglasses.core.BleAdvertisement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class WindowsPowerShellScanner(
    private val debug: Boolean = false,
    private val onDebug: (String) -> Unit = {}
) : PlatformBleScanner {

    private val running = AtomicBoolean(false)

    private var process: Process? = null
    private var readerThread: Thread? = null
    private var scriptPath: Path? = null
    private var callback: ((BleAdvertisement) -> Unit)? = null

    override fun start(onAdvertisement: (BleAdvertisement) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            return
        }

        callback = onAdvertisement
        val script = extractScript()
        scriptPath = script

        val commands = listOf("pwsh", "powershell")
        var started: Process? = null
        var startError: Exception? = null

        for (command in commands) {
            try {
                started = ProcessBuilder(
                    command,
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.toAbsolutePath().toString()
                ).redirectErrorStream(true).start()
                break
            } catch (e: Exception) {
                startError = e
            }
        }

        if (started == null) {
            running.set(false)
            throw IllegalStateException(
                "Failed to start PowerShell scanner. Ensure pwsh or powershell is installed.",
                startError
            )
        }

        process = started

        readerThread = thread(name = "powershell-ble-reader", isDaemon = true) {
            val reader = BufferedReader(InputStreamReader(started.inputStream, StandardCharsets.UTF_8))
            while (running.get()) {
                val line = reader.readLine() ?: break
                if (debug) {
                    onDebug("powershell: $line")
                }
                parseJsonLine(line)?.let { advertisement ->
                    callback?.invoke(advertisement)
                }
            }
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        process?.destroy()
        process = null

        readerThread?.interrupt()
        readerThread = null

        callback = null

        scriptPath?.let {
            try {
                Files.deleteIfExists(it)
            } catch (_: Exception) {
                // ignore
            }
        }
        scriptPath = null
    }

    private fun parseJsonLine(line: String): BleAdvertisement? {
        return try {
            val json = JsonParser.parseString(line).asJsonObject
            val address = json.readString("deviceAddress")?.uppercase(Locale.ROOT) ?: return null
            val rssi = json.readInt("rssi") ?: return null
            val companyId = json.readInt("companyId")
            val manufacturerDataHex = json.readString("manufacturerDataHex")
            val deviceName = json.readString("deviceName")

            BleAdvertisement(
                deviceAddress = address,
                deviceName = deviceName,
                rssi = rssi,
                companyId = companyId,
                manufacturerDataHex = manufacturerDataHex
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractScript(): Path {
        val stream = javaClass.classLoader.getResourceAsStream("windows_ble_scan.ps1")
            ?: throw IllegalStateException("Missing windows_ble_scan.ps1 resource")

        val temp = Files.createTempFile("nearby-glasses-ble", ".ps1")
        stream.use { input ->
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
        }
        return temp
    }

    private fun JsonObject.readString(name: String): String? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        val value = element.asString.trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.readInt(name: String): Int? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        return element.asInt
    }
}
