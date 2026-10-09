package melox.platform

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt

internal interface SecretProtection {
    fun protect(plain: ByteArray): ByteArray
    fun unprotect(encrypted: ByteArray): ByteArray
}

internal object DesktopSecretProtection {
    // No machine-wide flag: only the logged-in Windows user can decrypt.
    // Native errors propagate to the account UI rather than downgrading storage.
    val current: SecretProtection? by lazy { if (Platform.isWindows()) WindowsDpapi else null }
}

internal object WindowsDpapi : SecretProtection {
    override fun protect(plain: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(plain, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
    override fun unprotect(encrypted: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(encrypted, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
}
