package whl.trending.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import trendingai.chat.generated.resources.Res
import trendingai.chat.generated.resources.chat_waiting_processing
import trendingai.chat.generated.resources.chat_waiting_still_thinking
import trendingai.chat.generated.resources.chat_waiting_thinking

/** 等待文案按此间隔单向推进到下一句，走完后停在最后一句 */
private val WAITING_PHRASE_STEP = 3.seconds

private val waitingPhrases: List<StringResource> = listOf(
    Res.string.chat_waiting_processing,
    Res.string.chat_waiting_thinking,
    Res.string.chat_waiting_still_thinking,
)

/** 首字到达前的等待指示：三个交替闪烁的圆点 + 随等待时长单向推进的文案。 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    var phraseIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (phraseIndex < waitingPhrases.lastIndex) {
            delay(WAITING_PHRASE_STEP)
            phraseIndex++
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TypingDots()
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = phraseIndex,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 150)) + slideInVertically(tween(200, delayMillis = 150)) { it / 3 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "waitingPhrase",
        ) { index ->
            Text(
                text = stringResource(waitingPhrases[index]),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1f at 300
                        0.3f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TypingIndicatorPreview() {
    MaterialTheme {
        TypingIndicator(Modifier.padding(16.dp))
    }
}
