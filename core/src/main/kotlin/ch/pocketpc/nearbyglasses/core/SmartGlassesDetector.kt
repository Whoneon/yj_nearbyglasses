package ch.pocketpc.nearbyglasses.core

import java.util.Locale

object SmartGlassesDetector {
    const val META_COMPANY_ID1 = 0x01AB
    const val META_COMPANY_ID2 = 0x058E
    const val ESSILOR_COMPANY_ID = 0x0D53
    const val SNAP_COMPANY_ID = 0x03C2

    enum class CompanyKey {
        META,
        ESSILOR,
        SNAP
    }

    sealed interface Reason {
        data class CompanyIdMatch(val companyId: Int, val companyKey: CompanyKey) : Reason
        data class NameContains(val token: String) : Reason
    }

    data class Result(
        val isSmartGlasses: Boolean,
        val reasons: List<Reason>
    )

    private val knownCompanyIds = mapOf(
        META_COMPANY_ID1 to CompanyKey.META,
        META_COMPANY_ID2 to CompanyKey.META,
        ESSILOR_COMPANY_ID to CompanyKey.ESSILOR,
        SNAP_COMPANY_ID to CompanyKey.SNAP
    )

    fun detect(companyId: Int?, deviceName: String?): Result {
        val reasons = mutableListOf<Reason>()

        val companyKey = companyId?.let { knownCompanyIds[it] }
        if (companyId != null && companyKey != null) {
            reasons.add(Reason.CompanyIdMatch(companyId = companyId, companyKey = companyKey))
        }

        val tokenMatch = findNameToken(deviceName)
        if (tokenMatch != null) {
            reasons.add(Reason.NameContains(tokenMatch))
        }

        return Result(
            isSmartGlasses = reasons.isNotEmpty(),
            reasons = reasons
        )
    }

    fun companyKeyFor(companyId: Int): CompanyKey? = knownCompanyIds[companyId]

    fun formatCompanyId(companyId: Int): String = String.format(Locale.ROOT, "0x%04X", companyId)

    private fun findNameToken(deviceName: String?): String? {
        val lower = deviceName?.lowercase(Locale.ROOT) ?: return null
        return when {
            lower.contains("rayban") -> "rayban"
            lower.contains("ray-ban") -> "ray-ban"
            lower.contains("ray ban") -> "ray ban"
            else -> null
        }
    }
}
