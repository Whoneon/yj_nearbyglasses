package ch.pocketpc.nearbyglasses.desktop

import java.util.Locale

enum class ScannerMode {
    AUTO,
    LINUX,
    WINDOWS
}

data class CliConfig(
    val scannerMode: ScannerMode = ScannerMode.AUTO,
    val rssiThreshold: Int = -75,
    val cooldownMs: Long = 10_000,
    val overrideCompanyIds: Set<Int> = emptySet(),
    val debug: Boolean = false,
    val notificationsEnabled: Boolean = true
)

object CliParser {
    fun parse(args: Array<String>): CliConfig {
        var scannerMode = ScannerMode.AUTO
        var rssiThreshold = -75
        var cooldownMs = 10_000L
        var overrideCompanyIds = emptySet<Int>()
        var debug = false
        var notificationsEnabled = true

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--scanner" -> {
                    scannerMode = parseScannerMode(requireValue(args, i, "--scanner"))
                    i += 2
                }

                "--rssi-threshold" -> {
                    rssiThreshold = requireValue(args, i, "--rssi-threshold").toIntOrNull()
                        ?: throw IllegalArgumentException("--rssi-threshold must be an integer")
                    i += 2
                }

                "--cooldown-ms" -> {
                    cooldownMs = requireValue(args, i, "--cooldown-ms").toLongOrNull()
                        ?: throw IllegalArgumentException("--cooldown-ms must be a long")
                    i += 2
                }

                "--override-company-ids" -> {
                    overrideCompanyIds = parseCompanyIds(requireValue(args, i, "--override-company-ids"))
                    i += 2
                }

                "--debug" -> {
                    debug = true
                    i += 1
                }

                "--no-notify" -> {
                    notificationsEnabled = false
                    i += 1
                }

                "--help", "-h" -> throw HelpRequested

                else -> throw IllegalArgumentException("Unknown argument: ${args[i]}")
            }
        }

        return CliConfig(
            scannerMode = scannerMode,
            rssiThreshold = rssiThreshold,
            cooldownMs = cooldownMs.coerceAtLeast(0),
            overrideCompanyIds = overrideCompanyIds,
            debug = debug,
            notificationsEnabled = notificationsEnabled
        )
    }

    fun usage(): String = """
        Nearby Glasses Desktop CLI

        Usage:
          nearby-glasses [options]

        Options:
          --scanner auto|linux|windows
          --rssi-threshold <int>           Default: -75
          --cooldown-ms <long>             Default: 10000
          --override-company-ids <csv>     Example: 0x01AB,0x058E,0x0D53
          --debug
          --no-notify
          --help, -h
    """.trimIndent()

    private fun parseScannerMode(raw: String): ScannerMode {
        return when (raw.lowercase(Locale.ROOT)) {
            "auto" -> ScannerMode.AUTO
            "linux" -> ScannerMode.LINUX
            "windows" -> ScannerMode.WINDOWS
            else -> throw IllegalArgumentException("Unsupported scanner mode: $raw")
        }
    }

    private fun parseCompanyIds(raw: String): Set<Int> {
        if (raw.isBlank()) {
            return emptySet()
        }

        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                val t = token.lowercase(Locale.ROOT)
                when {
                    t.startsWith("0x") -> t.removePrefix("0x").toIntOrNull(16)
                    t.all { it.isDigit() } -> t.toIntOrNull(10)
                    else -> t.toIntOrNull(16)
                } ?: throw IllegalArgumentException("Invalid company ID: $token")
            }
            .toSet()
    }

    private fun requireValue(args: Array<String>, index: Int, key: String): String {
        if (index + 1 >= args.size) {
            throw IllegalArgumentException("Missing value for $key")
        }
        return args[index + 1]
    }

    object HelpRequested : RuntimeException()
}
