package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.beomhui.drivecompanion.core.handover.BriefingReason
import io.github.beomhui.drivecompanion.core.handover.Importance
import io.github.beomhui.drivecompanion.core.handover.Note
import io.github.beomhui.drivecompanion.core.handover.NoteAnchor
import io.github.beomhui.drivecompanion.core.handover.NoteKind
import io.github.beomhui.drivecompanion.core.handover.Place
import io.github.beomhui.drivecompanion.core.vehicle.RuntimeEnvironment
import io.github.beomhui.drivecompanion.core.vehicle.describeSnapshot
import io.github.beomhui.drivecompanion.core.vehicle.VehicleSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 차량 화면 최소 터치 높이. */
val TouchHeight = 64.dp

private val timeFormat = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

fun formatTime(epochMillis: Long): String = timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** 실행 환경과 데이터 출처(Q05). 둘 다 모르는 시뮬레이션이면 짧게 "시뮬레이션"만 쓴다. */
fun runtimeLabel(snapshot: VehicleSnapshot?): String {
    if (snapshot != null && snapshot.isSimulated && snapshot.runtime == RuntimeEnvironment.UNKNOWN) return "시뮬레이션"
    val runtime = when (snapshot?.runtime) {
        RuntimeEnvironment.EMULATOR -> "에뮬레이터"
        RuntimeEnvironment.VEHICLE -> "실차"
        else -> "실행 환경 미확인"
    }
    val data = when {
        snapshot == null -> "차량 데이터 없음"
        snapshot.isSimulated -> "시뮬레이션 데이터"
        else -> "차량 데이터"
    }
    return "$runtime · $data"
}

fun anchorLabel(anchor: NoteAnchor, place: Place?): String = when (anchor) {
    NoteAnchor.VEHICLE -> "차량"
    NoteAnchor.PLACE -> "장소 · ${place?.name ?: "미지정"}"
    NoteAnchor.ITEM -> if (place != null) "물건 · ${place.name}" else "물건"
}

fun kindLabel(kind: NoteKind) = if (kind == NoteKind.TASK) "해야 할 일" else "참고 정보"

@Composable
/** 둥근 알약 모양 표시. dot이면 앞에 작은 상태 점을 찍는다(주차 중 같은 실시간 상태). */
fun Tag(text: String, container: Color, content: Color, dot: Color? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** mine: 이 운전자가 남긴 인계. 남길 때 확인한 것으로 기록되므로 "확인함" 대신 작성자임을 보여준다. */
@Composable
fun ReasonTag(reason: BriefingReason, mine: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    when (reason) {
        BriefingReason.NEW -> Tag("새 인계", scheme.primary, scheme.onPrimary)
        BriefingReason.UPDATED -> Tag("내용 바뀜", scheme.primary, scheme.onPrimary)
        BriefingReason.CARRIED_OVER -> Tag("계속 남은 일", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        BriefingReason.ACKNOWLEDGED -> Tag(if (mine) "내가 남김" else "확인함", scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    note: Note,
    authorName: String?,
    place: Place?,
    reason: BriefingReason?,
    mine: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val emphasized = reason == BriefingReason.NEW || reason == BriefingReason.UPDATED
    // 새로 받거나 바뀐 인계는 강조색 테두리로 먼저 눈에 띄게 한다. 색 태그는 상태만, 나머지 정보는 회색 한 줄로 모은다.
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().then(if (emphasized) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.large) else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = if (emphasized) scheme.surfaceContainerHigh else scheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reason != null) ReasonTag(reason, mine)
                if (note.importance == Importance.HIGH) Tag("중요", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                Spacer(Modifier.weight(1f))
                Icon(anchorIcon(note.anchor), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    "${kindLabel(note.kind)} · ${anchorLabel(note.anchor, place)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            LongText(note.text)
            Text(
                listOfNotNull(
                    authorName ?: "알 수 없는 운전자",
                    formatTime(note.createdAt) + if (note.contentUpdatedAt != note.createdAt) " (${formatTime(note.contentUpdatedAt)} 수정)" else "",
                    note.snapshot?.let(::describeSnapshot),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                content = actions,
            )
        }
    }
}

/** 섹션 이름 옆에 건수를 작은 동그라미로 붙인다. */
@Composable
fun SectionTitle(text: String, count: Int? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        if (count != null) Tag("$count", scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}
@Composable
fun Banner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = TouchHeight)) { Text(actionLabel) }
            }
        }
    }
}

/** 접은 상태의 줄 수. 차량 화면에서 카드 하나가 화면을 다 차지하지 않게 한다. */
private const val CollapsedLines = 4

/** 긴 인계는 접어서 보여주고, 넘칠 때만 펼치기 버튼을 단다. */
@Composable
private fun LongText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else CollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
        )
        if (clipped || expanded) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = TouchHeight)) {
                Text(if (expanded) "접기" else "전체 보기")
            }
        }
    }
}
