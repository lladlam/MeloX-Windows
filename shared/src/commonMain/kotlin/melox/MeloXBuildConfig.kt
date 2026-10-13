package melox

object MeloXBuildConfig {
    val SPOTIFY_CLIENT_ID: String = ""
    val DEBUG: Boolean = false
    val BUILD_COMMIT: String by lazy {
        val values = java.util.Properties()
        MeloXBuildConfig::class.java.classLoader.getResourceAsStream("parity/build-info.properties")
            ?.reader(Charsets.UTF_8)?.use(values::load)
        values.getProperty("commit", "local development / unavailable")
    }
}
