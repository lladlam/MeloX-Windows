package melox.network

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Keep cancellation ownership alive through response-body consumption, not just execute(). */
@OptIn(InternalCoroutinesApi::class)
internal fun <T> OkHttpClient.executeOwned(request: Request, owner: Job?, consume: (Response) -> T): T {
    owner?.ensureActive()
    val call = newCall(request)
    val cancellation = owner?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
    return try { call.execute().use(consume) }
    catch (failure: java.io.IOException) { owner?.ensureActive(); throw failure }
    finally { cancellation?.dispose() }
}
