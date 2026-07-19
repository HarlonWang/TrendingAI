package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.action_favorite
import trendingai.shared.generated.resources.action_star
import trendingai.shared.generated.resources.action_unfavorite
import trendingai.shared.generated.resources.share_to_ai

@Composable
fun ItemActionMenu(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    /** 非空时在菜单中显示「Star 到 GitHub」项（仅 GitHub 仓库场景传入），null 则不显示 */
    onStar: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShare,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Shortcut,
                contentDescription = stringResource(Res.string.share_to_ai),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isFavorite) Res.string.action_unfavorite
                                else Res.string.action_favorite
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        expanded = false
                        onToggle()
                    }
                )
                if (onStar != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_star)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.StarBorder, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onStar()
                        }
                    )
                }
            }
        }
    }
}
