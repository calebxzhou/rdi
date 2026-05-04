package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.GameRuleModal
import calebxzhou.rdi.client.ui.comp.ImageCard
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.client.ui.comp.WorldCard
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.World
import calebxzhou.rdi.common.serdesJson
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId
import kotlin.random.Random

/**
 * calebxzhou @ 2026-02-28 22:46
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostNewCreateScreen(
    arg: HostCreate,
    onBack: () -> Unit,
    onNavigateProfile: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val overrideRules = remember { mutableStateMapOf<String, String>() }
    var selectedTab by remember { mutableStateOf(0) }

    var title by remember { mutableStateOf("创建新房间") }
    var hostName by remember { mutableStateOf("${loggedAccount.name}的世界${Random.nextInt(1000)}") }
    var modpackIdText by remember { mutableStateOf("") }
    var packVerText by remember { mutableStateOf("") }

    var localDirs by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var loadingLocalPacks by remember { mutableStateOf(true) }
    var localPackError by remember { mutableStateOf<String?>(null) }

    var worlds by remember { mutableStateOf<List<World.Vo>>(emptyList()) }
    var loadingWorlds by remember { mutableStateOf(true) }
    var loadingHost by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var noSave by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    var editHostId by remember { mutableStateOf<ObjectId?>(null) }
    var editWorldId by remember { mutableStateOf<ObjectId?>(null) }
    var editPreferNoSave by remember { mutableStateOf(false) }

    var selectedWorldId by remember { mutableStateOf<ObjectId?>(null) }
    var difficulty by remember { mutableStateOf(3) }
    var gameMode by remember { mutableStateOf(0) }
    var currentMcVersion by remember { mutableStateOf<McVersion?>(null) }
    var levelType by remember { mutableStateOf("minecraft:normal") }
    var levelChoice by remember { mutableStateOf(0) }
    var whitelist by remember { mutableStateOf(true) }
    var allowCheats by remember { mutableStateOf(false) }

    fun isEditMode() = editHostId != null
    fun skyblockLevelType(mcVersion: McVersion?): String {
        val minor = mcVersion?.vMinor?.toIntOrNull()
        return if (minor != null && minor <= 16) {
            "skyblockbuilder:custom_skyblock"
        } else {
            "skyblockbuilder:skyblock"
        }
    }
    fun updateLevelChoiceFromType(type: String, mcVersion: McVersion? = currentMcVersion) {
        when {
            type.contains("skyblock", ignoreCase = true) -> {
                levelChoice = 2
                levelType = mcVersion?.let(::skyblockLevelType) ?: type
            }

            type == "minecraft:flat" -> {
                levelChoice = 1
                levelType = "minecraft:flat"
            }

            else -> {
                levelChoice = 0
                levelType = "minecraft:normal"
            }
        }
    }

    fun reloadLocalPacks() {
        loadingLocalPacks = true
        localPackError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ModpackService.getLocalPackDirs() }
            }
            result.onSuccess { dirs ->
                localDirs = dirs
            }.onFailure {
                localPackError = "读取本地整合包失败: ${it.message}"
                localDirs = emptyList()
            }
            loadingLocalPacks = false
        }
    }

    LaunchedEffect(Unit) {
        reloadLocalPacks()
    }

    LaunchedEffect(arg.hostId) {
        val rawHostId = arg.hostId?.trim()
        if (rawHostId.isNullOrBlank() || !ObjectId.isValid(rawHostId)) {
            title = "创建新房间"
            editHostId = null
            currentMcVersion = selectedPack?.vo?.mcVer
            return@LaunchedEffect
        }
        editHostId = ObjectId(rawHostId)
        selectedTab = 1
        loadingHost = true
        scope.rdiRequest<Host.DetailVo>(
            path = "host/$rawHostId/detail",
            onOk = { resp ->
                val detail = resp.data ?: return@rdiRequest
                title = "编辑房间 · ${detail.name}"
                hostName = detail.name
                modpackIdText = detail.modpack.id.toHexString()
                packVerText = detail.packVer
                difficulty = detail.difficulty
                gameMode = detail.gameMode
                currentMcVersion = detail.modpack.mcVer
                whitelist = detail.whitelist
                allowCheats = detail.allowCheats
                updateLevelChoiceFromType(detail.levelType, detail.modpack.mcVer)
                if (detail.worldId == null) {
                    editPreferNoSave = true
                } else {
                    editWorldId = detail.worldId
                }
                overrideRules.clear()
                overrideRules.putAll(detail.gameRules)
            },
            onErr = { errorMessage = "无法加载房间信息: ${it.message}" },
            onDone = { loadingHost = false }
        )
    }

    LaunchedEffect(Unit) {
        loadingWorlds = true
        scope.rdiRequest<List<World.Vo>>(
            "world",
            onErr = { errorMessage = "无法载入存档: ${it.message}" },
            onOk = { resp ->
                worlds = resp.data ?: emptyList()
            },
            onDone = { loadingWorlds = false }
        )
    }

    LaunchedEffect(localDirs, modpackIdText, packVerText) {
        val matched = localDirs.firstOrNull {
            it.vo.id.toHexString() == modpackIdText && it.verName == packVerText
        } ?: return@LaunchedEffect
        if (selectedPack?.versionId != matched.versionId) {
            selectedPack = matched
        }
        if (currentMcVersion != matched.vo.mcVer) {
            currentMcVersion = matched.vo.mcVer
            if (levelChoice == 2) {
                levelType = skyblockLevelType(matched.vo.mcVer)
            }
        }
    }

    LaunchedEffect(worlds, editWorldId, editPreferNoSave) {
        val targetWorldId = editWorldId
        if (targetWorldId != null) {
            selectedWorldId = worlds.firstOrNull { it.id == targetWorldId }?.id
            editWorldId = null
        }
        if (editPreferNoSave) {
            noSave = true
            selectedWorldId = null
            editPreferNoSave = false
        }
    }

    fun submit() {
        if (submitting) return
        statusMessage = null
        val trimmedName = hostName.trim()
        if (trimmedName.isEmpty()) {
            statusMessage = "请输入房间名称"
            selectedTab = 1
            return
        }
        val hostId = editHostId
        if (hostId != null) {
            submitting = true
            val optionsDto = Host.OptionsDto(
                name = trimmedName,
                difficulty = difficulty,
                gameMode = gameMode,
                levelType = levelType,
                allowCheats = allowCheats,
                whitelist = whitelist,
                gameRules = overrideRules.toMutableMap()
            )
            val body = serdesJson.encodeToString(optionsDto)
            scope.rdiRequestU(
                path = "host/${hostId.toHexString()}/options",
                method = HttpMethod.Put,
                body = body,
                onErr = { statusMessage = "保存失败: ${it.message}" },
                onOk = { showResult = "设置已保存" },
                onDone = { submitting = false }
            )
            return
        }

        val pack = selectedPack ?: localDirs.firstOrNull {
            it.vo.id.toHexString() == modpackIdText && it.verName == packVerText
        }
        if (pack == null) {
            statusMessage = "请先在“选择整合包”标签中选择本地已安装整合包"
            selectedTab = 0
            return
        }
        if (!ObjectId.isValid(pack.vo.id.toHexString())) {
            statusMessage = "所选整合包无效，请重新选择"
            selectedTab = 0
            return
        }

        submitting = true
        val selectedWorld = selectedWorldId?.let { id -> worlds.firstOrNull { it.id == id } }
        val saveWorld = !noSave
        val worldId = when {
            !noSave && selectedWorld != null -> selectedWorld.id
            else -> null
        }
        val createDto = Host.CreateDto(
            name = trimmedName,
            modpackId = pack.vo.id,
            packVer = pack.verName,
            saveWorld = saveWorld,
            worldId = worldId,
            difficulty = difficulty,
            gameMode = gameMode,
            levelType = levelType,
            allowCheats = allowCheats,
            whitelist = whitelist,
            gameRules = overrideRules.toMutableMap()
        )
        val body = serdesJson.encodeToString(createDto)
        scope.rdiRequestU(
            path = "host/v2",
            method = HttpMethod.Post,
            body = body,
            onErr = { statusMessage = "创建失败: ${it.message}" },
            onOk = { showResult = "已提交创建请求 请等半分钟 完成后信箱通知你" },
            onDone = { submitting = false }
        )
    }

    val loadingAny = loadingLocalPacks || loadingHost || loadingWorlds
    val selectedPackTitle = selectedPack?.let { "${it.vo.name} ${it.verName}" } ?: "未选择整合包"
    val tabs = listOf("1.选择整合包（$selectedPackTitle）", "2.房间设置")
    MainColumn {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TitleRow(title, onBack) {
                if (loadingAny) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                CircleIconButton("\uDB82\uDE50", bgColor = MaterialColor.GREEN_900.color) {
                    submit()
                }
            }
            Space8h()
            statusMessage?.let {
                Text(it, color = MaterialTheme.colors.error)
                Space8h()
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colors.error)
                Space8h()
            }

            TabRow(selectedTabIndex = selectedTab, backgroundColor = MaterialColor.GRAY_100.color) {
                tabs.forEachIndexed { index, tabTitle ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tabTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            Space8h()

            when (selectedTab) {
                0 -> {
                    if (isEditMode()) {
                        Space8h()
                        Text("整合包一经设定，就不能更换。换包请重新创建房间", color = MaterialColor.GRAY_700.color)
                    }
                    Space8h()
                    localPackError?.let {
                        Text(it, color = MaterialTheme.colors.error)
                        Space8h()
                    }
                    if (loadingLocalPacks) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    Text("使用已下载的整合包创建房间。")
                    if (!loadingLocalPacks && localDirs.isEmpty()) {
                        Text("请先到“整合包管理界面”下载想玩的整合包，方可创建房间。", color = MaterialColor.GRAY_700.color)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 900.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localDirs, key = { it.versionId }) { packdir ->
                            ModpackManageCard(
                                packdir = packdir,
                                selected = packdir.versionId == selectedPack?.versionId ||
                                        (packdir.vo.id.toHexString() == modpackIdText && packdir.verName == packVerText),
                                        onClick = if (isEditMode()) {
                                            null
                                        } else {
                                            {
                                                selectedPack = packdir
                                                currentMcVersion = packdir.vo.mcVer
                                                modpackIdText = packdir.vo.id.toHexString()
                                                packVerText = packdir.verName
                                                if (levelChoice == 2) {
                                                    levelType = skyblockLevelType(packdir.vo.mcVer)
                                                }
                                            }
                                        }
                                    )
                        }
                    }
                }

                1 -> {
                    Text("超30天无人游玩房间会被自动删除（不删存档）届时需重新创建")
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val compactTopLayout = maxWidth < 1280.dp
                            val basicSettingSectionModifier = if (compactTopLayout) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.widthIn(min = 320.dp, max = 460.dp)
                            }
                            val gameplaySettingSectionModifier = if (compactTopLayout) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.widthIn(min = 520.dp, max = 980.dp)
                            }
                            FlowRow(
                                modifier = basicSettingSectionModifier,
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = if (compactTopLayout) 1 else 3
                            ) {
                                Column(
                                    modifier = basicSettingSectionModifier,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FlowRowV(
                                        modifier = basicSettingSectionModifier,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            label = { Text("房间名称") },
                                            value = hostName,
                                            onValueChange = { hostName = it },
                                            singleLine = true
                                        )
                                        RowV {

                                            Checkbox(whitelist, { whitelist = it })
                                            SimpleTooltip("仅限受邀玩家游玩") {
                                                Text("不允许陌生人游玩此房间")
                                            }
                                        }
                                        RowV {

                                            Checkbox(allowCheats, { allowCheats = it })
                                            Text("允许作弊")
                                        }
                                        Space8w()
                                        Button(onClick = { showRules = true }) {
                                            Text("修改游戏规则(${overrideRules.size})")
                                        }


                                    }
                                }

                                Column(
                                    modifier = gameplaySettingSectionModifier,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val compactGameplayLayout = maxWidth < 960.dp
                                        val optionSectionModifier = if (compactGameplayLayout) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier.widthIn(min = 220.dp, max = 340.dp)
                                        }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            maxItemsInEachRow = if (compactGameplayLayout) 1 else 3
                                        ) {
                                            Column(modifier = optionSectionModifier) {
                                                Text("难度", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(horizontal = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "和平",
                                                        iconPath = "assets/icons/difficulty_peaceful.png",
                                                        selected = difficulty == 0,
                                                        onClick = { difficulty = 0 }
                                                    )
                                                    ImageCard(
                                                        title = "简单",
                                                        iconPath = "assets/icons/difficulty_easy.png",
                                                        selected = difficulty == 1,
                                                        onClick = { difficulty = 1 }
                                                    )
                                                    ImageCard(
                                                        title = "普通",
                                                        iconPath = "assets/icons/difficulty_normal.png",
                                                        selected = difficulty == 2,
                                                        onClick = { difficulty = 2 }
                                                    )
                                                    ImageCard(
                                                        title = "困难",
                                                        iconPath = "assets/icons/difficulty_hard.png",
                                                        selected = difficulty == 3,
                                                        onClick = { difficulty = 3 }
                                                    )
                                                }
                                            }
                                            Column(modifier = optionSectionModifier) {
                                                Text("模式", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "生存",
                                                        iconPath = "assets/icons/gamemode_survival.png",
                                                        selected = gameMode == 0,
                                                        onClick = { gameMode = 0 }
                                                    )
                                                    ImageCard(
                                                        title = "创造",
                                                        iconPath = "assets/icons/gamemode_creative.png",
                                                        selected = gameMode == 1,
                                                        onClick = { gameMode = 1 }
                                                    )
                                                }
                                            }
                                            Column(modifier = optionSectionModifier) {
                                                Text("地形", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "普通",
                                                        iconPath = "assets/icons/worldtype_normal.png",
                                                        selected = levelChoice == 0,
                                                        onClick = {
                                                            levelChoice = 0
                                                            levelType = "minecraft:normal"
                                                        }
                                                    )
                                                    ImageCard(
                                                        title = "超平坦",
                                                        iconPath = "assets/icons/worldtype_flat.png",
                                                        selected = levelChoice == 1,
                                                        onClick = {
                                                            levelChoice = 1
                                                            levelType = "minecraft:flat"
                                                        }
                                                    )
                                                    ImageCard(
                                                        title = "空岛",
                                                        iconPath = "assets/icons/worldtype_skyblock.jpg",
                                                        selected = levelChoice == 2,
                                                        onClick = {
                                                            levelChoice = 2
                                                            levelType = skyblockLevelType(currentMcVersion)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Space8h()

                        if (!isEditMode()) {
                            Space8h()
                            Column(modifier = Modifier.fillMaxWidth()) {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val compactOptions = maxWidth < 920.dp
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        maxItemsInEachRow = if (compactOptions) 1 else 4
                                    ) {
                                        RowV {
                                            Text("选择要使用的存档", fontWeight = FontWeight.Bold)
                                            if (worlds.size < 5) {
                                                RadioButton(
                                                    selected = selectedWorldId == null && !noSave,
                                                    onClick = {
                                                        noSave = false
                                                        selectedWorldId = null
                                                    }
                                                )
                                                Text("创建新存档")
                                            }
                                            RadioButton(
                                                selected = selectedWorldId == null && noSave,
                                                onClick = {
                                                    selectedWorldId = null
                                                    noSave = true
                                                }
                                            )
                                            Text("不保存任何数据")
                                            if (noSave) {
                                                Space8w()
                                                Text("仅限测试整合包使用 谨慎选择", color = MaterialColor.RED_900.color)
                                            }
                                        }
                                    }
                                }
                                Space8h()
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 260.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 520.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(worlds) { world ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = if (world.id == selectedWorldId) 2.dp else 1.dp,
                                                    color = if (world.id == selectedWorldId) {
                                                        MaterialColor.PURPLE_500.color
                                                    } else {
                                                        MaterialColor.GRAY_200.color
                                                    },
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .padding(2.dp)
                                        ) {
                                            world.WorldCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = {
                                                    selectedWorldId = world.id
                                                    noSave = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Space8h()
        }
    }

    if (showRules) {
        GameRuleModal(
            show = true,
            overrideRules = overrideRules,
            onClose = { showRules = false }
        )
    }

    showResult?.let { message ->
        AlertDialog(
            onDismissRequest = { showResult = null },
            title = { Text(if (isEditMode()) "设置已保存" else "创建请求已提交") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showResult = null
                    if (isEditMode()) {
                        onBack()
                    } else {
                        onNavigateProfile()
                    }
                }) {
                    Text("确定")
                }
            }
        )
    }
}
