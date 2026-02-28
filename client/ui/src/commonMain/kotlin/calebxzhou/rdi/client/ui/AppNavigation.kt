package calebxzhou.rdi.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import calebxzhou.mykotutils.std.encodeBase64
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.UIFontFamily
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.model.McVersion
import org.bson.types.ObjectId

/**
 * Common Typography using the cross-platform UIFontFamily.
 */
val AppTypography: Typography
    @Composable get() = Typography(defaultFontFamily = UIFontFamily)

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
        val openTaskView: (calebxzhou.rdi.common.model.Task, Boolean, (() -> Unit)?) -> Unit = { task, autoClose, onDone ->
            TaskStore.current = task
            TaskStore.autoClose = autoClose
            TaskStore.onDone = onDone
            navController.navigate(TaskView)
        }

        // Android FCL launch dialog
        val showFclLaunchDialog = remember { mutableStateOf(false) }
        val fclLaunchArgs = remember { mutableStateOf<McPlayArgs?>(null) }
        val handleOpenMcPlay: (McPlayArgs) -> Unit = { args ->
            if (isDesktop) {
                McPlayStore.current = args
                navController.navigate(McPlayView)
            } else {
                fclLaunchArgs.value = args
                showFclLaunchDialog.value = true
            }
        }
        if (showFclLaunchDialog.value) {
            val args = fclLaunchArgs.value
            if (args != null) {
                val jvmArg = "-Drdi.play=${args.playArg.encodeBase64}"
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

        NavHost(navController = navController, startDestination = startDestination) {
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
                    onOpenMcVersions = { navController.navigate(RMcVersion(null)) },
                    onOpenSettings = { navController.navigate(Setting) },
                    onOpenMail = { navController.navigate(Mail) },
                    onOpenHostLobby = { navController.navigate(HostList) },
                    onOpenWardrobe = { navController.navigate(Wardrobe) },
                    onOpenModpackList = { navController.navigate(ModpackList) },
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
                    onBack = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.popBackStack()
                    }
                )
            }
            composable<Wardrobe> {
                WardrobeScreen(
                    onBack = { navController.popBackStack() },
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
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Mail> {
                MailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { mailId -> navController.navigate(MailDetail(mailId)) }
                )
            }
            composable<MailDetail> {
                val route = it.toRoute<MailDetail>()
                MailDetailScreen(
                    mailId = route.mailId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<HostList> {
                HostListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHostInfo = { hostId ->
                        navController.navigate(HostInfo(hostId))
                    },
                    onOpenHostCreate = {
                        navController.navigate(
                            HostCreate(
                                modpackId = "",
                                modpackName = "",
                                packVer = "",
                                skyblock = false
                            )
                        )
                    },
                    onOpenMcPlay = handleOpenMcPlay,
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(RMcVersion(mcVer?.mcVer))
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null)
                    }
                )
            }
            composable<HostInfo> {
                val route = it.toRoute<HostInfo>()
                HostInfoScreen(
                    hostId = ObjectId(route.hostId),
                    onBack = { navController.popBackStack() },
                    onOpenModpackInfo = { modpackId ->
                        navController.navigate(ModpackInfo(modpackId, fromHostId = route.hostId))
                    },
                    onOpenMcPlay = handleOpenMcPlay,
                    onOpenMcVersions = { mcVer ->
                        navController.navigate(RMcVersion(mcVer?.mcVer))
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null)
                    },
                    onOpenHostEdit = { host ->
                        navController.navigate(
                            HostCreate(
                                modpackId = host.modpack.id.toHexString(),
                                modpackName = host.modpack.name,
                                packVer = host.packVer,
                                skyblock = host.levelType.contains("skyblock", true),
                                hostId = host._id.toHexString()
                            )
                        )
                    }
                )
            }
            composable<WorldList> {
                WorldListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBirdView = { worldId ->
                        navController.navigate(WorldBirdView(worldId))
                    }
                )
            }
            composable<WorldBirdView> {
                val route = it.toRoute<WorldBirdView>()
                WorldBirdViewScreen(
                    worldId = route.worldId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<HostCreate> {
                HostNewCreateScreen(
                    it.toRoute(),
                    onBack = { navController.popBackStack() },
                    onNavigateProfile = { navController.navigate(HostList) }
                )
            }
            composable<TaskView> {
                val task = TaskStore.current
                if (task != null) {
                    TaskScreen(
                        task = task,
                        autoClose = TaskStore.autoClose,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            TaskStore.onDone?.invoke()
                            TaskStore.onDone = null
                            TaskStore.autoClose = false
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
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ModpackList> {
                ModpackListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUpload = { navController.navigate(ModpackUpload) },
                    onOpenTask = { task, autoClose, onDone ->
                        openTaskView(task, autoClose, onDone)
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
                            navController.navigate(HostInfo(route.fromHostId))
                        } else {
                            navController.navigate(ModpackList)
                        }
                    },
                    onOpenUpload = { modpackId, modpackName ->
                        navController.navigate(ModpackUpload)
                    },
                    onCreateHost = { modpackId, modpackName, packVer, skyblock ->
                        navController.navigate(
                            HostCreate(
                                modpackId = modpackId,
                                modpackName = modpackName,
                                packVer = packVer,
                                skyblock = skyblock
                            )
                        )
                    },
                    onOpenTask = { task ->
                        openTaskView(task, false, null)
                    }
                )
            }

            composable<RMcVersion> {
                val route = it.toRoute<RMcVersion>()
                val required = route.mcVer?.let { ver -> McVersion.from(ver) }
                McVersionScreen(
                    requiredMcVer = required,
                    onBack = { navController.popBackStack() },
                    onOpenTask = { task ->
                        openTaskView(task, false, null)
                    },
                    onOpenPlay = handleOpenMcPlay
                )
            }
        }
    }
}
