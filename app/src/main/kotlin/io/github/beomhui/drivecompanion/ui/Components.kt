package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

fun runtimeLabel(snapshot: VehicleSnapshot?): String {
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
fun Tag(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
    // 새로 받거나 바뀐 인계는 강조색 테두리로 먼저 눈에 띄게 한다. 태그는 상태(무엇을 할지)만 색으로 두고, 종류·대상은 아래 한 줄 글자로 내린다.
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = if (emphasized) scheme.surfaceContainerHigh else scheme.surfaceVariant),
        border = if (emphasized) BorderStroke(2.dp, scheme.primary) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reason != null || note.importance == Importance.HIGH) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (reason != null) ReasonTag(reason, mine)
                    if (note.importance == Importance.HIGH) Tag("중요", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                }
            }
            LongText(note.text)
            Text(
                listOf(kindLabel(note.kind), anchorLabel(note.anchor, place), authorName ?: "알 수 없는 운전자", formatTime(note.createdAt))
                    .joinToString(" · ") +
                    if (note.contentUpdatedAt != note.createdAt) " · ${formatTime(note.contentUpdatedAt)} 수정" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            note.snapshot?.let {
                Text("남길 때 차량 상태: ${describeSnapshot(it)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                content = actions,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, count: Int? = null) {
    Text(
        if (count != null) "$text $count" else text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
fun Banner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
