package melox.autosync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Public model management used by both settings layouts. */
class MeloXAutoSyncPack internal constructor(internal val pack: AutoSyncModelRepository.Pack) {
    val language get() = pack.language
    val sourceModel get() = pack.sourceModel
    val license get() = pack.license
    val accuracyStatus get() = pack.accuracyStatus
    val bytes get() = pack.totalBytes
    val precisionBytes get() = pack.precision?.let { precision ->
        pack.totalBytes - pack.required.single { it.role == "model" }.bytes + precision.bytes
    }
}

data class MeloXAutoSyncInstalled(val language: String, val variant: String, val bytes: Long)

object MeloXAutoSyncModels {
    internal val repository = AutoSyncModelRepository()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    suspend fun available(): List<MeloXAutoSyncPack> = repository.fetchIndex().packs.values
        .sortedBy { listOf("zh", "en", "ja").indexOf(it.language) }.map(::MeloXAutoSyncPack)

    fun installed(language: String): MeloXAutoSyncInstalled? = repository.installed(language)?.let { model ->
        val files = model.manifest.getJSONArray("installed_files")
        MeloXAutoSyncInstalled(language, model.manifest.optString("installed_variant", "int8"),
            (0 until files.length()).sumOf { files.getJSONObject(it).getLong("bytes") })
    }

    suspend fun install(pack: MeloXAutoSyncPack, precision: Boolean = false, onProgress: (Float) -> Unit = {}) {
        repository.install(pack.pack, precision, onProgress)
        _revision.update { it + 1 }
    }

    suspend fun remove(language: String): Boolean = repository.remove(language).also { removed ->
        if (removed) _revision.update { it + 1 }
    }
}
