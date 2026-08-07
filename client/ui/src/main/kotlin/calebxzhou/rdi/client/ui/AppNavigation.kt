package calebxzhou.rdi.client.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import calebxzau.rdi.client.blessingskin.BlessingSkinClient
import calebxzau.rdi.client.ui.screen.PlayerInfoScreen
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.screen.ModpackVersionEditScreen
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import org.bson.types.ObjectId

private const val SCREEN_FADE_DURATION_MS = 500
private const val BLESSING_SKIN_BASE_URL = "https://littleskin.cn"

inline fun <reified T : Any> NavHostController.navigateAbsolute(route: T) {
    navigate(route) {
        popUpTo<T> { inclusive = true }
        launchSingleTop = true
        restoreState = false
    }
}

fun NavHostController.navigateRoot(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
        restoreState = false
    }
}

/** Main application navigation graph. */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modCatalog: ModCatalog,
    startDestination: Any = Login,
) {
    val blessingSkin = remember { BlessingSkinClient(BLESSING_SKIN_BASE_URL) }
    val openMcPlay: (McPlayArgs, (() -> Unit)?) -> Unit = { args, onBack ->
        McPlayStore.pendingLaunch = args
        McPlayStore.onBack = onBack
        navController.navigate(McPlayView)
    }
    val returnToHostList: (Boolean) -> Unit = { fromAllHosts ->
        if (!navController.popBackStack()) {
            val tab = if (fromAllHosts) HostTab.AllHosts else HostTab.MyHosts
            navController.navigateAbsolute(HostRoute(tab.name))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
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
                onOpenHostLobby = { navController.navigate(HostRoute(HostTab.MyHosts.name)) },
                onOpenHost2Lobby = { navController.navigate(Host2Lobby) },
                onOpenWardrobe = { navController.navigate(Wardrobe) },
                onOpenMcPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(Menu) }
                },
                onOpenMcVersions = { mcVersion ->
                    navController.navigate(ResourceRoute(ResourceTab.McResources.name, mcVersion?.mcVer))
                },
                onOpenTaskList = { runId ->
                    navController.navigate(TaskList(selectedRunId = runId))
                }
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
                blessingSkin = blessingSkin,
                onBack = { navController.navigateAbsolute(Menu) },
                onOpenSkinPreview = { textureId ->
                    navController.navigate(SkinPreview(textureId))
                }
            )
        }
        composable<SkinPreview> {
            val route = it.toRoute<SkinPreview>()
            SkinPreviewScreen(
                blessingSkin = blessingSkin,
                textureId = route.textureId,
                onBack = { navController.navigateAbsolute(Wardrobe) }
            )
        }
        composable<MailDetail> {
            val route = it.toRoute<MailDetail>()
            MailDetailScreen(
                mailId = route.mailId,
                onBack = { navController.navigateAbsolute(Mailbox) }
            )
        }
        composable<Mailbox> {
            MailScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateRoot(if (AccountSessionStore.isLoggedIn) Menu else Login)
                    }
                }
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
                onOpenHostMembers = { hostId, fromAllHosts ->
                    navController.navigate(HostMembers(hostId, fromAllHosts))
                },
                onOpenHostMods = { hostId, fromAllHosts ->
                    navController.navigate(HostMods(hostId, fromAllHosts))
                },
                onOpenHostFiles = { hostId, fromAllHosts ->
                    navController.navigate(HostFiles(hostId, fromAllHosts))
                },
                onOpenHostBackend = { hostId, fromAllHosts ->
                    navController.navigate(HostBackend(hostId, fromAllHosts))
                },
                onOpenHostSettings = { hostId, fromAllHosts ->
                    navController.navigate(HostCreate(hostId, fromAllHosts))
                },
                onOpenMcPlay = { args, fromAllHosts ->
                    val tab = if (fromAllHosts) HostTab.AllHosts else HostTab.MyHosts
                    openMcPlay(args) { navController.navigateAbsolute(HostRoute(tab.name)) }
                },
                onOpenMcVersions = { mcVer, _ ->
                    navController.navigate(ResourceRoute(ResourceTab.McResources.name, mcVer?.mcVer))
                },
                onOpenHostCreate = {
                    navController.navigate(HostCreate())
                },
                /*
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
                */
                onOpenTaskList = { runId ->
                    navController.navigate(TaskList(selectedRunId = runId, fromHostTab = route.tab))
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
                }
            )
        }
        composable<HostMembers> {
            val route = it.toRoute<HostMembers>()
            HostMembersScreen(
                hostId = ObjectId(route.hostId),
                onBack = { returnToHostList(route.fromAllHosts) },
                onQuit = { returnToHostList(route.fromAllHosts) }
            )
        }
        composable<HostMods> {
            val route = it.toRoute<HostMods>()
            HostModsScreen(
                modCatalog = modCatalog,
                hostId = ObjectId(route.hostId),
                onBack = { returnToHostList(route.fromAllHosts) },
                onOpenResourceMods = { mcVersion, modLoader ->
                    navController.navigate(
                        ResourceRoute(
                            tab = ResourceTab.Mods.name,
                            requiredMcVer = mcVersion.mcVer,
                            requiredLoader = modLoader.name,
                            fromHostId = route.hostId,
                            fromAllHosts = route.fromAllHosts,
                            fromHostMods = true
                        )
                    )
                },
                onOpenTaskList = { runId ->
                    navController.navigate(
                        TaskList(
                            selectedRunId = runId,
                            fromHostModsId = route.hostId,
                            fromAllHosts = route.fromAllHosts
                        )
                    )
                }
            )
        }
        composable<HostFiles> {
            val route = it.toRoute<HostFiles>()
            HostFilesScreen(
                hostId = ObjectId(route.hostId),
                onBack = {
                    val tab = if (route.fromAllHosts) HostTab.AllHosts else HostTab.MyHosts
                    navController.navigateAbsolute(HostRoute(tab.name))
                },
                onOpenTaskList = { runId ->
                    navController.navigate(
                        TaskList(
                            selectedRunId = runId,
                            fromHostTab = if (route.fromAllHosts) HostTab.AllHosts.name else HostTab.MyHosts.name
                        )
                    )
                }
            )
        }
        composable<HostBackend> {
            val route = it.toRoute<HostBackend>()
            HostBackendScreen(
                hostId = ObjectId(route.hostId),
                onBack = { returnToHostList(route.fromAllHosts) }
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
                onBack = {
                    route.fromHostModsId?.let { hostId ->
                        navController.navigateAbsolute(HostMods(hostId, route.fromAllHosts))
                    } ?: route.fromHostTab?.let { tab ->
                        navController.navigateAbsolute(HostRoute(tab))
                    } ?: navController.navigateAbsolute(Menu)
                },
                initialSelectedRunId = route.selectedRunId
            )
        }
        composable<HostCreate> {
            val route = it.toRoute<HostCreate>()
            HostNewCreateScreen(
                route,
                onBack = {
                    if (route.hostId != null) {
                        val tab = if (route.fromAllHosts) HostTab.AllHosts else HostTab.MyHosts
                        navController.navigateAbsolute(HostRoute(tab.name))
                    } else {
                        navController.navigateAbsolute(HostRoute(HostTab.MyHosts.name))
                    }
                },
                onNavigateProfile = { navController.navigateAbsolute(HostRoute(HostTab.MyHosts.name)) }
            )
        }
        composable<McPlayView> {
            val launchArgs = androidx.compose.runtime.remember {
                if (McPlayStore.openConsoleOnly) {
                    McPlayStore.openConsoleOnly = false
                    null
                } else {
                    McPlayStore.pendingLaunch.also { McPlayStore.pendingLaunch = null }
                }
            }
            McPlayScreen(
                launchArgs = launchArgs,
                autoStart = launchArgs != null,
                onBack = {
                    val callback = McPlayStore.onBack
                    McPlayStore.onBack = null
                    if (callback != null) {
                        callback()
                    } else {
                        navController.navigate(HostRoute(HostTab.MyHosts.name))
                    }
                }
            )
        }
        composable<ModpackUpload> {
            ModpackUploadScreen(
                modCatalog = modCatalog,
                onBack = {
                    navController.navigate(ResourceRoute(ResourceTab.All.name)) {
                        popUpTo<ModpackUpload> { inclusive = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onUploadSubmitted = { runId ->
                    navController.navigate(TaskList(runId)) {
                        popUpTo<ModpackUpload> { inclusive = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
        composable<Setting> {
            SettingScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateRoot(if (AccountSessionStore.isLoggedIn) Menu else Login)
                    }
                },
            )
        }
        composable<ModpackOptions> {
            val route = it.toRoute<ModpackOptions>()
            ModpackOptionScreen(
                versionId = route.versionId,
                modpackName = route.modpackName,
                versionName = route.versionName,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(ResourceRoute(ResourceTab.Installed.name))
                    }
                }
            )
        }
        composable<Host2Lobby> {
            Host2LobbyScreen(
                onBack = { navController.navigateAbsolute(Menu) },
                onCreate = { navController.navigate(Host2Create) },
                onOpen = { navController.navigate(Host2Info(it)) }
            )
        }
        composable<Host2Create> {
            Host2CreateScreen(
                onBack = { navController.navigateAbsolute(Host2Lobby) },
                onCreated = { navController.navigateAbsolute(Host2Info(it)) }
            )
        }
        composable<Host2Info> {
            val route = it.toRoute<Host2Info>()
            Host2InfoScreen(
                hostId = route.hostId,
                onBack = { navController.navigateAbsolute(Host2Lobby) },
                onOpenMods = { mcVersion ->
                    navController.navigate(
                        ResourceRoute(
                            ResourceTab.Mods.name,
                            mcVersion.mcVer,
                            fromHost2Id = route.hostId
                        )
                    )
                },
                onOpenTask = { runId -> navController.navigate(TaskList(runId)) },
                onOpenPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(Host2Info(route.hostId)) }
                }
            )
        }
        composable<PlayerInfo> {
            PlayerInfoScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateRoot(if (AccountSessionStore.isLoggedIn) Menu else Login)
                    }
                }
            )
        }
        composable<ResourceRoute> {
            val route = it.toRoute<ResourceRoute>()
            ResourceScreen(
                modCatalog = modCatalog,
                initialCategory = ResourceTab.fromRouteValue(route.tab),
                requiredMcVer = route.requiredMcVer?.let(McVersion::from),
                requiredLoader = route.requiredLoader?.let(ModLoader::from),
                onBack = {
                    val fromHostId = route.fromHostId
                    if (route.fromHost2Id != null) {
                        navController.navigateAbsolute(Host2Info(route.fromHost2Id))
                    } else if (fromHostId != null) {
                        if (!navController.popBackStack()) {
                            if (route.fromHostMods) {
                                navController.navigateAbsolute(HostMods(fromHostId, route.fromAllHosts))
                            } else {
                                navController.navigateAbsolute(HostInfo(fromHostId, route.fromAllHosts))
                            }
                        }
                    } else {
                        navController.navigateAbsolute(Menu)
                    }
                },
                onOpenUpload = { navController.navigate(ModpackUpload) },
                onOpenModpackInfo = { modpackId ->
                    navController.navigate(ModpackInfo(modpackId))
                },
                onOpenRemoteMod = { mod, pack ->
                    val ref = mod.primaryRef
                    navController.navigate(
                        RemoteModInfoRoute(
                            platform = ref.platform.name,
                            projectId = ref.projectId,
                            requiredMcVer = pack?.vo?.mcVer?.mcVer ?: route.requiredMcVer,
                            requiredLoader = pack?.vo?.modloader?.name ?: route.requiredLoader,
                            targetLocalVersionId = pack?.versionId,
                            targetHostId = route.fromHostId,
                            targetHost2Id = route.fromHost2Id,
                            fromAllHosts = route.fromAllHosts,
                            fromHostMods = route.fromHostMods
                        )
                    )
                },
                onOpenResourceInfo = { resource, type, pack ->
                    navController.navigate(
                        ResourceInfoRoute(
                            type = type.name,
                            projectId = resource.projectId,
                            targetLocalVersionId = pack?.versionId
                        )
                    )
                },
                onOpenPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(route) }
                },
                onOpenModpackOptions = { pack ->
                    navController.navigate(
                        ModpackOptions(
                            versionId = pack.versionId,
                            modpackName = pack.vo.name,
                            versionName = pack.verName,
                        )
                    )
                },
                onOpenTaskList = { runId ->
                    navController.navigate(
                        TaskList(
                            selectedRunId = runId,
                            fromHostModsId = route.fromHostId.takeIf { route.fromHostMods },
                            fromAllHosts = route.fromAllHosts
                        )
                    )
                }
            )
        }
        composable<ResourceInfoRoute> {
            val route = it.toRoute<ResourceInfoRoute>()
            ResourceInfoScreen(
                route = route,
                modCatalog = modCatalog,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(ResourceRoute())
                    }
                },
                onOpenTaskList = { runId -> navController.navigate(TaskList(runId)) },
                onTargetUnavailable = {
                    if (route.targetLocalVersionId != null) {
                        navController.navigateAbsolute(ResourceRoute(ResourceTab.Installed.name))
                    } else if (!navController.popBackStack()) {
                        navController.navigateAbsolute(ResourceRoute())
                    }
                }
            )
        }
        composable<RemoteModInfoRoute> {
            val route = it.toRoute<RemoteModInfoRoute>()
            RemoteModInfoScreen(
                route = route,
                catalog = modCatalog,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(ResourceRoute())
                    }
                },
                onOpenDependencyMod = { dependency ->
                    val ref = dependency.primaryRef
                    navController.navigate(
                        route.copy(
                            platform = ref.platform.name,
                            projectId = ref.projectId
                        )
                    )
                },
                onOpenTaskList = { runId ->
                    navController.navigate(
                        TaskList(
                            selectedRunId = runId,
                            fromHostModsId = route.targetHostId.takeIf { route.fromHostMods },
                            fromAllHosts = route.fromAllHosts
                        )
                    )
                },
                onTargetUnavailable = {
                    if (route.targetLocalVersionId != null) {
                        navController.navigateAbsolute(ResourceRoute(ResourceTab.Installed.name))
                    } else if (!navController.popBackStack()) {
                        navController.navigateAbsolute(ResourceRoute())
                    }
                }
            )
        }
        composable<ModpackInfo> {
            val route = it.toRoute<ModpackInfo>()
            ModpackInfoScreen(
                modCatalog = modCatalog,
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
