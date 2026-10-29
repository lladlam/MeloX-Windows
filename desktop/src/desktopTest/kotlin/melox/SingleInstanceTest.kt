package melox

import java.awt.EventQueue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SingleInstanceTest {
    @Test fun activationDuringStartupIsDeliveredAndLockIsReleased() {
        val root = Files.createTempDirectory("melox-single-instance-test-")
        val primary = assertNotNull(SingleInstance.acquire(root))
        try {
            // A second launch may arrive before Compose has installed its window callback.
            assertNull(SingleInstance.acquire(root))
            val delivered = CountDownLatch(1)
            var onEventThread = false
            primary.onActivate { onEventThread = EventQueue.isDispatchThread(); delivered.countDown() }
            assertTrue(delivered.await(5, TimeUnit.SECONDS))
            assertTrue(onEventThread)
            val next = CountDownLatch(1)
            primary.onActivate { next.countDown() }
            assertNull(SingleInstance.acquire(root))
            assertTrue(next.await(5, TimeUnit.SECONDS))
        } finally { primary.close() }
        assertNotNull(SingleInstance.acquire(root)).close()
    }

    @Test fun aSeparateProcessActivatesThePrimary() {
        val root = Files.createTempDirectory("melox-single-instance-process-")
        val primary = assertNotNull(SingleInstance.acquire(root))
        try {
            val delivered = CountDownLatch(1)
            primary.onActivate { delivered.countDown() }
            val classpath = listOf(SingleInstance::class.java, SingleInstanceLaunchProbe::class.java,
                kotlin.Unit::class.java, com.sun.jna.Native::class.java)
                .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
            val java = File(System.getProperty("java.home"), "bin/java").path
            val process = ProcessBuilder(java, "-cp", classpath, SingleInstanceLaunchProbe::class.java.name, root.toString())
                .redirectErrorStream(true).start()
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "secondary process did not exit")
            assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
            assertTrue(delivered.await(5, TimeUnit.SECONDS))
        } finally { primary.close() }
    }
}

object SingleInstanceLaunchProbe {
    @JvmStatic fun main(args: Array<String>) {
        check(SingleInstance.acquire(java.nio.file.Path.of(args.single())) == null) { "unexpected second primary" }
    }
}
