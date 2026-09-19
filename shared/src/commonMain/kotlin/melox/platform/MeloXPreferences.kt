package melox.platform

/**
 * Desktop equivalent of android.content.SharedPreferences.
 *
 * Backed by a single properties file per [name] under the MeloX data directory.
 * Used by session stores and provider selection stores that originally called
 * ContextSharedPreferences.
 */
expect class MeloXPreferences(name: String) {
    fun getString(key: String, defaultValue: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, defaultValue: Float): Float
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
    fun clear()
    fun contains(key: String): Boolean
    fun putStringSet(key: String, values: Set<String>)
    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>?
}

expect fun getPreferences(name: String): MeloXPreferences