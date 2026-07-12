package calebxzhou.rdi.client.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.model.McVersion
import org.bson.types.ObjectId

private const val SCREEN_FADE_DURATION_MS = 500

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
        val navController = rememberNavController()
        val openMcPlay: (McPlayArgs, (() -> Unit)?) -> Unit = { args, onBack ->
            if (isDesktop) {
                McPlayStore.pendingLaunch = args
                McPlayStore.onBack = onBack
                navController.navigate(McPlayView)
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(animationSpec = tween(SCREEN_FADE_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(SCREEN_FADE_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(SCREEN_FADE_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(SCREEN_FADE_DURATION_MS)) }
        ) {
            composable<Login> {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Menu) {
                            popUpTo(Login) { inclusive = true }
                        }
                    },
                    onOpenRegister = { msa -> navController.navigate(Register(msa)) },
                    onOpenResetPassword = { navController.navigate(ResetPassword) }
                )
            }
            composable<Menu> {
                MenuScreen(
                    onOpenResources = { navController.navigate(ResourceRoute(ResourceTab.All.name)) },
                    onOpenMcmod = { navController.navigate(Mcmod) },
                    onOpenSponsor = { navController.navigate(Sponsor) },
                    onOpenAiChat = { mcpPort, versionDir -> /*navController.navigate(AiChat(mcpPort, versionDir))*/ },
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
            composable<Mcmod> {
                McmodScreen(
                    onBack = { navController.navigateAbsolute(Menu) }
                )
            }
            composable<Sponsor> {
                SponsorScreen(
                    onBack = { navController.navigateAbsolute(Menu) }
                )
            }
            /*composable<AiChat> {
                val route = it.toRoute<AiChat>()
                AiChatScreen(
                    mcpPort = route.mcpPort,
                    versionDir = route.versionDir,
                    chatId = route.chatId,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigateAbsolute(Menu)
                        }
                    },
                    onOpenSettings = { navController.navigate(Setting) }
                )
            }*/
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
            composable<ResetPassword> {
                ResetPasswordScreen(
                    onBack = { navController.navigateAbsolute(Login) },
                    onResetSuccess = { navController.navigateAbsolute(Login) }
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
                    onOpenResourceMods = { mcVer, hostId, fromAllHosts ->
                        navController.navigate(
                            ResourceRoute(
                                tab = ResourceTab.Mods.name,
                                requiredMcVer = mcVer?.mcVer,
                                fromHostId = hostId,
                                fromAllHosts = fromAllHosts
                            )
                        )
                    },
                    onOpenBirdView = { worldId ->
                        /*WorldBirdViewStore.current = WorldBirdViewSourceSpec.Remote(worldId)
                        navController.navigate(WorldBirdView(worldId))*/
                    },
                    onOpenLocalBirdView = {
                        /*scope.launch {
                            val rootPath = pickLocalMinecraftWorldDir() ?: return@launch
                            WorldBirdViewStore.current = WorldBirdViewSourceSpec.Local(rootPath)
                            navController.navigate(WorldBirdView(LOCAL_WORLD_BIRD_VIEW_ROUTE_ID))
                        }*/
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
            /*composable<WorldBirdView> {
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
            }*/
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
                    targetHostId = route.fromHostId?.let(::ObjectId),
                    onBack = {
                        val fromHostId = route.fromHostId
                        if (fromHostId != null) {
                            navController.navigateAbsolute(HostInfo(fromHostId, route.fromAllHosts))
                        } else {
                            navController.navigateAbsolute(Menu)
                        }
                    },
                    onOpenUpload = { navController.navigate(ModpackUpload) },
                    onOpenModpackInfo = { modpackId ->
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

private fun String.withGameAddr(gameAddr: String): String {
    val lines = split(Regex("\\r?\\n")).toMutableList()
    require(lines.size >= 2) { "RDI参数错误，请重新启动房间" }
    lines[1] = gameAddr
    return lines.joinToString("\n")
}
