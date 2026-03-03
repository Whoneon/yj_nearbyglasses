package ch.pocketpc.nearbyglasses.desktop.scanner

import ch.pocketpc.nearbyglasses.core.BleAdvertisement
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LinuxBluetoothCtlScanner(
    private val debug: Boolean = false,
    private val onDebug: (String) -> Unit = {}
) : PlatformBleScanner {

    private data class DeviceState(
        var name: String? = null,
        var rssi: Int? = null,
        var companyId: Int? = null,
        var manufacturerDataHex: String? = null
    )

    private val running = AtomicBoolean(false)
    private val states = ConcurrentHashMap<String, DeviceState>()

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null
    private var callback: ((BleAdvertisement) -> Unit)? = null

    override fun start(onAdvertisement: (BleAdvertisement) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            return
        }

        callback = onAdvertisement
        val started = try {
            ProcessBuilder("bluetoothctl")
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            running.set(false)
            throw IllegalStateException(
                "Failed to start bluetoothctl. Ensure BlueZ/bluetoothctl is installed.",
                e
            )
        }

        process = started
        writer = BufferedWriter(OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8))

        readerThread = thread(name = "bluetoothctl-reader", isDaemon = true) {
            val reader = BufferedReader(InputStreamReader(started.inputStream, StandardCharsets.UTF_8))
            while (running.get()) {
                val line = reader.readLine() ?: break
                if (debug) {
                    onDebug("bluetoothctl: $line")
                }
                parseLine(line)?.let { advertisement ->
                    callback?.invoke(advertisement)
                }
            }
        }

        sendCommand("power on")
        sendCommand("scan on")
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        try {
            sendCommand("scan off")
            sendCommand("quit")
            writer?.flush()
        } catch (_: Exception) {
            // ignore cleanup failures
        }

        writer?.close()
        writer = null

        process?.destroy()
        process = null

        readerThread?.interrupt()
        readerThread = null

        callback = null
        states.clear()
    }

    private fun sendCommand(command: String) {
        val currentWriter = writer ?: return
        currentWriter.write(command)
        currentWriter.newLine()
        currentWriter.flush()
    }

    private fun parseLine(line: String): BleAdvertisement? {
        val addressMatch = MAC_REGEX.find(line) ?: return null
        val address = addressMatch.value.uppercase(Locale.ROOT)
        val state = states.computeIfAbsent(address) { DeviceState() }

        var touched = false

        RSSI_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { rssi ->
            state.rssi = rssi
            touched = true
        }

        MFG_KEY_REGEX.find(line)?.groupValues?.getOrNull(1)?.let { raw ->
            raw.removePrefix("0x").removePrefix("0X").toIntOrNull(16)?.let { companyId ->
                state.companyId = companyId
                touched = true
            }
        }

        MFG_VALUE_REGEX.find(line)?.groupValues?.getOrNull(1)?.let { raw ->
            val hex = raw.replace(" ", "").replace(":", "")
                .replace(Regex("[^0-9A-Fa-f]"), "")
                .uppercase(Locale.ROOT)
            if (hex.isNotEmpty()) {
                state.manufacturerDataHex = hex
                touched = true
            }
        }

        NAME_KEY_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
            state.name = name
            touched = true
        }

        SIMPLE_DEVICE_LINE.find(line)?.let { match ->
            val payload = match.groupValues[1].trim()
            if (!looksLikeProperty(payload) && payload.isNotBlank()) {
                state.name = payload
                touched = true
            }
        }

        if (!touched || state.rssi == null) {
            return null
        }

        if (state.companyId == null && state.name.isNullOrBlank()) {
            return null
        }

        return BleAdvertisement(
            deviceAddress = address,
            deviceName = state.name,
            rssi = state.rssi ?: return null,
            companyId = state.companyId,
            manufacturerDataHex = state.manufacturerDataHex
        )
    }

    private fun looksLikeProperty(payload: String): Boolean {
        return payload.startsWith("RSSI:") ||
            payload.startsWith("TxPower:") ||
            payload.startsWith("ManufacturerData") ||
            payload.startsWith("ServiceData") ||
            payload.startsWith("Name:") ||
            payload.startsWith("Alias:")
    }

    companion object {
        private val MAC_REGEX = Regex("""([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})""")
        private val RSSI_REGEX = Regex("""RSSI:\s*(-?\d+)""")
        private val MFG_KEY_REGEX = Regex("""ManufacturerData Key:\s*(0x[0-9A-Fa-f]{1,4})""")
        private val MFG_VALUE_REGEX = Regex("""ManufacturerData Value:\s*(.*)$""")
        private val NAME_KEY_REGEX = Regex("""(?:Name|Alias):\s*(.+)$""")
        private val SIMPLE_DEVICE_LINE = Regex("""\[(?:NEW|CHG)\]\s+Device\s+[0-9A-Fa-f:]{17}\s+(.+)$""")
    }
}
