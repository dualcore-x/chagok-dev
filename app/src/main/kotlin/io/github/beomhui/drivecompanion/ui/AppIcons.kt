package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.github.beomhui.drivecompanion.core.handover.NoteAnchor

/** 기본 아이콘 묶음에 없는 차·물건·마이크. 24dp 격자에 둥근 선으로 그려 기본 아이콘과 같은 무게로 맞춘다. */
object AppIcons {
    val Car: ImageVector by lazy {
        icon("Car") {
            // 차체와 지붕
            moveTo(5f, 11f); lineTo(6.6f, 6.6f); curveTo(6.9f, 5.7f, 7.7f, 5f, 8.7f, 5f)
            lineTo(15.3f, 5f); curveTo(16.3f, 5f, 17.1f, 5.7f, 17.4f, 6.6f); lineTo(19f, 11f)
            curveTo(20.1f, 11.2f, 21f, 12.1f, 21f, 13.3f); lineTo(21f, 17f); curveTo(21f, 17.6f, 20.6f, 18f, 20f, 18f)
            lineTo(19f, 18f); lineTo(19f, 19f); curveTo(19f, 19.6f, 18.6f, 20f, 18f, 20f); lineTo(17f, 20f)
            curveTo(16.4f, 20f, 16f, 19.6f, 16f, 19f); lineTo(16f, 18f); lineTo(8f, 18f); lineTo(8f, 19f)
            curveTo(8f, 19.6f, 7.6f, 20f, 7f, 20f); lineTo(6f, 20f); curveTo(5.4f, 20f, 5f, 19.6f, 5f, 19f)
            lineTo(5f, 18f); lineTo(4f, 18f); curveTo(3.4f, 18f, 3f, 17.6f, 3f, 17f); lineTo(3f, 13.3f)
            curveTo(3f, 12.1f, 3.9f, 11.2f, 5f, 11f); close()
            // 앞유리(빈칸)
            moveTo(7.2f, 11f); lineTo(16.8f, 11f); lineTo(15.6f, 7.4f); curveTo(15.5f, 7.2f, 15.3f, 7f, 15.1f, 7f)
            lineTo(8.9f, 7f); curveTo(8.7f, 7f, 8.5f, 7.2f, 8.4f, 7.4f); close()
            // 전조등
            moveTo(7f, 13.5f); curveTo(6.2f, 13.5f, 5.5f, 14.2f, 5.5f, 15f); curveTo(5.5f, 15.8f, 6.2f, 16.5f, 7f, 16.5f)
            curveTo(7.8f, 16.5f, 8.5f, 15.8f, 8.5f, 15f); curveTo(8.5f, 14.2f, 7.8f, 13.5f, 7f, 13.5f); close()
            moveTo(17f, 13.5f); curveTo(16.2f, 13.5f, 15.5f, 14.2f, 15.5f, 15f); curveTo(15.5f, 15.8f, 16.2f, 16.5f, 17f, 16.5f)
            curveTo(17.8f, 16.5f, 18.5f, 15.8f, 18.5f, 15f); curveTo(18.5f, 14.2f, 17.8f, 13.5f, 17f, 13.5f); close()
        }
    }

    val Box: ImageVector by lazy {
        icon("Box") {
            moveTo(12f, 2.5f); lineTo(20.5f, 6.8f); lineTo(20.5f, 17.2f); lineTo(12f, 21.5f); lineTo(3.5f, 17.2f)
            lineTo(3.5f, 6.8f); close()
            // 윗면 접힘선과 가운데 세로선(빈칸)
            moveTo(12f, 4.7f); lineTo(6.2f, 7.6f); lineTo(12f, 10.5f); lineTo(17.8f, 7.6f); close()
            moveTo(11.2f, 11.9f); lineTo(5.5f, 9f); lineTo(5.5f, 16f); lineTo(11.2f, 18.9f); close()
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            moveTo(12f, 14f); curveTo(13.7f, 14f, 15f, 12.7f, 15f, 11f); lineTo(15f, 5f)
            curveTo(15f, 3.3f, 13.7f, 2f, 12f, 2f); curveTo(10.3f, 2f, 9f, 3.3f, 9f, 5f); lineTo(9f, 11f)
            curveTo(9f, 12.7f, 10.3f, 14f, 12f, 14f); close()
            moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.8f, 16.1f, 12f, 16.1f); curveTo(9.2f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f); curveTo(5f, 14.4f, 7.7f, 17.2f, 11f, 17.7f); lineTo(11f, 21f); lineTo(13f, 21f)
            lineTo(13f, 17.7f); curveTo(16.3f, 17.2f, 19f, 14.4f, 19f, 11f); close()
        }
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd, pathBuilder = block)
            .build()
}

/** 인계 대상(차량·장소·물건)을 나타내는 작은 아이콘. */
fun anchorIcon(anchor: NoteAnchor): ImageVector = when (anchor) {
    NoteAnchor.VEHICLE -> AppIcons.Car
    NoteAnchor.PLACE -> Icons.Rounded.Place
    NoteAnchor.ITEM -> AppIcons.Box
}

/** 버튼 안 아이콘 + 글자. 버튼마다 같은 크기와 간격을 쓴다. */
@Composable
fun RowScope.IconLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}
