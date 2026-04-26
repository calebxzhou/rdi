package calebxzhou.rdi.client.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Typography
import androidx.compose.material3.MaterialTheme as MaterialTheme3
import androidx.compose.material3.Typography as Typography3
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
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

private fun TextStyle.withUiFontFamily() = copy(fontFamily = UIFontFamily)

val AppTypography3: Typography3
    get() = Typography3().run {
        copy(
            displayLarge = displayLarge.withUiFontFamily(),
            displayMedium = displayMedium.withUiFontFamily(),
            displaySmall = displaySmall.withUiFontFamily(),
            headlineLarge = headlineLarge.withUiFontFamily(),
            headlineMedium = headlineMedium.withUiFontFamily(),
            headlineSmall = headlineSmall.withUiFontFamily(),
            titleLarge = titleLarge.withUiFontFamily(),
            titleMedium = titleMedium.withUiFontFamily(),
            titleSmall = titleSmall.withUiFontFamily(),
            bodyLarge = bodyLarge.withUiFontFamily(),
            bodyMedium = bodyMedium.withUiFontFamily(),
            bodySmall = bodySmall.withUiFontFamily(),
            labelLarge = labelLarge.withUiFontFamily(),
            labelMedium = labelMedium.withUiFontFamily(),
            labelSmall = labelSmall.withUiFontFamily()
        )
    }

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
        MaterialTheme3(typography = AppTypography3) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
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
                    onOpenResources = { navController.navigate(ResourceRoute(ResourceTab.All.name)) },
                    onOpenSponsor = { navController.navigate(Sponsor) },
                    onOpenTaskList = { navController.navigate(TaskList()) },
                    onOpenMcConsole = {
                        McPlayStore.openConsoleOnly = true
                        navController.navigate(McPlayView)
                    },
                    onOpenSettings = { navController.navigate(Setting) },
                    onOpenMail = { navController.navigate(HostRoute(HostTab.Mail.name)) },
                    onOpenHostLobby = { navController.navigate(HostRoute(HostTab.MyHosts.name)) },
                    onOpenHostInfo = { hostId -> navController.navigate(HostInfo(hostId)) },
                    onOpenWardrobe = { navController.navigate(Wardrobe) },
                    onOpenWorldList = { navController.navigate(HostRoute(HostTab.Worlds.name)) },
                    onBack = {
                        navController.navigate(Login) {
                            popUpTo(Menu) { inclusive = true }
                        }
                    }
                )
            }
            composable<Sponsor> {
                SponsorScreen(
                    onBack = { navController.navigateAbsolute(Menu) }
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
            composable<MailDetail> {
                val route = it.toRoute<MailDetail>()
                MailDetailScreen(
                    mailId = route.mailId,
                    onBack = { navController.navigateAbsolute(HostRoute(HostTab.Mail.name)) }
                )
            }
            composable<HostRoute> {
                val route = it.toRoute<HostRoute>()
                HostListScreen(
                    initialTab = HostTab.fromRouteValue(route.tab),
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenHostInfo = { hostId, fromAllHosts ->
                        navController.navigate(HostInfo(hostId, fromAllHosts))
                    },
                    onOpenHostCreate = {
                        navController.navigate(HostCreate())
                    },
                    onOpenMcPlay = { args ->
                        openMcPlay(args) { navController.navigateAbsolute(route) }
                    },
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(ResourceRoute(ResourceTab.McResources.name, mcVer?.mcVer))
                    },
                    onOpenMailDetail = { mailId ->
                        navController.navigate(MailDetail(mailId))
                    },
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
                    },
                    onOpenTaskList = { runId ->
                        navController.navigate(TaskList(runId))
                    }
                )
            }
            composable<HostInfo> {
                val route = it.toRoute<HostInfo>()
                HostInfoScreen(
                    hostId = ObjectId(route.hostId),
                    onBack = {
                        if (route.fromAllHosts) {
                            navController.navigateAbsolute(HostRoute(HostTab.AllHosts.name))
                        } else {
                            navController.navigateAbsolute(HostRoute(HostTab.MyHosts.name))
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
                        navController.navigate(ResourceRoute(ResourceTab.McResources.name, mcVer?.mcVer))
                    },
                    onOpenHostEdit = { host ->
                        navController.navigate(
                            HostCreate(
                                hostId = host._id.toHexString(),
                                fromAllHosts = route.fromAllHosts
                            )
                        )
                    },
                    onOpenTaskList = { runId ->
                        navController.navigate(TaskList(runId))
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
                    onBack = { navController.navigateAbsolute(HostRoute(HostTab.Worlds.name)) }
                )
            }
            composable<TaskList> {
                val route = it.toRoute<TaskList>()
                TaskListScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                    initialSelectedRunId = route.selectedRunId
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
                            navController.navigateAbsolute(HostRoute(HostTab.MyHosts.name))
                        }
                    },
                    onNavigateProfile = { navController.navigateAbsolute(HostRoute(HostTab.MyHosts.name)) }
                )
            }
            // Desktop-only routes (McPlayView, ModpackUpload) are added via expect/actual
            addDesktopOnlyRoutes(navController)
            composable<Setting> {
                SettingScreen(
                    onBack = { navController.navigateAbsolute(Menu) },
                )
            }
            composable<ResourceRoute> {
                val route = it.toRoute<ResourceRoute>()
                ResourceScreen(
                    initialCategory = ResourceTab.fromRouteValue(route.tab),
                    requiredMcVer = route.requiredMcVer?.let(McVersion::from),
                    onBack = { navController.navigateAbsolute(Menu) },
                    onOpenUpload = { navController.navigate(ModpackUpload) },
                    onOpenInfo = { modpackId ->
                        navController.navigate(ModpackInfo(modpackId))
                    },
                    onOpenPlay = { args ->
                        openMcPlay(args) { navController.navigateAbsolute(route) }
                    },
                    onOpenTaskList = { runId ->
                        navController.navigate(TaskList(runId))
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
                            navController.navigateAbsolute(ResourceRoute())
                        }
                    },
                    onOpenTaskList = { runId ->
                        navController.navigate(TaskList(runId))
                    },
                    onOpenVersionEdit = { verName ->
                        navController.navigate(
                            ModpackVersionEdit(
                                modpackId = route.modpackId,
                                verName = verName,
                                fromHostId = route.fromHostId,
                                fromAllHosts = route.fromAllHosts
                            )
                        )
                    },
                    onCreateHost = { _, _, _, _ -> 
                        navController.navigate(HostCreate())
                    }
                )
            }
            composable<ModpackVersionEdit> {
                val route = it.toRoute<ModpackVersionEdit>()
                ModpackVersionEditScreen(
                    modpackId = route.modpackId,
                    verName = route.verName,
                    onBack = {
                        navController.navigateAbsolute(
                            ModpackInfo(
                                modpackId = route.modpackId,
                                fromHostId = route.fromHostId,
                                fromAllHosts = route.fromAllHosts
                            )
                        )
                    }
                )
            }

        }
        }
    }
}

private fun String.withGameAddr(gameAddr: String): String {
    val lines = split(Regex("\\r?\\n")).toMutableList()
    require(lines.size >= 2) { "RDI参数错误，请重新启动房间" }
    lines[1] = gameAddr
    return lines.joinToString("\n")
}
