package calebxzhou.rdi.client.service

import org.lwjgl.glfw.GLFW.*

fun getDisplayModes(): List<String> {
    if (!glfwInit()) {
        return emptyList()
    }

    val monitors = glfwGetMonitors() ?: return emptyList()
    val modesList = mutableListOf<String>()

    for (i in 0 until monitors.limit()) {
        val monitor = monitors.get(i)
        val mode = glfwGetVideoMode(monitor) ?: continue
        modesList.add("${mode.width()}x${mode.height()}@${mode.refreshRate()}")
    }

    return modesList.distinct()
}
