package melox.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

/** Documented desktop InputPane interop; TryShow is idempotent, unlike Toggle. */
internal object MeloXWindowsTouchKeyboard {
    val requestCount = java.util.concurrent.atomic.AtomicLong()
    private interface Runtime : StdCallLibrary {
        fun RoInitialize(type: Int): Int
        fun RoUninitialize()
        fun WindowsCreateString(text: Pointer, length: Int, result: PointerByReference): Int
        fun WindowsDeleteString(value: Pointer): Int
        fun RoGetActivationFactory(name: Pointer, iid: Guid.GUID, result: PointerByReference): Int
    }
    private val runtime by lazy { Native.load("combase", Runtime::class.java) }
    private fun call(instance: Pointer, slot: Int, vararg args: Any): Int =
        Function.getFunction(instance.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            .invokeInt(arrayOf(instance, *args))

    fun show() {
        requestCount.incrementAndGet()
        if (SwingUtilities.isEventDispatchThread()) showNative() else SwingUtilities.invokeLater { showNative() }
    }

    private fun showNative() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true) || GraphicsEnvironment.isHeadless()) return
        val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow ?: return
        if (!window.isFocused || !window.isDisplayable) return
        runCatching {
            val initialized = runtime.RoInitialize(0)
            // AWT may already have initialized this thread with a different apartment.
            check(initialized >= 0 || initialized == 0x80010106.toInt()) { "RoInitialize=$initialized" }
            val name = PointerByReference()
            val factory = PointerByReference()
            val pane = PointerByReference()
            try {
                val className = "Windows.UI.ViewManagement.InputPane"
                val utf16 = Memory((className.length + 1L) * 2)
                className.forEachIndexed { index, char -> utf16.setShort(index * 2L, char.code.toShort()) }
                utf16.setShort(className.length * 2L, 0)
                check(runtime.WindowsCreateString(utf16, className.length, name) >= 0)
                check(runtime.RoGetActivationFactory(name.value, Guid.GUID("{75CF2C57-9195-4931-8332-F0B409E916AF}"), factory) >= 0)
                // IInputPaneInterop inherits IInspectable (six base vtable slots).
                check(call(factory.value, 6, Native.getComponentPointer(window),
                    Guid.GUID("{8A6B3F26-7090-4793-944C-C3F2CDE26276}"), pane) >= 0)
                val shown = ByteByReference()
                val result = call(pane.value, 6, shown)
                logInfo("MeloXTouchKeyboard", "InputPane.TryShow hresult=$result accepted=${shown.value.toInt() != 0}")
            } finally {
                pane.value?.let { call(it, 2) }
                factory.value?.let { call(it, 2) }
                name.value?.let { runtime.WindowsDeleteString(it) }
                if (initialized >= 0) runtime.RoUninitialize()
            }
        }.onFailure { logInfo("MeloXTouchKeyboard", "InputPane request failed: ${it.javaClass.simpleName}: ${it.message}") }
    }
}
