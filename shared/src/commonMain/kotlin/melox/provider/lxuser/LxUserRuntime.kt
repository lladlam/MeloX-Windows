package melox.provider.lxuser

class LxUserRuntime : AutoCloseable {
    fun load(script: LxUserScript) {}
    fun callAction(action: String, vararg args: Any?): Any? = null
    fun qualityFor(source: String, requestedQuality: String): String = requestedQuality
    override fun close() {}
}