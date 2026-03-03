package ch.pocketpc.nearbyglasses.model

import android.content.Context
import android.os.Parcelable
import ch.pocketpc.nearbyglasses.R
import ch.pocketpc.nearbyglasses.core.SmartGlassesDetector
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Parcelize
data class DetectionEvent(
    val timestamp: Long,
    val deviceAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val companyId: String?,
    val companyName: String,
    val manufacturerData: String?,
    val detectionReason: String
) : Parcelable {

    fun toJson(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
            {
                "timestamp": $timestamp,
                "timestampFormatted": "${dateFormat.format(Date(timestamp))}",
                "deviceAddress": "$deviceAddress",
                "deviceName": ${deviceName?.let { "\"$it\"" } ?: "null"},
                "rssi": $rssi,
                "companyId": ${companyId?.let { "\"$it\"" } ?: "null"},
                "companyName": "$companyName",
                "manufacturerData": ${manufacturerData?.let { "\"$it\"" } ?: "null"},
                "detectionReason": "$detectionReason"
            }
        """.trimIndent()
    }

    fun toLogString(context: Context): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = dateFormat.format(Date(timestamp))
        val name = deviceName ?: context.getString(R.string.unknown_device)
        return "[$time] $name (${rssi}dBm) - $detectionReason"
    }

    companion object {
        fun isSmartGlasses(context: Context, companyId: Int?, deviceName: String?): Pair<Boolean, String> {
            val result = SmartGlassesDetector.detect(companyId, deviceName)
            val reasons = result.reasons.map { reason ->
                when (reason) {
                    is SmartGlassesDetector.Reason.CompanyIdMatch -> {
                        when (reason.companyKey) {
                            SmartGlassesDetector.CompanyKey.META -> context.getString(
                                R.string.reason_meta_company_id,
                                SmartGlassesDetector.formatCompanyId(reason.companyId)
                            )

                            SmartGlassesDetector.CompanyKey.ESSILOR -> context.getString(
                                R.string.reason_essilor_company_id,
                                SmartGlassesDetector.formatCompanyId(reason.companyId)
                            )

                            SmartGlassesDetector.CompanyKey.SNAP -> context.getString(
                                R.string.reason_snap_company_id,
                                SmartGlassesDetector.formatCompanyId(reason.companyId)
                            )
                        }
                    }

                    is SmartGlassesDetector.Reason.NameContains -> {
                        context.getString(R.string.reason_name_contains, reason.token)
                    }
                }
            }

            return Pair(result.isSmartGlasses, reasons.joinToString(", "))
        }

        fun getCompanyName(context: Context, companyId: Int): String {
            return when (SmartGlassesDetector.companyKeyFor(companyId)) {
                SmartGlassesDetector.CompanyKey.META -> context.getString(R.string.company_meta)
                SmartGlassesDetector.CompanyKey.ESSILOR -> context.getString(R.string.company_essilor)
                SmartGlassesDetector.CompanyKey.SNAP -> context.getString(R.string.company_snap)
                null -> context.getString(
                    R.string.company_unknown,
                    SmartGlassesDetector.formatCompanyId(companyId)
                )
            }
        }
    }
}
