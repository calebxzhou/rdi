package calebxzhou.rdi.client.service

import org.lwjgl.glfw.GLFW.*
import calebxzau.rdi.render.GlfwRuntime
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun getDisplayModes(): List<String> {
    return runCatching {
        GlfwRuntime.acquire().use {
            val monitors = glfwGetMonitors() ?: return@use emptyList()
            val modesList = mutableListOf<String>()
            for (i in 0 until monitors.limit()) {
                val monitor = monitors.get(i)
                val mode = glfwGetVideoMode(monitor) ?: continue
                modesList.add("${mode.width()}x${mode.height()}@${mode.refreshRate()}")
            }
            modesList.distinct()
        }
    }.onFailure { logger.warn(it) { "读取显示器模式失败" } }.getOrElse { emptyList() }
}
