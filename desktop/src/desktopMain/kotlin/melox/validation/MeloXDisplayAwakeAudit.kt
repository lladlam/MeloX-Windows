package melox.validation

import melox.platform.MeloXDisplayAwakeController
import melox.platform.MeloXDisplayAwakeSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Calls real Windows kernel32; returned prior execution flags prove same-thread acquire/release. */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { parentFile.mkdirs() }
    val states = mutableListOf<MeloXDisplayAwakeSnapshot>()
    val owner = MeloXDisplayAwakeController()
    try {
        val on = owner.setRequired(true).get(10, TimeUnit.SECONDS).also(states::add)
        if (!on.available) {
            output.writeText(JSONObject().put("status", "UNSUPPORTED").put("os", System.getProperty("os.name"))
                .put("native_windows_acceptance", "BLOCKED").toString(2) + "\n")
            println("UNSUPPORTED: real Windows display power API is unavailable")
            return
        }
        check(on.active && on.error == null && on.nativeCalls == 1)
        val unchanged = owner.setRequired(true).get(10, TimeUnit.SECONDS).also(states::add)
        check(unchanged == on) { "Repeated stable UI must not make extra native requests" }
        val off = owner.setForeground(false).get(10, TimeUnit.SECONDS).also(states::add)
        check(!off.active && off.error == null && off.previousFlags and 3 == 3 && off.nativeCalls == 2)
        val staleUi = owner.setRequired(true).get(10, TimeUnit.SECONDS).also(states::add)
        check(staleUi == off) { "Delayed UI request reacquired a background/minimized display hold" }
        val resumed = owner.setForeground(true).get(10, TimeUnit.SECONDS).also(states::add)
        check(resumed.active && resumed.error == null && resumed.previousFlags and 3 == 0)
        val released = owner.release().get(10, TimeUnit.SECONDS).also(states::add)
        check(!released.active && released.error == null && released.previousFlags and 3 == 3 && released.nativeCalls == 4)
        check(states.map { it.ownerThreadId }.distinct().size == 1)
        check(owner.setRequired(true).get(10, TimeUnit.SECONDS) == released) { "Late child disposal/request resurrected native hold" }
        check(owner.release().get(10, TimeUnit.SECONDS) == released)
        val samples = JSONArray()
        states.forEach { s -> samples.put(JSONObject().put("requested", s.requested).put("active", s.active)
            .put("native_calls", s.nativeCalls).put("previous_flags", s.previousFlags.toLong() and 0xffffffffL)
            .put("owner_thread_id", s.ownerThreadId).put("error", s.error ?: JSONObject.NULL)) }
        output.writeText(JSONObject().put("status", "PASSED").put("os", System.getProperty("os.name"))
            .put("api", "kernel32.SetThreadExecutionState").put("samples", samples)
            .put("physical_display_idle_sleep_acceptance", "BLOCKED").toString(2) + "\n")
        println("PASS: real Windows display request acquire/release/reacquire/disposal on one native owner thread")
    } finally { owner.close() }
}
