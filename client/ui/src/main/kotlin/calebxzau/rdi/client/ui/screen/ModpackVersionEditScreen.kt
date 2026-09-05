package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzau.rdi.client.ui.ErrorText
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.Space8w
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.common.model.*


private data class EditableVersionModState(
    val originalProjectId: String,
    val originalFileId: String,
    val displayName: String,
    val platform: String,
    val projectId: String,
    val slug: String,
    val fileId: String,
    val hash: String,
    val downloadUrlsText: String,
    val side: Mod.Side,
)

private fun UiMod.toEditableVersionModState() = EditableVersionModState(
    originalProjectId = projectId,
    originalFileId = fileId,
    displayName = displayName,
    platform = platform,
    projectId = projectId,
    slug = slug,
    fileId = fileId,
    hash = hash,
    downloadUrlsText = mod.downloadUrls.joinToString("\n"),
    side = side
)

private fun EditableVersionModState.toBatchReplaceItem(): ModBatchReplaceItem {
    val normalizedPlatform = platform.trim().lowercase()
    val normalizedProjectId = projectId.trim()
    val normalizedSlug = slug.trim()
    val normalizedFileId = fileId.trim()
    val normalizedHash = hash.trim()
    require(normalizedPlatform.isNotBlank()) { "platform不能为空" }
    require(normalizedProjectId.isNotBlank()) { "projectId不能为空" }
    require(normalizedSlug.isNotBlank()) { "slug不能为空" }
    require(normalizedFileId.isNotBlank()) { "fileId不能为空" }
    require(normalizedHash.isNotBlank()) { "hash不能为空" }
    return ModBatchReplaceItem(
        projectId = originalProjectId,
        fileId = originalFileId,
        mod = Mod(
            platform = normalizedPlatform,
            projectId = normalizedProjectId,
            slug = normalizedSlug,
            fileId = normalizedFileId,
            hash = normalizedHash,
            side = side,
            downloadUrls = downloadUrlsText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()
        )
    )
}

@Composable
fun VersionModBatchEditDialog(
    uiMods: List<UiMod>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<ModBatchReplaceItem>) -> Unit
) {
    val dialogKey = remember(uiMods) { uiMods.map(UiMod::key).sorted().joinToString("|") }
    var editStates by remember(dialogKey) {
        mutableStateOf(uiMods.map { it.toEditableVersionModState() })
    }
    var localError by remember(dialogKey) { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.92f),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("批量编辑${editStates.size}个Mod", style = MaterialTheme.typography.titleLarge)
                localError?.let { ErrorText(it) }
                RScrollableColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    editStates.forEachIndexed { index, state ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(248, 248, 248))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("${index + 1}. ${state.displayName}")
                            RRow {


                                OutlinedTextField(
                                    value = state.platform,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(platform = value) }
                                    },
                                    label = { Text("platform") },
                                    singleLine = true,

                                    )
                                OutlinedTextField(
                                    value = state.projectId,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(projectId = value) }
                                    },
                                    label = { Text("projectId") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.slug,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(slug = value) }
                                    },
                                    label = { Text("slug") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.fileId,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(fileId = value) }
                                    },
                                    label = { Text("fileId") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.hash,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(hash = value) }
                                    },
                                    label = { Text("hash") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = state.downloadUrlsText,
                                    onValueChange = { value ->
                                        editStates = editStates.updateAt(index) { copy(downloadUrlsText = value) }
                                    },
                                    label = { Text("downloadUrls") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("side")
                                Mod.Side.entries.forEach { candidate ->

                                    RadioButton(
                                        selected = state.side == candidate,
                                        onClick = {
                                            editStates = editStates.updateAt(index) { copy(side = candidate) }
                                        }
                                    )
                                    Text(candidate.text)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !saving,
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    Space8w()
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            val replaceItems = runCatching {
                                editStates.map(EditableVersionModState::toBatchReplaceItem)
                            }.getOrElse {
                                localError = it.message ?: "Mod信息不合法"
                                return@TextButton
                            }
                            localError = null
                            onSave(replaceItems)
                        }
                    ) {
                        Text(if (saving) "保存中..." else "保存")
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.updateAt(index: Int, transform: T.() -> T): List<T> = mapIndexed { currentIndex, value ->
    if (currentIndex == index) value.transform() else value
}

