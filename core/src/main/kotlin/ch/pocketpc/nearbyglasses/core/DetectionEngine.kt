package ch.pocketpc.nearbyglasses.core

data class DetectionDecision(
    val matched: Boolean,
    val matchedByOverride: Boolean,
    val reasons: List<SmartGlassesDetector.Reason>
)

class DetectionEngine(
    private val rssiThreshold: Int,
    private val overrideCompanyIds: Set<Int> = emptySet()
) {
    fun evaluate(advertisement: BleAdvertisement): DetectionDecision {
        if (advertisement.rssi < rssiThreshold) {
            return DetectionDecision(
                matched = false,
                matchedByOverride = false,
                reasons = emptyList()
            )
        }

        val detectorResult = SmartGlassesDetector.detect(
            companyId = advertisement.companyId,
            deviceName = advertisement.deviceName
        )
        val overrideMatch = advertisement.companyId != null && overrideCompanyIds.contains(advertisement.companyId)

        return DetectionDecision(
            matched = detectorResult.isSmartGlasses || overrideMatch,
            matchedByOverride = overrideMatch,
            reasons = detectorResult.reasons
        )
    }
}
