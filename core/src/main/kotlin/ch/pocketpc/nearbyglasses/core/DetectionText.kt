package ch.pocketpc.nearbyglasses.core

object DetectionText {
    fun formatReasons(reasons: List<SmartGlassesDetector.Reason>): String {
        return reasons.joinToString(", ") { reason ->
            when (reason) {
                is SmartGlassesDetector.Reason.CompanyIdMatch -> {
                    when (reason.companyKey) {
                        SmartGlassesDetector.CompanyKey.META ->
                            "Meta company ID (${SmartGlassesDetector.formatCompanyId(reason.companyId)})"

                        SmartGlassesDetector.CompanyKey.ESSILOR ->
                            "EssilorLuxottica company ID (${SmartGlassesDetector.formatCompanyId(reason.companyId)})"

                        SmartGlassesDetector.CompanyKey.SNAP ->
                            "Snap company ID (${SmartGlassesDetector.formatCompanyId(reason.companyId)})"
                    }
                }

                is SmartGlassesDetector.Reason.NameContains ->
                    "Device name contains '${reason.token}'"
            }
        }
    }

    fun companyName(companyId: Int?): String {
        if (companyId == null) {
            return "Unknown"
        }

        return when (SmartGlassesDetector.companyKeyFor(companyId)) {
            SmartGlassesDetector.CompanyKey.META -> "Meta Platforms"
            SmartGlassesDetector.CompanyKey.ESSILOR -> "EssilorLuxottica"
            SmartGlassesDetector.CompanyKey.SNAP -> "Snap"
            null -> "Unknown (${SmartGlassesDetector.formatCompanyId(companyId)})"
        }
    }
}
