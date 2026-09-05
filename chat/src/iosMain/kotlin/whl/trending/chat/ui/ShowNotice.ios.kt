package whl.trending.chat.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert

@Composable
internal actual fun rememberShowNotice(): (String) -> Unit = { text ->
    val alert = UIAlertController.alertControllerWithTitle(null, text, UIAlertControllerStyleAlert)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    topViewController()?.presentViewController(alert, animated = true, completion = null)
}
