package whl.trending.chat.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberShowNotice(): (String) -> Unit {
    val context = LocalContext.current
    return { text -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
