package calebxzhou.rdi.client.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import calebxzau.rdi.client.blessingskin.BlessingSkinClient
import calebxzau.rdi.client.modcatalog.CatalogMod
import calebxzau.rdi.client.ui.screen.PlayerInfoScreen
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.screen.ModpackVersionEditScreen
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.model.McVersion

private const val SCREEN_FADE_DURATION_MS = 500
private const val BLESSING_SKIN_BASE_URL = "https://littleskin.cn"

private fun RemoteModRoute.toInfoRoute(mod: CatalogMod): RemoteModInfoRoute =
    RemoteModInfoRoute(
        platform = mod.primaryRef.platform.name,
        projectId = mod.primaryRef.projectId,
        requiredMcVer = requiredMcVer,
        requiredLoader = requiredLoader,
        targetLocalVersionId = targetLocalVersionId,
        targetLocalKind = targetLocalKind,
        targetLocalId = targetLocalId,
        targetHostId = targetHostId,
        fromAllHosts = fromAllHosts,
        fromHostMods = fromHostMods,
    )

private fun RemoteModInfoRoute.toBrowseRouteOrNull(clearLocalTarget: Boolean = false): RemoteModRoute? {
    val mcVersion = requiredMcVer ?: return null
    val loader = requiredLoader ?: return null
    return RemoteModRoute(
        requiredMcVer = mcVersion,
        requiredLoader = loader,
        targetLocalVersionId = targetLocalVersionId.takeUnless { clearLocalTarget },
        targetLocalKind = targetLocalKind.takeUnless { clearLocalTarget },
        targetLocalId = targetLocalId.takeUnless { clearLocalTarget },
        targetHostId = targetHostId,
        fromAllHosts = fromAllHosts,
        fromHostMods = fromHostMods,
    )
}

private fun NavHostController.returnFromRemoteModInfo(route: RemoteModInfoRoute, clearLocalTarget: Boolean) {
    route.toBrowseRouteOrNull(clearLocalTarget)?.let(::navigateAbsolute) ?: when {
        !clearLocalTarget && route.localCatalogTarget() != null -> navigateAbsolute(MyModpackRoute)
        else -> navigateAbsolute(Menu)
    }
}

private fun NavHostController.returnFromRemoteMod(route: RemoteModRoute) {
    if (popBackStack()) return
    val hostId = route.targetHostId
    when {
        hostId != null -> {
            if (route.fromHostMods) {
                navigateAbsolute(HostMods(hostId, route.fromAllHosts, HostKind.Legacy.name))
            } else {
                navigateAbsolute(HostInfo(hostId, route.fromAllHosts, HostKind.Legacy.name))
            }
        }
        route.localCatalogTarget() != null -> navigateAbsolute(MyModpackRoute)
        else -> navigateAbsolute(Menu)
    }
}

private fun NavHostController.returnFromModpackPlaza(route: ModpackPlazaRoute) {
    if (popBackStack()) return
    when {
        route.fromHostId != null -> {
            if (route.fromHostMods) {
                navigateAbsolute(HostMods(route.fromHostId, route.fromAllHosts, HostKind.Legacy.name))
            } else {
                navigateAbsolute(HostInfo(route.fromHostId, route.fromAllHosts, HostKind.Legacy.name))
            }
        }
        else -> navigateAbsolute(MyModpackRoute)
    }
}

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
    onOpenTask: (String) -> Unit,
    startDestination: Any = Login,
) {
    val blessingSkin = remember { BlessingSkinClient(BLESSING_SKIN_BASE_URL) }
    val openMcPlay: (McPlayArgs, (() -> Unit)?) -> Unit = { args, onBack ->
        McPlayStore.pendingLaunch = args
        McPlayStore.onBack = onBack
        navController.navigate(McPlayView)
    }
    val returnToHostList: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigateAbsolute(HostRoute)
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
                onOpenModpacks = { navController.navigate(MyModpackRoute) },
                onOpenHostLobby = { navController.navigate(HostRoute) },
                onOpenMcPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(Menu) }
                },
                onOpenTaskList = { runId ->
                    onOpenTask(runId)
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
            HostListScreen(
                onBack = { navController.navigateAbsolute(Menu) },
                onOpenModpackPlaza = { navController.navigate(ModpackPlazaRoute()) },
                onOpenWorlds = { navController.navigate(WorldRoute) },
                onOpenHostInfo = { target, fromAllHosts ->
                    navController.navigate(HostInfo(target.id, fromAllHosts, target.kind.name))
                },
                onOpenHostMembers = { target, fromAllHosts ->
                    navController.navigate(HostMembers(target.id, fromAllHosts, target.kind.name))
                },
                onOpenHostMods = { target, fromAllHosts ->
                    navController.navigate(HostMods(target.id, fromAllHosts, target.kind.name))
                },
                onOpenHostFiles = { target, fromAllHosts ->
                    navController.navigate(HostFiles(target.id, fromAllHosts, target.kind.name))
                },
                onOpenHostBackend = { target, fromAllHosts ->
                    navController.navigate(HostBackend(target.id, fromAllHosts, target.kind.name))
                },
                onOpenHostSettings = { target, fromAllHosts ->
                    navController.navigate(HostCreate(target.id, fromAllHosts, target.kind.name))
                },
                onOpenMcPlay = { args, _ ->
                    openMcPlay(args) { navController.navigateAbsolute(HostRoute) }
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
                    onOpenTask(runId)
                }
            )
        }
        composable<WorldRoute> {
            WorldListScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(HostRoute)
                    }
                }
            )
        }
        composable<HostInfo> {
            val route = it.toRoute<HostInfo>()
            val kind = HostKind.fromRouteValue(route.kind)
            HostInfoScreen(
                target = HostTarget(kind, route.hostId),
                onBack = {
                    navController.navigateAbsolute(HostRoute)
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
                onOpenPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(HostRoute) }
                },
                onOpenTaskList = onOpenTask,
            )
        }
        composable<HostMembers> {
            val route = it.toRoute<HostMembers>()
            val kind = HostKind.fromRouteValue(route.kind)
            HostMembersScreen(
                target = HostTarget(kind, route.hostId),
                onBack = returnToHostList,
                onQuit = returnToHostList
            )
        }
        composable<HostMods> {
            val route = it.toRoute<HostMods>()
            val kind = HostKind.fromRouteValue(route.kind)
            HostModsScreen(
                modCatalog = modCatalog,
                target = HostTarget(kind, route.hostId),
                onBack = returnToHostList,
                onOpenResourceMods = { mcVersion, modLoader ->
                    navController.navigate(
                        RemoteModRoute(
                            requiredMcVer = mcVersion.mcVer,
                            requiredLoader = modLoader.name,
                            targetHostId = route.hostId.takeIf { kind == HostKind.Legacy },
                            fromAllHosts = route.fromAllHosts,
                            fromHostMods = true
                        )
                    )
                },
                onOpenTaskList = { runId ->
                    onOpenTask(runId)
                }
            )
        }
        composable<HostFiles> {
            val route = it.toRoute<HostFiles>()
            val kind = HostKind.fromRouteValue(route.kind)
            HostFilesScreen(
                target = HostTarget(kind, route.hostId),
                onBack = {
                    navController.navigateAbsolute(HostRoute)
                },
                onOpenTaskList = { runId ->
                    onOpenTask(runId)
                }
            )
        }
        composable<HostBackend> {
            val route = it.toRoute<HostBackend>()
            val kind = HostKind.fromRouteValue(route.kind)
            HostBackendScreen(
                target = HostTarget(kind, route.hostId),
                onBack = returnToHostList
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
                onBack = { navController.navigateAbsolute(WorldRoute) }
            )
        }*/
        composable<HostCreate> {
            val route = it.toRoute<HostCreate>()
            val kind = HostKind.fromRouteValue(route.kind)
            val onBack = {
                if (route.hostId == null) {
                    navController.navigateAbsolute(MyModpackRoute)
                } else {
                    navController.navigateAbsolute(HostRoute)
                }
            }
            if (kind == HostKind.Host2) {
                HostDetailRouteError("该房间类型暂不可用", onBack)
            } else if (route.hostId == null) {
                HostNewCreateScreen(
                    route,
                    onBack = onBack,
                )
            } else {
                HostNewCreateScreen(route, onBack = onBack)
            }
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
                        navController.navigate(HostRoute)
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
                        navController.navigateAbsolute(MyModpackRoute)
                    }
                }
            )
        }
        composable<PlayerInfo> { entry ->
            val route = entry.toRoute<PlayerInfo>()
            PlayerInfoScreen(
                playerId = route.playerId,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateRoot(if (AccountSessionStore.isLoggedIn) Menu else Login)
                    }
                }
            )
        }
        composable<MyModpackRoute> {
            MyModpackScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(Menu)
                    }
                },
                onOpenPlaza = { navController.navigate(ModpackPlazaRoute()) },
                onOpenContent = { pack, contentType ->
                    when (contentType) {
                        ModpackContentType.Mods -> navController.navigate(
                            RemoteModRoute(
                                requiredMcVer = pack.mcVersion.mcVer,
                                requiredLoader = pack.modLoader.name,
                                targetLocalVersionId = pack.versionId,
                            )
                        )

                        ModpackContentType.ResourcePacks,
                        ModpackContentType.Shaders -> navController.navigate(
                            ModpackContentRoute(
                                type = contentType.name,
                                requiredMcVer = pack.mcVersion.mcVer,
                                requiredLoader = pack.modLoader.name,
                                targetLocalVersionId = pack.versionId,
                            )
                        )
                    }
                },
                onOpenOptions = { pack ->
                    navController.navigate(
                        ModpackOptions(
                            versionId = pack.versionId,
                            modpackName = pack.name,
                            versionName = pack.verName,
                        )
                    )
                },
                onOpenPlay = { args ->
                    openMcPlay(args) { navController.navigateAbsolute(MyModpackRoute) }
                },
                onOpenTask = onOpenTask,
            )
        }
        /* Friends navigation is archived with the friend system. */
        composable<ModpackPlazaRoute> {
            val route = it.toRoute<ModpackPlazaRoute>()
            RemoteModpackScreen(
                onOpenInfo = { modpackId ->
                    navController.navigate(
                        ModpackInfo(
                            modpackId = modpackId,
                        )
                    )
                },
                onBack = { navController.returnFromModpackPlaza(route) },
                onOpenUpload = { navController.navigate(ModpackUpload) },
                requiredMcVer = route.requiredMcVer?.let(McVersion::from),
                requiredLoader = route.requiredLoader,
            )
        }
        composable<ModpackContentRoute> {
            val route = it.toRoute<ModpackContentRoute>()
            val contentType = ModpackContentType.valueOf(route.type)
            val onBack = {
                if (!navController.popBackStack()) {
                    navController.navigateAbsolute(MyModpackRoute)
                }
            }
            when (contentType) {
                ModpackContentType.Mods -> RemoteModScreen(
                    route = RemoteModRoute(
                        requiredMcVer = route.requiredMcVer,
                        requiredLoader = route.requiredLoader,
                        targetLocalVersionId = route.targetLocalVersionId,
                        targetLocalKind = route.targetLocalKind,
                        targetLocalId = route.targetLocalId,
                    ),
                    onBack = onBack,
                    onOpenMod = { mod ->
                        navController.navigate(
                            RemoteModInfoRoute(
                                platform = mod.primaryRef.platform.name,
                                projectId = mod.primaryRef.projectId,
                                requiredMcVer = route.requiredMcVer,
                                requiredLoader = route.requiredLoader,
                                targetLocalVersionId = route.targetLocalVersionId,
                                targetLocalKind = route.targetLocalKind,
                                targetLocalId = route.targetLocalId,
                            )
                        )
                    },
                )

                ModpackContentType.ResourcePacks -> ResourcepackListScreen(
                    requiredMcVer = route.requiredMcVer?.let(McVersion::from),
                    onBack = onBack,
                    onOpenResourcepack = { project ->
                        navController.navigate(
                            ResourceInfoRoute(
                                type = ResourceInfoType.ResourcePack.name,
                                projectId = project.projectId,
                                targetLocalVersionId = route.targetLocalVersionId,
                                targetLocalKind = route.targetLocalKind,
                                targetLocalId = route.targetLocalId,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                ModpackContentType.Shaders -> ShaderListScreen(
                    requiredMcVer = route.requiredMcVer?.let(McVersion::from),
                    onBack = onBack,
                    onOpenShader = { project ->
                        navController.navigate(
                            ResourceInfoRoute(
                                type = ResourceInfoType.Shader.name,
                                projectId = project.projectId,
                                targetLocalVersionId = route.targetLocalVersionId,
                                targetLocalKind = route.targetLocalKind,
                                targetLocalId = route.targetLocalId,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composable<ResourceInfoRoute> {
            val route = it.toRoute<ResourceInfoRoute>()
            ResourceInfoScreen(
                route = route,
                modCatalog = modCatalog,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateAbsolute(MyModpackRoute)
                    }
                },
                onOpenTaskList = onOpenTask,
                onTargetUnavailable = {
                    if (route.localCatalogTarget() != null) {
                        navController.navigateAbsolute(MyModpackRoute)
                    } else if (!navController.popBackStack()) {
                        navController.navigateAbsolute(MyModpackRoute)
                    }
                }
            )
        }
        composable<ModpackUpload> {
            ModpackUploadScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigateAbsolute(ModpackPlazaRoute())
                },
                onUploadSubmitted = onOpenTask,
            )
        }
        /* composable<Modpack2Upload> {
            Modpack2UploadScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigateAbsolute(ModpackPlazaRoute())
                },
                onOpenTask = onOpenTask,
            )
        }
        composable<Modpack2ContentRoute> {
            val route = it.toRoute<Modpack2ContentRoute>()
            Modpack2ContentScreen(
                versionId = route.versionId,
                onBack = {
                    if (!navController.popBackStack()) navController.navigateAbsolute(MyModpackRoute)
                },
                onBrowse = { type, versionId, mcVersion, modLoader ->
                    when (type) {
                        ModpackContentType.Mods -> navController.navigate(RemoteModRoute(
                            requiredMcVer = mcVersion.mcVer,
                            requiredLoader = modLoader.name,
                            targetLocalKind = CatalogLocalTargetKind.Modpack2.name,
                            targetLocalId = versionId,
                        ))
                        else -> navController.navigate(ModpackContentRoute(
                            type = type.name,
                            requiredMcVer = mcVersion.mcVer,
                            requiredLoader = modLoader.name,
                            targetLocalKind = CatalogLocalTargetKind.Modpack2.name,
                            targetLocalId = versionId,
                        ))
                    }
                },
                onOpenKnown = { content, mcVersion, modLoader ->
                    navController.navigate(RemoteModInfoRoute(
                        platform = when (content.platform.name) { "CurseForge" -> "CURSEFORGE"; else -> "MODRINTH" },
                        projectId = content.projectId,
                        requiredMcVer = mcVersion.mcVer,
                        requiredLoader = modLoader.name,
                        targetLocalKind = CatalogLocalTargetKind.Modpack2.name,
                        targetLocalId = route.versionId,
                    ))
                },
            )
        } */
        composable<RemoteModRoute> {
            val route = it.toRoute<RemoteModRoute>()
            if (isDisabledCatalogLocalTargetKind(route.targetLocalKind)) {
                LaunchedEffect(route) {
                    navController.navigateAbsolute(MyModpackRoute)
                }
            } else {
                RemoteModScreen(
                    route = route,
                    onBack = { navController.returnFromRemoteMod(route) },
                    onOpenMod = { mod ->
                        navController.navigate(route.toInfoRoute(mod))
                    },
                )
            }
        }
        composable<RemoteModInfoRoute> {
            val route = it.toRoute<RemoteModInfoRoute>()
            RemoteModInfoScreen(
                route = route,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.returnFromRemoteModInfo(route, clearLocalTarget = false)
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
                onOpenTaskList = onOpenTask,
                onTargetUnavailable = {
                    if (route.hasDisabledCatalogLocalTargetKind()) {
                        navController.navigateAbsolute(MyModpackRoute)
                    } else if (route.localCatalogTarget() != null) {
                        navController.returnFromRemoteModInfo(route, clearLocalTarget = true)
                    } else if (!navController.popBackStack()) {
                        navController.returnFromRemoteModInfo(route, clearLocalTarget = false)
                    }
                }
            )
        }
        composable<ModpackInfo> {
            val route = it.toRoute<ModpackInfo>()
            val onBackFromModpackInfo = {
                if (route.fromHostId != null) {
                    navController.navigateAbsolute(HostInfo(route.fromHostId, route.fromAllHosts))
                } else if (!navController.popBackStack()) {
                    navController.navigateAbsolute(ModpackPlazaRoute())
                }
            }
            if (ModpackInfoSource.fromRouteValue(route.source) != ModpackInfoSource.Legacy) {
                LaunchedEffect(route) {
                    onBackFromModpackInfo()
                }
            } else {
                ModpackInfoScreen(
                    modpackId = route.modpackId,
                    onBack = onBackFromModpackInfo,
                    onOpenTaskList = onOpenTask,
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
                    onCreateHost = { pack, version ->
                        navController.navigate(
                            HostCreate(
                                kind = HostKind.Legacy.name,
                                sourceId = pack._id.toHexString(),
                                legacyVersionName = version.name,
                                displayName = pack.name,
                                legacyMcVersion = pack.mcVer.name,
                            )
                        )
                    },
                )
            }
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
