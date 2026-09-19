package melox.recommendation

data class LocalTrackFeatures(
    val trackKey: String,
    val title: String,
    val artist: String,
    val playCount: Int = 0,
    val completedCount: Int = 0,
    val skipCount: Int = 0,
    val liked: Boolean = false,
    val lastPlayedAtMs: Long = 0L,
    val sourceCount: Int = 1,
    val versionKind: String = "Studio",
    val qualityPreference: Float = 0f,
)

data class LocalRecommendationItem(
    val trackKey: String,
    val title: String,
    val artist: String,
    val score: Float,
    val ruleScore: Float,
    val modelScore: Float,
    val reason: String = "本地行为偏好",
)

data class LocalModelMetadata(
    val version: Int = 2,
    val sampleCount: Int = 0,
    val trackCount: Int = 0,
    val trainedAtMs: Long = 0L,
    val backend: String = "本地线性模型",
)

enum class LocalAnalysisStage {
    Idle,
    QuickScoring,
    SyncPreparing,
    Syncing,
    IdentityMerging,
    RuleScoring,
    ModelScoring,
    Completed,
    Paused,
    Failed,
}

data class LocalAnalysisProgress(
    val stage: LocalAnalysisStage = LocalAnalysisStage.Idle,
    val processed: Int = 0,
    val total: Int = 0,
    val modelBackend: String = "本地线性模型",
    val message: String? = null,
    val isFullAnalysis: Boolean = false,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (processed.toFloat() / total).coerceIn(0f, 1f)
}
