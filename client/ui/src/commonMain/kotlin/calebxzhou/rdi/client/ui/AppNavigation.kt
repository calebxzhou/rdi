package calebxzhou.rdi.client.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import calebxzhou.mykotutils.std.encodeBase64
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.proxy.LocalMcProxy
import calebxzhou.rdi.client.UIFontFamily
import calebxzhou.rdi.client.service.LOCAL_WORLD_BIRD_VIEW_ROUTE_ID
import calebxzhou.rdi.client.service.WorldBirdViewSourceSpec
import calebxzhou.rdi.client.service.WorldBirdViewStore
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.model.McVersion
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * Common Typography using the cross-platform UIFontFamily.
 */
val AppTypography: Typography
    @Composable get() = Typography(defaultFontFamily = UIFontFamily)

private inline fun <reified T : Any> NavHostController.navigateAbsolute(route: T) {
    navigate(route) {
        popUpTo<T> { inclusive = true }
        launchSingleTop = true
        restoreState = false
    }
}

/**
 * Common navigation graph shared between desktop and Android.
 * Desktop-only routes (McPlayView, ModpackUpload) are gated behind isDesktop.
 *
 * @param startDestination the initial route (default: Login)
 * @param onInstallDropTask optional callback for desktop drag-and-drop import (desktop sets this from Main.kt)
 */
@Composable
fun AppNavigation(
    startDestination: Any = Login,
) {
    MaterialTheme(typography = AppTypography) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val openTaskView: (calebxzhou.rdi.common.model.Task, Boolean, (() -> Unit)?, (() -> Unit)?) -> Unit = { task, autoClose, onDone, onBack ->
            TaskStore.current = task
            TaskStore.autoClose = autoClose
            TaskStore.onDone = onDone
            TaskStore.onBack = onBack
            navController.navigate(TaskView)
        }

        // Android FCL launch dialog
        val showFclLaunchDialog = remember { mutableStateOf(false) }
        val fclLaunchArgs = remember { mutableStateOf<McPlayArgs?>(null) }
        val openMcPlay: (McPlayArgs, (() -> Unit)?) -> Unit = { args, onBack ->
            if (isDesktop) {
                McPlayStore.current = args
                McPlayStore.onBack = onBack
                navController.navigate(McPlayView)
            } else {
                fclLaunchArgs.value = args
                showFclLaunchDialog.value = true
            }
        }
        if (showFclLaunchDialog.value) {
            val args = fclLaunchArgs.value
            if (args != null) {
                var jvmArg by remember(args.playArg) { mutableStateOf("") }
                LaunchedEffect(args.playArg) {
                    jvmArg = "-Drdi.play=${args.playArg.withGameAddr(LocalMcProxy.gameAddr).encodeBase64}"
                }
                AlertDialog(
                    onDismissRequest = { showFclLaunchDialog.value = false },
                    title = { Text("在FCL中启动游戏") },
                    text = {
                        Column {
                            Text("0.随意建个离线账户")
                            Text("1.点 管理版本")
                            Text("2.点 公有目录，点击 刷新")
                            Text("3.点击 ${args.versionId}")
                            Text("4.点击 \uF013".asIconText)
                            Text("5.翻到最下面 找到Java虚拟机参数 全部清空")
                            Text("6.粘贴 $jvmArg")
                        }

                    },
                    confirmButton = {
                        TextButton(onClick = {
                            copyToClipboard(jvmArg)
                            openGameLauncher()
                        }) {
                            Text("复制参数并打开FCL")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFclLaunchDialog.value = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable<Login> {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Menu) {
                            popUpTo(Login) { inclusive = true }
                        }
                    },
                    onOpenRegister = { msa -> navController.navigate(Register(msa)) }
                )
            }
            composable<Menu> {
                MenuScreen(
                    onOpenModpackLocalManage = { navController.navigate(ModpackLocalManage) },
                    onOpenMcVersionManage = { navController.navigate(RMcVersion(null)) },
                    onOpenSettings = { navController.navigate(Setting) },
                    onOpenMail = { navController.navigate(Mail) },
                    onOpenHostLobby = { navController.navigate(HostList) },
                    onOpenHostInfo = { hostId -> navController.navigate(HostInfo(hostId)) },
                    onOpenWardrobe = { navController.navigate(Wardrobe) },
                    onOpenWorldList = { navController.navigate(WorldList) },
                    onBack = {
                        navController.navigate(Login) {
                            popUpTo(Menu) { inclusive = true }
                        }
                    }
                )
            }
            composable<Register> {
                val route = it.toRoute<Register>()
                RegisterScreen(
                    route.msa,
                    onBack = { navController.navigateAbsolute(Login) },
                    onRegisterSuccess = {
                        navController.navigateAbsolute(Login)
                    }
                )
            }
            composable<Wardrobe> {
                WardrobeScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenSkinPreview = { skin ->
                        navController.navigate(
                            SkinPreview(
                                tid = skin.tid,
                                name = skin.name,
                                type = skin.type,
                                uploader = skin.uploader,
                                isPublic = skin.public,
                                likes = skin.likes
                            )
                        )
                    }
                )
            }
            composable<SkinPreview> {
                val route = it.toRoute<SkinPreview>()
                SkinPreviewScreen(
                    skin = BSSkinData(
                        tid = route.tid,
                        name = route.name,
                        type = route.type,
                        uploader = route.uploader,
                        public = route.isPublic,
                        likes = route.likes
                    ),
                    onBack = { navController.navigateAbsolute(Wardrobe) }
                )
            }
            composable<Mail> {
                MailScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenDetail = { mailId -> navController.navigate(MailDetail(mailId)) }
                )
            }
            composable<MailDetail> {
                val route = it.toRoute<MailDetail>()
                MailDetailScreen(
                    mailId = route.mailId,
                    onBack = { navController.navigateAbsolute(Mail) }
                )
            }
            composable<HostList> {
                HostListScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenHostAll = { navController.navigate(HostAll) },
                    onOpenHostInfo = { hostId ->
                        navController.navigate(HostInfo(hostId))
                    },
                    onOpenHostCreate = {
                        navController.navigate(HostCreate())
                    },
                    onOpenMcPlay = { args ->
                        openMcPlay(args) { navController.navigateAbsolute(HostList) }
                    },
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(RMcVersion(mcVer?.mcVer))
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(HostList)
                        }
                    }
                )
            }
            composable<ModpackLocalManage> {
                ModpackLocalManageScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(ModpackLocalManage)
                        }
                    },
                    onOpenPlay = { args ->
                        openMcPlay(args) { navController.navigateAbsolute(ModpackLocalManage) }
                    },
                    onOpenModpackList = { navController.navigate(ModpackList) }
                )
            }
            composable<HostAll> {
                HostAllScreen(
                    onBack = { navController.navigateAbsolute(HostList) },
                    onOpenHostInfo = { hostId ->
                        navController.navigate(HostInfo(hostId, fromAllHosts = true))
                    },
                    onOpenMcPlay = { args ->
                        openMcPlay(args) { navController.navigateAbsolute(HostAll) }
                    },
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(RMcVersion(mcVer?.mcVer))
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(HostAll)
                        }
                    }
                )
            }
            composable<HostInfo> {
                val route = it.toRoute<HostInfo>()
                HostInfoScreen(
                    hostId = ObjectId(route.hostId),
                    onBack = {
                        if (route.fromAllHosts) {
                            navController.navigateAbsolute(HostAll)
                        } else {
                            navController.navigateAbsolute(HostList)
                        }
                    },
                    onOpenModpackInfo = { modpackId ->
                        navController.navigate(
                            ModpackInfo(
                                modpackId = modpackId,
                                fromHostId = route.hostId,
                                fromAllHosts = route.fromAllHosts
                            )
                        )
                    },
                    onOpenMcPlay = { args ->
                        openMcPlay(args) {
                            navController.navigateAbsolute(HostInfo(route.hostId, route.fromAllHosts))
                        }
                    },
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(RMcVersion(mcVer?.mcVer))
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(HostInfo(route.hostId, route.fromAllHosts))
                        }
                    },
                    onOpenHostEdit = { host ->
                        navController.navigate(
                            HostCreate(
                                hostId = host._id.toHexString(),
                                fromAllHosts = route.fromAllHosts
                            )
                        )
                    }
                )
            }
            composable<WorldList> {
                WorldListScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenBirdView = { worldId ->
                        WorldBirdViewStore.current = WorldBirdViewSourceSpec.Remote(worldId)
                        navController.navigate(WorldBirdView(worldId))
                    },
                    onOpenLocalBirdView = {
                        scope.launch {
                            val rootPath = pickLocalMinecraftWorldDir() ?: return@launch
                            WorldBirdViewStore.current = WorldBirdViewSourceSpec.Local(rootPath)
                            navController.navigate(WorldBirdView(LOCAL_WORLD_BIRD_VIEW_ROUTE_ID))
                        }
                    }
                )
            }
            composable<WorldBirdView> {
                val route = it.toRoute<WorldBirdView>()
                val sourceSpec = when (route.worldId) {
                    LOCAL_WORLD_BIRD_VIEW_ROUTE_ID -> {
                        (WorldBirdViewStore.current as? WorldBirdViewSourceSpec.Local)
                            ?: WorldBirdViewSourceSpec.Remote(route.worldId)
                    }
                    else -> WorldBirdViewSourceSpec.Remote(route.worldId)
                }
                WorldBirdViewScreen(
                    sourceSpec = sourceSpec,
                    onBack = { navController.navigateAbsolute(WorldList) }
                )
            }
            composable<HostCreate> {
                val route = it.toRoute<HostCreate>()
                HostNewCreateScreen(
                    route,
                    onBack = {
                        if (route.hostId != null) {
                            navController.navigateAbsolute(HostInfo(route.hostId, route.fromAllHosts))
                        } else {
                            navController.navigateAbsolute(HostList)
                        }
                    },
                    onNavigateProfile = { navController.navigateAbsolute(HostList) }
                )
            }
            composable<TaskView> {
                val task = TaskStore.current
                if (task != null) {
                    TaskScreen(
                        task = task,
                        autoClose = TaskStore.autoClose,
                        onBack = {
                            val back = TaskStore.onBack ?: { navController.navigateAbsolute(Menu) }
                            TaskStore.current = null
                            TaskStore.onBack = null
                            TaskStore.onDone = null
                            TaskStore.autoClose = false
                            back.invoke()
                        },
                        onDone = {
                            TaskStore.onDone?.invoke()
                            TaskStore.onDone = null
                        }
                    )
                } else {
                    Text("没有可显示的任务")
                }
            }
            // Desktop-only routes (McPlayView, ModpackUpload) are added via expect/actual
            addDesktopOnlyRoutes(navController)
            composable<Setting> {
                SettingScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                )
            }
            composable<ModpackList> {
                ModpackListScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenUpload = { navController.navigate(ModpackUpload) },
                    onOpenTask = { task, autoClose, onDone ->
                        openTaskView(task, autoClose, onDone) {
                            navController.navigateAbsolute(ModpackList)
                        }
                    },
                    onOpenMcVersions = { navController.navigate(RMcVersion(null)) },
                    onOpenInfo = { modpackId ->
                        navController.navigate(ModpackInfo(modpackId))
                    }
                )
            }
            composable<ModpackInfo> {
                val route = it.toRoute<ModpackInfo>()
                ModpackInfoScreen(
                    modpackId = route.modpackId,
                    onBack = {
                        if (route.fromHostId != null) {
                            navController.navigateAbsolute(HostInfo(route.fromHostId, route.fromAllHosts))
                        } else {
                            navController.navigateAbsolute(ModpackList)
                        }
                    },
                    onOpenUpload = { modpackId, modpackName ->
                        navController.navigate(ModpackUpload)
                    },
                    onCreateHost = { _, _, _, _ ->
                        navController.navigate(HostCreate())
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(
                                ModpackInfo(route.modpackId, route.fromHostId, route.fromAllHosts)
                            )
                        }
                    }
                )
            }

            composable<RMcVersion> {
                val route = it.toRoute<RMcVersion>()
                val required = route.mcVer?.let { ver -> McVersion.from(ver) }
                McVersionScreen(
                    requiredMcVer = required,
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenTask = { task ->
                        openTaskView(task, false, null) {
                            navController.navigateAbsolute(RMcVersion(route.mcVer))
                        }
                    }
                )
            }
        }
    }
}

private fun String.withGameAddr(gameAddr: String): String {
    val lines = split(Regex("\\r?\\n")).toMutableList()
    require(lines.size >= 2) { "RDI参数错误，请重新启动地图" }
    lines[1] = gameAddr
    return lines.joinToString("\n")
}
