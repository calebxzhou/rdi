package calebxzau.rdi.render

import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.system.MemoryUtil.NULL

/** Process-wide GLFW lifetime shared by the small off-screen renderers. */
object GlfwRuntime {
    private val lock = Any()
    private var references = 0

    fun acquire(): Lease {
        synchronized(lock) {
            if (references == 0) check(glfwInit()) { "Failed to initialize GLFW" }
            references++
            return Lease()
        }
    }

    /** Creates a hidden 4.5 core context while GLFW's process-global hints are locked. */
    fun createHiddenWindow(title: String): Long = synchronized(lock) {
        check(references > 0) { "A GLFW lease is required before creating a window" }
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwCreateWindow(1, 1, title, NULL, NULL)
    }

    fun destroyWindow(window: Long) = synchronized(lock) {
        if (window != NULL) org.lwjgl.glfw.GLFW.glfwDestroyWindow(window)
    }

    class Lease internal constructor() : AutoCloseable {
        private var closed = false

        override fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                check(references > 0) { "GLFW lease count underflow" }
                references--
                if (references == 0) glfwTerminate()
            }
        }
    }
}
