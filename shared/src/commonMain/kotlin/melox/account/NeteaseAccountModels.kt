package melox.account

data class NeteaseAccountProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val signature: String?,
)
