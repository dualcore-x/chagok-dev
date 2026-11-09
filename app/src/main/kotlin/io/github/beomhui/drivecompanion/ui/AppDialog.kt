package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * AlertDialog와 같은 모양의 안내창. 안내창은 별도 창이라 앱 화면의 배율(큰 화면에서 키운 크기)을 잃는데,
 * 여기서 LocalAppDensity를 다시 씌워 본문 화면과 같은 크기로 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides (LocalAppDensity.current ?: LocalDensity.current)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).widthIn(min = 320.dp, max = 640.dp),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (icon != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.primary) { icon() }
                        }
                    }
                    if (title != null) {
                        val style = MaterialTheme.typography.titleLarge.copy(textAlign = if (icon != null) TextAlign.Center else TextAlign.Start)
                        Box(Modifier.fillMaxWidth()) {
                            androidx.compose.material3.ProvideTextStyle(style) { Box(Modifier.fillMaxWidth(), contentAlignment = if (icon != null) Alignment.Center else Alignment.CenterStart) { title() } }
                        }
                    }
                    if (text != null) {
                        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { text() }
                        }
                    }
                    // 버튼이 많아 폭이 모자라면 글자를 꺾지 않고 다음 줄로 넘긴다.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
