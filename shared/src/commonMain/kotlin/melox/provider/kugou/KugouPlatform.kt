package melox.provider.kugou

/** Standard and concept clients have distinct app identities and credentials. */
enum class KugouPlatform(
    val appId: Int,
    val clientVersion: Int,
    val androidSignatureSalt: String,
    val keySignatureSalt: String,
    val userAgent: String,
    val isLite: Boolean,
) {
    Standard(1005, 20489, "OIlwieks28dk2k092lksi2UIkp", "57ae12eb6890223e355ccfcb74edf70d",
        "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi", false),
    Lite(3116, 11440, "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA", "185672dd44712f60bb1736df5a377e82",
        "Android15-1070-11440-46-0-DiscoveryDRADProtocol-wifi", true),
}
