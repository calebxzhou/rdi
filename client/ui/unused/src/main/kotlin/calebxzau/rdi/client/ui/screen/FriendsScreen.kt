package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.service.SocialService
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RTextField
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzau.rdi.common.model.Friend
import calebxzau.rdi.common.model.FriendTag
import calebxzhou.rdi.common.model.RAccount
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.util.UUID

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenPlayer: (ObjectId) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var tags by remember { mutableStateOf<List<FriendTag>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<UUID?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editingFriend by remember { mutableStateOf<Friend?>(null) }
    var showTagManager by remember { mutableStateOf(false) }
    var removingFriend by remember { mutableStateOf<Friend?>(null) }

    fun reload() {
        loading = true
        errorMessage = null
        scope.launch {
            val friendsResult = SocialService.loadFriends()
            val tagsResult = SocialService.loadTags()
            friendsResult.fold(
                onSuccess = { friends = it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { friend -> friend.name }) },
                onFailure = { errorMessage = it.message ?: "加载好友失败" }
            )
            tagsResult.onSuccess { tags = it }.onFailure { if (errorMessage == null) errorMessage = it.message ?: "加载标签失败" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    val shownFriends = remember(friends, selectedTag) {
        friends.filter { selectedTag == null || it.tags.any { tag -> tag.id == selectedTag } }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("好友", onBack = onBack) {
                CircleIconButton(icon = "\uF1D8", label = "添加好友", showText = false) { showAdd = true }
                CircleIconButton(icon = "\uF013", label = "管理标签", showText = false) { showTagManager = true }
            }
            ContentBody {
                Column(Modifier.fillMaxSize()) {
                    FlowRowV(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("全部") })
                        tags.forEach { tag ->
                            FilterChip(selected = selectedTag == tag.id, onClick = { selectedTag = tag.id }, label = { Text(tag.name) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (shownFriends.isEmpty() && errorMessage == null) {
                        Text("还没有好友", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                            items(shownFriends, key = { it.id }) { friend ->
                                FriendRow(
                                    friend = friend,
                                    onOpen = { onOpenPlayer(friend.id) },
                                    onEditTags = { editingFriend = friend },
                                    onRemove = { removingFriend = friend }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFriendDialog(
            onDismiss = { showAdd = false },
            onError = { errorMessage = it },
            onSent = { showAdd = false; reload() }
        )
    }
    editingFriend?.let { friend ->
        FriendTagsDialog(
            friend = friend,
            tags = tags,
            onDismiss = { editingFriend = null },
            onSaved = { editingFriend = null; reload() },
            onError = { errorMessage = it }
        )
    }
    if (showTagManager) {
        TagManagerDialog(
            tags = tags,
            onDismiss = { showTagManager = false },
            onChanged = { reload() },
            onError = { errorMessage = it }
        )
    }
    removingFriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { removingFriend = null },
            title = { Text("删除好友") },
            text = { Text("确定删除${friend.name}吗？") },
            confirmButton = {
                TextButton(onClick = {
                    removingFriend = null
                    scope.launch {
                        SocialService.removeFriend(friend.id).fold(
                            onSuccess = { reload() },
                            onFailure = { errorMessage = it.message ?: "删除好友失败" }
                        )
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingFriend = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    onOpen: () -> Unit,
    onEditTags: () -> Unit,
    onRemove: () -> Unit,
) {
    androidx.compose.material3.Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            HeadButton(uid = friend.id, avatarSize = 36.dp, showName = false, onClick = onOpen)
            Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = onOpen)) {
                Text(friend.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (friend.tags.isNotEmpty()) {
                    Text(friend.tags.joinToString(" · ") { it.name }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CircleIconButton(icon = "\uF040", label = "编辑标签", showText = false, size = 32.dp, onClick = onEditTags)
            CircleIconButton(icon = "\uEA81", label = "删除好友", showText = false, size = 32.dp, bgColor = MaterialTheme.colorScheme.error, onClick = onRemove)
        }
    }
}

@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onError: (String) -> Unit, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var qq by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<RAccount.Dto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好友") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RTextField("QQ号", qq, onValueChange = { qq = it.filter(Char::isDigit); preview = null; localError = null })
                preview?.let { account ->
                    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HeadButton(uid = account.id, avatarSize = 38.dp, showName = false)
                            Text(account.name, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && !sending, onClick = {
                if (preview == null) {
                    loading = true
                    scope.launch {
                        SocialService.lookupFriend(qq).fold(
                            onSuccess = { preview = it; loading = false },
                            onFailure = { localError = it.message ?: "查找玩家失败"; loading = false }
                        )
                    }
                } else {
                    sending = true
                    scope.launch {
                        SocialService.requestFriend(qq).fold(
                            onSuccess = { onSent() },
                            onFailure = { localError = it.message ?: "发送好友申请失败"; onError(localError!!); sending = false }
                        )
                    }
                }
            }) { Text(if (preview == null) "查找" else "发送申请") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FriendTagsDialog(
    friend: Friend,
    tags: List<FriendTag>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember(friend.id) { mutableStateOf<Set<UUID>>(friend.tags.map { it.id }.toSet()) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑${friend.name}的标签") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (tag.id in selected) selected - tag.id else selected + tag.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tag.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + tag.id else selected - tag.id })
                        Text(tag.name)
                    }
                }
                if (tags.isEmpty()) Text("请先创建标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && selected.size <= 5, onClick = {
                saving = true
                scope.launch {
                    SocialService.replaceFriendTags(friend.id, selected).fold(
                        onSuccess = { onSaved() },
                        onFailure = { onError(it.message ?: "保存标签失败"); saving = false }
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TagManagerDialog(
    tags: List<FriendTag>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<UUID?>(null) }
    var editingName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理标签") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RTextField("新标签", newName, modifier = Modifier.weight(1f), onValueChange = { newName = it })
                    CircleIconButton(icon = "\uF067", label = "创建标签", showText = false, size = 32.dp) {
                        scope.launch { SocialService.createTag(newName).fold(onSuccess = { newName = ""; onChanged() }, onFailure = { onError(it.message ?: "创建标签失败") }) }
                    }
                }
                tags.forEach { tag ->
                    if (editingId == tag.id) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RTextField("标签名称", editingName, modifier = Modifier.weight(1f), onValueChange = { editingName = it })
                            TextButton(onClick = { scope.launch { SocialService.renameTag(tag.id, editingName).fold(onSuccess = { editingId = null; onChanged() }, onFailure = { onError(it.message ?: "重命名标签失败") }) } }) { Text("保存") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(tag.name, modifier = Modifier.weight(1f))
                            CircleIconButton(icon = "\uF040", label = "重命名", showText = false, size = 30.dp) { editingId = tag.id; editingName = tag.name }
                            CircleIconButton(icon = "\uEA81", label = "删除标签", showText = false, size = 30.dp, bgColor = MaterialTheme.colorScheme.error) {
                                scope.launch { SocialService.deleteTag(tag.id).fold(onSuccess = { onChanged() }, onFailure = { onError(it.message ?: "删除标签失败") }) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
