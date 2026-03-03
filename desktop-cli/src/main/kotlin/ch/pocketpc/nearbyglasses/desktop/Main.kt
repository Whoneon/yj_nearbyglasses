package ch.pocketpc.nearbyglasses.desktop

import ch.pocketpc.nearbyglasses.core.BleAdvertisement
import ch.pocketpc.nearbyglasses.core.DetectionEngine
import ch.pocketpc.nearbyglasses.core.DetectionText
import ch.pocketpc.nearbyglasses.core.SmartGlassesDetector
import ch.pocketpc.nearbyglasses.desktop.scanner.LinuxBluetoothCtlScanner
import ch.pocketpc.nearbyglasses.desktop.scanner.PlatformBleScanner
import ch.pocketpc.nearbyglasses.desktop.scanner.WindowsPowerShellScanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch

private enum class RuntimePlatform {
    LINUX,
    WINDOWS,
    MAC,
    OTHER
}

private interface AlertNotifier {
    fun notify(title: String, message: String)
}

private object NoopNotifier : AlertNotifier {
    override fun notify(title: String, message: String) = Unit
}

private class LinuxNotifySendNotifier : AlertNotifier {
    override fun notify(title: String, message: String) {
        try {
            ProcessBuilder("notify-send", title, message).start()
        } catch (_: Exception) {
            // ignore if notify-send is unavailable
        }
    }
}

private class DetectionCoordinator(
    private val config: CliConfig,
    private val notifier: AlertNotifier,
    private val onDebug: (String) -> Unit
) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val engine = DetectionEngine(
        rssiThreshold = config.rssiThreshold,
        overrideCompanyIds = config.overrideCompanyIds
    )

    private var lastNotificationAt = 0L

    @Synchronized
    fun onAdvertisement(advertisement: BleAdvertisement) {
        val decision = engine.evaluate(advertisement)
        if (!decision.matched) {
            return
        }

        val reasonsText = buildReasonText(advertisement, decision)
        val companyName = DetectionText.companyName(advertisement.companyId)
        val deviceName = advertisement.deviceName ?: "Unknown device"
        val timestamp = timeFormat.format(Date(advertisement.timestamp))

        println("[$timestamp] $deviceName (${advertisement.rssi} dBm) - $reasonsText [$companyName]")

        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < config.cooldownMs) {
            if (config.debug) {
                onDebug("Notification suppressed by cooldown (${config.cooldownMs} ms)")
            }
            return
        }

        notifier.notify(
            title = "Nearby Glasses Alert",
            message = "$deviceName (${advertisement.rssi} dBm) - $reasonsText"
        )
        lastNotificationAt = now
    }

    private fun buildReasonText(
        advertisement: BleAdvertisement,
        decision: ch.pocketpc.nearbyglasses.core.DetectionDecision
    ): String {
        val base = DetectionText.formatReasons(decision.reasons)
        if (!decision.matchedByOverride) {
            return base
        }

        val overrideText = advertisement.companyId?.let {
            "Override company ID match (${SmartGlassesDetector.formatCompanyId(it)})"
        } ?: "Override company ID match"

        return if (base.isBlank()) {
            overrideText
        } else {
            "$base, $overrideText"
        }
    }
}

fun main(args: Array<String>) {
    val config = try {
        CliParser.parse(args)
    } catch (_: CliParser.HelpRequested) {
        println(CliParser.usage())
        return
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        System.err.println()
        System.err.println(CliParser.usage())
        return
    }

    val platform = detectPlatform()
    val debugPrinter: (String) -> Unit = { message ->
        if (config.debug) {
            val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            println("[$now] DEBUG: $message")
        }
    }

    val scanner = createScanner(config, platform, debugPrinter)
    val notifier = createNotifier(config, platform)
    val coordinator = DetectionCoordinator(config, notifier, debugPrinter)

    println("Starting scanner: platform=$platform, mode=${config.scannerMode}, rssiThreshold=${config.rssiThreshold}, cooldownMs=${config.cooldownMs}")
    if (config.overrideCompanyIds.isNotEmpty()) {
        val ids = config.overrideCompanyIds.joinToString(",") { SmartGlassesDetector.formatCompanyId(it) }
        println("Override company IDs: $ids")
    }

    val done = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Stopping scanner...")
        scanner.stop()
        done.countDown()
    })

    scanner.start { advertisement ->
        coordinator.onAdvertisement(advertisement)
    }

    println("Scanning started. Press Ctrl+C to stop.")
    done.await()
}

private fun createScanner(
    config: CliConfig,
    platform: RuntimePlatform,
    onDebug: (String) -> Unit
): PlatformBleScanner {
    return when (config.scannerMode) {
        ScannerMode.AUTO -> {
            when (platform) {
                RuntimePlatform.LINUX -> LinuxBluetoothCtlScanner(config.debug, onDebug)
                RuntimePlatform.WINDOWS -> WindowsPowerShellScanner(config.debug, onDebug)
                else -> throw IllegalStateException(
                    "Auto scanner unsupported on platform $platform. Use --scanner linux or --scanner windows on the target OS."
                )
            }
        }

        ScannerMode.LINUX -> LinuxBluetoothCtlScanner(config.debug, onDebug)
        ScannerMode.WINDOWS -> WindowsPowerShellScanner(config.debug, onDebug)
    }
}

private fun createNotifier(config: CliConfig, platform: RuntimePlatform): AlertNotifier {
    if (!config.notificationsEnabled) {
        return NoopNotifier
    }

    return when (platform) {
        RuntimePlatform.LINUX -> LinuxNotifySendNotifier()
        else -> NoopNotifier
    }
}

private fun detectPlatform(): RuntimePlatform {
    val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
    return when {
        osName.contains("win") -> RuntimePlatform.WINDOWS
        osName.contains("linux") -> RuntimePlatform.LINUX
        osName.contains("mac") || osName.contains("darwin") -> RuntimePlatform.MAC
        else -> RuntimePlatform.OTHER
    }
}
