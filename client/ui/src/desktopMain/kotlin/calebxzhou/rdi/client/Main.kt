package calebxzhou.rdi.client

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import calebxzhou.mykotutils.std.decodeBase64
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.ui.AppNavigation
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskProgress
import calebxzhou.rdi.common.serdesJson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile

val VERTICAL_MODE= System.getProperty("rdi.ui.vertical").toBoolean()
lateinit var ScreenSize: Pair<Dp, Dp>
fun main() {
    clearPackProcDirOnStartup()
    application {
        if(DEBUG){
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT")
        }
        val windowIcon = remember {
            jarResource("icon.png").use { stream ->
                BitmapPainter(stream.readAllBytes().decodeToImageBitmap())
            }
        }

        //账号信息
        System.getProperty("rdi.account")?.let {
            loggedAccount = serdesJson.decodeFromString(it.decodeBase64)
        }
        //JWT
        System.getProperty("rdi.jwt")?.let {
            loggedAccount.jwt = it
        }
        if (loggedAccount.jwt == null && loggedAccount != RAccount.DEFAULT) {
            GlobalScope.launch {
                val jwt = PlayerService.getJwt(loggedAccount.qq, loggedAccount.pwd)
                loggedAccount.jwt = jwt
            }
        }
        // 设置窗口初始大小为屏幕的2/3，并居中显示
        val screen = remember { Toolkit.getDefaultToolkit().screenSize }
        ScreenSize = (screen.width * 2 / 3).dp to  (screen.height * 2 / 3).dp
        val windowState = if(VERTICAL_MODE) rememberWindowState(
            width = (screen.width*1/5).dp ,
            height = ScreenSize.second,
            position = WindowPosition(Alignment.Center)
        )  else rememberWindowState(
            width = ScreenSize.first,
            height = ScreenSize.second,
            position = WindowPosition(Alignment.Center)
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "RDI ${Const.VERSION_NUMBER}",
            icon = windowIcon,
            state = windowState
        ) {
            val initScreenName = System.getProperty("rdi.init.screen")?.trim()
            val startDestination: Any = when (initScreenName) {
                "wd" -> Wardrobe
                "mail" -> Mail
                "hl" -> HostList
                "wl" -> WorldList
                "ml" -> ModpackList
                else -> Login
            }
            AppNavigation(startDestination = startDestination)
        }
    }
}

private fun clearPackProcDirOnStartup()= GlobalScope.launch {
    val packProcDir = ClientDirs.packProcDir
    runCatching {
        packProcDir.deleteRecursivelyNoSymlink()
        packProcDir.mkdirs()
    }
}

private fun installPackDropTarget(
    window: java.awt.Window,
    onOpenTask: (Task) -> Unit
) {
    val uriListFlavor = runCatching { DataFlavor("text/uri-list;class=java.lang.String") }.getOrNull()
    val listener = object : DropTargetAdapter() {
        override fun dragEnter(dtde: DropTargetDragEvent) {
            if (supportsFileDrop(dtde.currentDataFlavors, uriListFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            } else {
                dtde.rejectDrag()
            }
        }

        override fun dragOver(dtde: DropTargetDragEvent) {
            if (supportsFileDrop(dtde.currentDataFlavors, uriListFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            } else {
                dtde.rejectDrag()
            }
        }

        override fun drop(dtde: DropTargetDropEvent) {
            val files = when {
                dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                }
                uriListFlavor != null && dtde.isDataFlavorSupported(uriListFlavor) -> {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val data = dtde.transferable.getTransferData(uriListFlavor) as? String
                    data?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() && !it.startsWith("#") }
                        ?.mapNotNull { line ->
                            runCatching { java.io.File(URI(line)) }.getOrNull()
                        }
                }
                dtde.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val data = dtde.transferable.getTransferData(DataFlavor.stringFlavor) as? String
                    data?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() && !it.startsWith("#") }
                        ?.mapNotNull { line ->
                            runCatching { java.io.File(URI(line)) }.getOrNull()
                        }
                }
                else -> null
            } ?: return
            val packFiles = files.filterIsInstance<java.io.File>()
                .filter { it.name.endsWith(".rdipack", ignoreCase = true) }
            if (packFiles.isEmpty()) return
            val task = if (packFiles.size == 1) {
                buildImportPackTask(packFiles.first())
            } else {
                Task.Sequence(
                    name = "导入整合包",
                    subTasks = packFiles.map { buildImportPackTask(it) }
                )
            }
            onOpenTask(task)
        }
    }
    window.dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, listener, true)
    (window as? java.awt.Container)?.let { container ->
        attachDropTargetRecursively(container, listener)
    }
    (window as? javax.swing.RootPaneContainer)?.let { root ->
        attachDropTargetRecursively(root.contentPane, listener)
        attachDropTargetRecursively(root.glassPane as? java.awt.Container, listener)
    }
}

private fun supportsFileDrop(
    flavors: Array<DataFlavor>,
    uriListFlavor: DataFlavor?
): Boolean {
    return flavors.any { flavor ->
        flavor == DataFlavor.javaFileListFlavor ||
            (uriListFlavor != null && flavor == uriListFlavor) ||
            flavor == DataFlavor.stringFlavor
    }
}

private fun attachDropTargetRecursively(
    container: java.awt.Container?,
    listener: DropTargetAdapter
) {
    if (container == null) return
    container.dropTarget = DropTarget(container, listener)
    container.components.forEach { component ->
        component.dropTarget = DropTarget(component, listener)
        if (component is java.awt.Container) {
            attachDropTargetRecursively(component, listener)
        }
    }
}

private fun buildImportPackTask(zipFile: java.io.File): Task {
    return Task.Leaf("导入 ${zipFile.name}") { ctx ->
        val targetRoot = ClientDirs.mcDir.canonicalFile
        val totalFiles = ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().count { !it.isDirectory }
        }.coerceAtLeast(1)
        var processed = 0
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (name.isEmpty()) return@forEach
                val outFile = targetRoot.resolve(name)
                val normalized = outFile.canonicalFile
                if (!normalized.path.startsWith(targetRoot.path)) return@forEach
                if (entry.isDirectory) {
                    normalized.mkdirs()
                } else {
                    normalized.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(
                            normalized.toPath(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                        ).use { output ->
                            input.copyTo(output)
                        }
                    }
                    processed += 1
                    ctx.emitProgress(
                        TaskProgress("解压 ${entry.name}", processed.toFloat() / totalFiles)
                    )
                }
            }
        }
        ctx.emitProgress(TaskProgress("完成", 1f))
    }
}
