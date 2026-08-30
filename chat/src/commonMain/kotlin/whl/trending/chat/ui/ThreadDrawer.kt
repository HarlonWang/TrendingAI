package whl.trending.chat.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_dialog_cancel
import trendingai.chat.generated.resources.chat_dialog_confirm
import trendingai.chat.generated.resources.chat_history
import trendingai.chat.generated.resources.chat_new_thread
import trendingai.chat.generated.resources.chat_thread_delete
import trendingai.chat.generated.resources.chat_thread_delete_text
import trendingai.chat.generated.resources.chat_thread_delete_title
import trendingai.chat.generated.resources.chat_thread_more
import trendingai.chat.generated.resources.chat_thread_rename
import whl.trending.chat.ThreadSummary

/**
 * 会话抽屉：新会话 + 历史列表（点击切换；条目「更多」菜单提供重命名/删除）。
 * P1 不做搜索与分组（Recent 单列表足够，量大再议）。
 */
@Composable
fun ThreadDrawer(
    threads: List<ThreadSummary>,
    currentThreadId: Long?,
    onNewThread: () -> Unit,
    onSwitch: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<ThreadSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ThreadSummary?>(null) }

    ModalDrawerSheet {
        Text(
            text = stringResource(Res.string.chat_history),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.chat_new_thread)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onNewThread,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(threads, key = { it.id }) { thread ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    NavigationDrawerItem(
                        label = {
                            Text(thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        selected = thread.id == currentThreadId,
                        onClick = { onSwitch(thread.id) },
                        badge = { ThreadItemMenu(thread, { renameTarget = it }, { deleteTarget = it }) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onConfirm = { newTitle ->
                onRename(target.id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.chat_thread_delete_title)) },
            text = { Text(stringResource(Res.string.chat_thread_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text(stringResource(Res.string.chat_thread_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.chat_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ThreadItemMenu(
    thread: ThreadSummary,
    onRenameRequest: (ThreadSummary) -> Unit,
    onDeleteRequest: (ThreadSummary) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.chat_thread_more),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.chat_thread_rename)) },
            onClick = {
                expanded = false
                onRenameRequest(thread)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.chat_thread_delete)) },
            onClick = {
                expanded = false
                onDeleteRequest(thread)
            },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_thread_rename)) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value) },
            ) { Text(stringResource(Res.string.chat_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.chat_dialog_cancel)) }
        },
    )
}
