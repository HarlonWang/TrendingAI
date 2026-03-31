package whl.trending.ai.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.icon_openai_dark
import trendingai.shared.generated.resources.icon_openai_light

@Composable
fun AiSummaryBox(summary: String, modifier: Modifier = Modifier) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = summary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Icon(
            painter = painterResource(
                if (isDarkTheme) Res.drawable.icon_openai_dark else Res.drawable.icon_openai_light
            ),
            contentDescription = "ChatGPT",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.End)
        )
    }
}
