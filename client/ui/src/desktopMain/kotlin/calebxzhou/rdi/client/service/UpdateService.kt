package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.net.downloadFileFrom
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object UpdateService {
    private val lgr by Loggers
    private const val UI_LIB_STAGING_DIR_NAME = ".ui-update-staging"
    private const val UI_LIB_UPDATES_DIR_NAME = "updates"
    private const val UI_LIB_DELETE_LIST_FILE_NAME = "delete-list.txt"
    private const val UPDATE_MAIN_CLASS = "calebxzhou.rdi.client.MainKt"

    suspend fun startUpdateFlow(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit,
        onRestart: (suspend () -> Unit)? = null
    ) {
        runCatching {
            onStatus("正在检查更新...")
            val mcTargets = McVersion.entries.filter { it.enabled }.flatMap { mcVer ->
                mcVer.loaderVersions.keys.map { loader ->
                    val slug = "${mcVer.mcVer}-${loader.name.lowercase()}"
                    slug to DL_MOD_DIR.resolve("rdi-5-mc-client-$slug.jar").absoluteFile
                }
            }

            mcTargets.forEach { (slug, mcFile) ->
                val mcHash = server.makeRequest<String>("update/mc/$slug/hash").data
                    ?: throw RequestError("获取MC核心版本信息失败: $slug")

                val mcNeedsUpdate = !mcFile.exists() || mcFile.sha1 != mcHash
                if (mcNeedsUpdate) {
                    onStatus("准备下载 ${mcFile.name}...")
                    val mcUpdated = downloadAndReplaceCore(
                        targetFile = mcFile,
                        downloadUrl = "${server.hqUrl}/update/mc/$slug",
                        expectedSha = mcHash,
                        label = mcFile.name,
                        onDetail = onDetail
                    )
                    if (!mcUpdated) {
                        onStatus("更新失败，请检查网络")
                        return@runCatching
                    }
                    onStatus("${mcFile.name} 更新完成")
                }
            }

            val uiSync = syncUiLibs(onStatus, onDetail)
            if (!uiSync.first) {
                onStatus("更新失败，请检查网络")
                return@runCatching
            }
            // UI库更新改为暂存并在退出后应用，避免运行中替换类路径导致崩溃
            if (uiSync.second) {
                onStatus("更新包已准备完成，重启后应用")
                val prepared = prepareUiLibApplyOnExit(onDetail)
                if (!prepared) {
                    onStatus("更新失败，请检查环境")
                    return@runCatching
                }
                onStatus("更新完成，需要重启")
                onRestart?.invoke()
                return@runCatching
            }
            onStatus("当前已是最新版核心")
            onDetail("")
        }.onFailure {
            onStatus("更新流程遇到错误")
            it.printStackTrace()
            onDetail(it.message ?: "未知错误")
        }
    }

    private suspend fun downloadAndReplaceCore(
        targetFile: File,
        downloadUrl: String,
        expectedSha: String,
        label: String,
        onDetail: (String) -> Unit
    ): Boolean {
        val parentDir = targetFile.absoluteFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()
        val tempFile = File(parentDir, "${targetFile.name}.downloading.${System.currentTimeMillis()}")

        tempFile.toPath().downloadFileFrom(downloadUrl) { dl ->
            val totalBytes = dl.totalBytes
            val downloadedBytes = dl.bytesDownloaded.takeIf { it >= 0 } ?: 0L
            val percentValue = when {
                totalBytes > 0 -> downloadedBytes * 100.0 / totalBytes
                dl.fraction >= 0 -> dl.fraction*100.0
                else -> -1.0
            }

            val percentText = percentValue.takeIf { it >= 0 }
                ?.let { String.format("%.1f%%", it) } ?: "--"
            val downloadedText = downloadedBytes.takeIf { it > 0 }?.humanFileSize ?: "0B"
            val totalText = totalBytes.takeIf { it > 0 }?.humanFileSize ?: "--"
            val speedText = dl.speedBytesPerSecond.takeIf { it > 0 }
                ?.let { "${it / 1000}KB/s" } ?: "--"
            onDetail("$label $percentText $downloadedText/$totalText $speedText")
        }.getOrElse {
            it.printStackTrace()
            tempFile.delete()
            onDetail("下载失败，请检查网络后重试")
            return false
        }

        val downloadedSha = tempFile.sha1
        if (!downloadedSha.equals(expectedSha, true)) {
            tempFile.delete()
            onDetail("文件损坏了，请重下")
            return false
        }

        fun deleteWithRetry(file: File, retries: Int = 5, delayMs: Long = 200): Boolean {
            repeat(retries) {
                if (!file.exists() || file.delete()) return true
                Thread.sleep(delayMs)
            }
            return !file.exists()
        }

        fun tryOverwriteEvenIfLocked(src: File, dst: File): Boolean = runCatching {
            FileInputStream(src).channel.use { inCh ->
                FileOutputStream(dst, false).channel.use { outCh ->
                    outCh.truncate(0)
                    var pos = 0L
                    val size = inCh.size()
                    while (pos < size) {
                        val transferred = inCh.transferTo(pos, 1024 * 1024, outCh)
                        if (transferred <= 0) break
                        pos += transferred
                    }
                }
            }
            true
        }.getOrElse { false }

        val backupFile = if (targetFile.exists()) File(
            parentDir,
            "${targetFile.name}.backup.${System.currentTimeMillis()}"
        ) else null

        val replaced = runCatching {
            backupFile?.let { targetFile.copyTo(it, overwrite = true) }

            if (targetFile.exists() && !deleteWithRetry(targetFile)) {
                targetFile.deleteOnExit()
                val overwritten = tryOverwriteEvenIfLocked(tempFile, targetFile)
                if (!overwritten) {
                    throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
                }
                tempFile.delete()
                return@runCatching
            }

            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }.recoverCatching {
            if (targetFile.exists() && !deleteWithRetry(targetFile)) {
                targetFile.deleteOnExit()
                if (!tryOverwriteEvenIfLocked(tempFile, targetFile)) {
                    throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
                }
                tempFile.delete()
            } else {
                Files.copy(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                tempFile.delete()
            }
        }.isSuccess

        if (replaced) {
            backupFile?.delete()
            onDetail("核心文件已更新至最新版本。")
        }

        return replaced
    }

    private suspend fun syncUiLibs(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit
    ): Pair<Boolean, Boolean> {
        val libDir = File("lib").absoluteFile
        if (!libDir.exists()) libDir.mkdirs()
        val stageDir = File(libDir, UI_LIB_STAGING_DIR_NAME).absoluteFile
        val updatesDir = File(stageDir, UI_LIB_UPDATES_DIR_NAME).absoluteFile
        if (!updatesDir.exists()) updatesDir.mkdirs()
        val deleteListFile = File(stageDir, UI_LIB_DELETE_LIST_FILE_NAME).absoluteFile

        val serverEntries = server.makeRequest<Map<String, String>>("update/ui/libs").data
            ?: throw RequestError("获取UI库信息失败")
        var updated = false

        serverEntries.forEach { (name, sha) ->
            val localFile = File(libDir, name)
            val stagedFile = File(updatesDir, name)
            stagedFile.parentFile?.mkdirs()
            val localUpToDate = localFile.exists() && localFile.sha1.equals(sha, true)
            val stagedUpToDate = stagedFile.exists() && stagedFile.sha1.equals(sha, true)
            val needsUpdate = !localUpToDate && !stagedUpToDate
            if (needsUpdate) {
                onStatus("准备下载 $name...")
                val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val ok = downloadAndReplaceCore(
                    targetFile = stagedFile,
                    downloadUrl = "${server.hqUrl}/update/ui/lib/$encodedName",
                    expectedSha = sha,
                    label = name,
                    onDetail = onDetail
                )
                if (!ok) return false to updated
                updated = true
                onStatus("$name 下载完成，将在重启后应用")
            }
        }

        val serverNames = serverEntries.keys
        lgr.info { "服务器库列表: $serverNames" }
        val allLocalFiles = libDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        lgr.info { "本地库列表: $allLocalFiles" }
        val extraFiles = libDir.listFiles()
            ?.filter { it.isFile && it.name !in serverNames }
            ?: emptyList()
        val extraNames = extraFiles.map { it.name }
        lgr.info { "需要删除的库: $extraNames" }

        if (extraFiles.isNotEmpty()) {
            stageDir.mkdirs()
            deleteListFile.writeText(extraNames.joinToString("\n"))
            onStatus("已记录${extraFiles.size}个多余库文件，将在重启后清理")
            onDetail("等待重启后删除: ${extraNames.joinToString(", ")}")
            updated = true
        } else if (deleteListFile.exists()) {
            deleteListFile.delete()
        }

        val pendingStagedFiles = updatesDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val pendingDeleteNames = readPendingDeleteNames(deleteListFile)
        val restartRequired = pendingStagedFiles.isNotEmpty() || pendingDeleteNames.isNotEmpty()
        if (restartRequired) {
            onDetail("等待重启应用更新，待应用文件${pendingStagedFiles.size}个，待删除文件${pendingDeleteNames.size}个")
        }

        return true to restartRequired
    }

    private fun prepareUiLibApplyOnExit(onDetail: (String) -> Unit): Boolean {
        val libDir = File("lib").absoluteFile
        val stageDir = File(libDir, UI_LIB_STAGING_DIR_NAME).absoluteFile
        val updatesDir = File(stageDir, UI_LIB_UPDATES_DIR_NAME).absoluteFile
        val deleteListFile = File(stageDir, UI_LIB_DELETE_LIST_FILE_NAME).absoluteFile
        val hasPendingUpdates = updatesDir.listFiles()?.any { it.isFile } == true
        val hasPendingDeletes = readPendingDeleteNames(deleteListFile).isNotEmpty()
        if (!hasPendingUpdates && !hasPendingDeletes) return true

        return if (isWindows()) {
            prepareUiLibApplyOnExitWindows(stageDir, libDir, onDetail)
        } else {
            prepareUiLibApplyOnExitPosix(stageDir, libDir, onDetail)
        }
    }

    private fun prepareUiLibApplyOnExitWindows(
        stageDir: File,
        libDir: File,
        onDetail: (String) -> Unit
    ): Boolean {
        val javaPath = resolveJavaExecutableForRestart() ?: run {
            onDetail("未找到Java可执行文件，无法自动重启")
            return false
        }
        val classPath = System.getProperty("java.class.path") ?: run {
            onDetail("未读取到类路径，无法自动重启")
            return false
        }
        val workDir = File(".").absoluteFile
        val pid = ProcessHandle.current().pid().toString()
        val script = File.createTempFile("rdi-ui-apply-", ".ps1").absoluteFile
        script.writeText(
            """
            param(
              [long]__D__PidToWait,
              [string]__D__StageDir,
              [string]__D__LibDir,
              [string]__D__JavaPath,
              [string]__D__Classpath,
              [string]__D__MainClass,
              [string]__D__WorkDir
            )
            __D__ErrorActionPreference = 'SilentlyContinue'
            for (__D__i = 0; __D__i -lt 1200; __D__i++) {
              __D__proc = Get-Process -Id __D__PidToWait -ErrorAction SilentlyContinue
              if (__D__null -eq __D__proc) { break }
              Start-Sleep -Milliseconds 250
            }
            __D__updatesDir = Join-Path __D__StageDir 'updates'
            if (Test-Path __D__updatesDir) {
              Get-ChildItem -Path __D__updatesDir -File | ForEach-Object {
                __D__target = Join-Path __D__LibDir __D___.Name
                Move-Item -Path __D___.FullName -Destination __D__target -Force
              }
            }
            __D__deleteList = Join-Path __D__StageDir 'delete-list.txt'
            if (Test-Path __D__deleteList) {
              Get-Content -Path __D__deleteList | ForEach-Object {
                __D__name = __D___.Trim()
                if ([string]::IsNullOrWhiteSpace(__D__name)) { return }
                __D__target = Join-Path __D__LibDir __D__name
                if (Test-Path __D__target) { Remove-Item -Path __D__target -Force -ErrorAction SilentlyContinue }
              }
            }
            Remove-Item -Path __D__StageDir -Recurse -Force -ErrorAction SilentlyContinue
            Start-Process -FilePath __D__JavaPath -WorkingDirectory __D__WorkDir -ArgumentList @('-cp', __D__Classpath, __D__MainClass)
            """.trimIndent().replace("__D__", "$")
        )

        val psCommands = listOf("powershell", "pwsh")
        val started = psCommands.any { cmd ->
            runCatching {
                ProcessBuilder(
                    cmd,
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.absolutePath,
                    "-PidToWait",
                    pid,
                    "-StageDir",
                    stageDir.absolutePath,
                    "-LibDir",
                    libDir.absolutePath,
                    "-JavaPath",
                    javaPath.absolutePath,
                    "-Classpath",
                    classPath,
                    "-MainClass",
                    UPDATE_MAIN_CLASS,
                    "-WorkDir",
                    workDir.absolutePath
                ).start()
            }.isSuccess
        }
        if (!started) {
            onDetail("无法启动PowerShell自动应用更新，请手动重启")
        }
        return started
    }

    private fun prepareUiLibApplyOnExitPosix(
        stageDir: File,
        libDir: File,
        onDetail: (String) -> Unit
    ): Boolean {
        val javaPath = resolveJavaExecutableForRestart() ?: run {
            onDetail("未找到Java可执行文件，无法自动重启")
            return false
        }
        val classPath = System.getProperty("java.class.path") ?: run {
            onDetail("未读取到类路径，无法自动重启")
            return false
        }
        val workDir = File(".").absoluteFile
        val pid = ProcessHandle.current().pid().toString()
        val script = File.createTempFile("rdi-ui-apply-", ".sh").absoluteFile
        script.writeText(
            """
            #!/bin/sh
            PID_TO_WAIT="__D__1"
            STAGE_DIR="__D__2"
            LIB_DIR="__D__3"
            JAVA_PATH="__D__4"
            CLASSPATH="__D__5"
            MAIN_CLASS="__D__6"
            WORK_DIR="__D__7"
            while kill -0 "__D__PID_TO_WAIT" 2>/dev/null; do
              sleep 0.2
            done
            UPDATES_DIR="__D__STAGE_DIR/updates"
            if [ -d "__D__UPDATES_DIR" ]; then
              for f in "__D__UPDATES_DIR"/*; do
                [ -f "__D__f" ] || continue
                mv -f "__D__f" "__D__LIB_DIR/__D__(basename "__D__f")"
              done
            fi
            if [ -f "__D__STAGE_DIR/delete-list.txt" ]; then
              while IFS= read -r name; do
                [ -n "__D__name" ] || continue
                rm -f "__D__LIB_DIR/__D__name"
              done < "__D__STAGE_DIR/delete-list.txt"
            fi
            rm -rf "__D__STAGE_DIR"
            cd "__D__WORK_DIR" || exit 0
            nohup "__D__JAVA_PATH" -cp "__D__CLASSPATH" "__D__MAIN_CLASS" >/dev/null 2>&1 &
            """.trimIndent().replace("__D__", "$")
        )
        script.setExecutable(true)
        val started = runCatching {
            ProcessBuilder(
                "sh",
                script.absolutePath,
                pid,
                stageDir.absolutePath,
                libDir.absolutePath,
                javaPath.absolutePath,
                classPath,
                UPDATE_MAIN_CLASS,
                workDir.absolutePath
            ).start()
        }.isSuccess
        if (!started) {
            onDetail("无法启动shell自动应用更新，请手动重启")
        }
        return started
    }

    private fun resolveJavaExecutableForRestart(): File? {
        val javaHome = File(System.getProperty("java.home"))
        val isWin = isWindows()
        val preferred = if (isWin) {
            javaHome.resolve("bin").resolve("javaw.exe")
        } else {
            javaHome.resolve("bin").resolve("java")
        }
        if (preferred.exists()) return preferred
        val fallback = if (isWin) javaHome.resolve("bin").resolve("java.exe") else preferred
        return fallback.takeIf { it.exists() }
    }

    private fun readPendingDeleteNames(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }
}

