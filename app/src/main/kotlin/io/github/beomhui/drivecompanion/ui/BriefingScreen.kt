package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.beomhui.drivecompanion.BuildConfig
import io.github.beomhui.drivecompanion.core.handover.ArrivalEvidence
import io.github.beomhui.drivecompanion.core.handover.Briefing
import io.github.beomhui.drivecompanion.core.handover.BriefingItem
import io.github.beomhui.drivecompanion.core.handover.DestinationSource
import io.github.beomhui.drivecompanion.core.handover.Importance
import io.github.beomhui.drivecompanion.core.handover.NoteKind

/**
 * 가로로 넓은 차량 화면은 왼쪽에 인사·숫자 요약·버튼을 고정하고, 오른쪽에 목적지 칸과 인계 목록을 둔다. 좁으면 한 줄로 쌓는다.
 * 목적지 칸은 높이가 짧은 화면에서 왼쪽에 넣으면 "도착했어요"가 잘려서, 이번 목적지 인계 바로 위에 둔다.
 */
@Composable
fun BriefingScreen(ui: HandoverUiState, model: HandoverViewModel) {
    val briefing = ui.briefing ?: return
    val enabled = !ui.busy && ui.inputAllowed
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(340.dp).fillMaxHeight()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Overview(briefing)
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { Actions(enabled, model) }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NoteList(ui, briefing, enabled, model, Modifier.weight(1f).fillMaxHeight()) {
                    item(key = "destination") { DestinationPanel(ui, enabled, model) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                NoteList(ui, briefing, enabled, model, Modifier.weight(1f)) {
                    item { Overview(briefing) }
                    item(key = "destination") { DestinationPanel(ui, enabled, model) }
                }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Actions(enabled, model, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 인사, 요약 문장, 숫자 칸. 숫자는 운전자가 한눈에 보도록 크게, 요약 문장은 시연 대본과 같은 문구를 그대로 둔다. */
@Composable
private fun Overview(briefing: Briefing) {
    val scheme = MaterialTheme.colorScheme
    val fresh = (briefing.atDestination + briefing.attention).count { it.needsAcknowledgement }
    val important = (briefing.atDestination + briefing.attention + briefing.carriedOver + briefing.others)
        .count { it.note.kind == NoteKind.TASK && it.note.importance == Importance.HIGH }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${briefing.driver.name}님,\n인수할 내용이에요", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("새로 받음", fresh, scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
            Stat("중요", important, scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f))
            if (briefing.destination != null) {
                Stat("목적지 일", briefing.atDestination.size, scheme.secondaryContainer, scheme.onSecondaryContainer, Modifier.weight(1f))
            }
        }
        Text(briefing.summary, style = MaterialTheme.typography.bodyLarge, color = scheme.primary)
    }
}

@Composable
private fun Stat(label: String, count: Int, container: Color, content: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    // 0이면 눈에 띄지 않게 가라앉힌다. 남은 게 있을 때만 색이 들어온다.
    Surface(
        color = if (count > 0) container else scheme.surfaceVariant,
        contentColor = if (count > 0) content else scheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$count", style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Actions(enabled: Boolean, model: HandoverViewModel, modifier: Modifier = Modifier.fillMaxWidth()) {
    Button(onClick = { model.newNote() }, enabled = enabled, modifier = modifier.heightIn(min = TouchHeight)) {
        Text("인계 남기기")
    }
    OutlinedButton(onClick = model::requestReturn, enabled = enabled, modifier = modifier.heightIn(min = TouchHeight)) {
        Text("반납하기")
    }
}

@Composable
private fun NoteList(
    ui: HandoverUiState,
    briefing: Briefing,
    enabled: Boolean,
    model: HandoverViewModel,
    modifier: Modifier,
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        header()
        val empty = briefing.atDestination.isEmpty() && briefing.attention.isEmpty() &&
            briefing.carriedOver.isEmpty() && briefing.others.isEmpty()
        if (empty) {
            item {
                Text(
                    "진행 중인 인계가 없어요. 차를 넘기기 전에 다음 사람이 알아야 할 내용이 있으면 '인계 남기기'를 눌러주세요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        briefing.destination?.let { section("이번 목적지 · ${it.name}", briefing.atDestination, briefing.driver.id, enabled, model) }
        section("새로 받은 인계", briefing.attention, briefing.driver.id, enabled, model)
        section("계속 남은 중요한 일", briefing.carriedOver, briefing.driver.id, enabled, model)
        section("진행 중인 인계", briefing.others, briefing.driver.id, enabled, model)
        if (ui.recentlyDone.isNotEmpty()) {
            item { SectionTitle("최근 처리한 일") }
            items(ui.recentlyDone, key = { "done-" + it.note.id }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.note.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${entry.actor?.name ?: "알 수 없는 운전자"} · ${formatTime(entry.resolution.performedAt)} 완료" +
                                    (entry.resolution.comment?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { model.reopen(entry.note) }, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                            Text("다시 열기")
                        }
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) item(key = "simulation") { SimulationTools(ui, model) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<BriefingItem>,
    driverId: String,
    enabled: Boolean,
    model: HandoverViewModel,
) {
    if (items.isEmpty()) return
    item(key = "title-$title") { SectionTitle(title, items.size) }
    items(items, key = { it.note.id }) { item ->
        val note = item.note
        val needsAck = item.needsAcknowledgement
        NoteCard(note, item.author?.name, item.place, item.reason, mine = item.author?.id == driverId) {
            TextButton(onClick = { model.editNote(note) }, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                Text("고치기")
            }
            if (note.kind == NoteKind.TASK) {
                OutlinedButton(onClick = { model.requestResolve(note) }, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                    Text("처리 완료")
                }
            } else {
                OutlinedButton(onClick = { model.requestArchive(note) }, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                    Text("정보 내리기")
                }
            }
            if (needsAck) {
                Button(onClick = { model.acknowledge(note) }, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                    Text("확인했어요")
                }
            }
        }
    }
}

/** 내비 목적지가 오면 그대로 쓰고, 없으면 등록 장소 중에서 직접 고른다. 도착은 내비 신호나 직접 확인으로만 받는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestinationPanel(ui: HandoverUiState, enabled: Boolean, model: HandoverViewModel) {
    val destination = ui.destination
    val place = destination?.place
    val scheme = MaterialTheme.colorScheme
    OutlinedCard(Modifier.fillMaxWidth()) {
        // 차량 화면은 세로가 짧다. 목적지가 없을 때는 설명 줄을 빼서 아래 인계가 먼저 보이게 한다.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (destination == null) "어디로 가나요? 고르면 그곳 일을 먼저 보여드려요." else "이번 목적지",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                when (destination?.source) {
                    DestinationSource.NAVIGATION -> Tag("내비 목적지", scheme.secondaryContainer, scheme.onSecondaryContainer)
                    DestinationSource.USER -> Tag("직접 선택", scheme.surfaceVariant, scheme.onSurfaceVariant)
                    null -> Unit
                }
                when (ui.arrivedBy) {
                    // 영상에서도 도착을 무엇으로 알았는지 드러낸다(Q05). 에뮬레이터는 내비 도착 신호를 못 만든다.
                    ArrivalEvidence.NAVIGATION -> Tag("도착 · 내비 신호", scheme.primaryContainer, scheme.onPrimaryContainer)
                    ArrivalEvidence.DRIVER -> Tag("도착 · 직접 확인", scheme.primaryContainer, scheme.onPrimaryContainer)
                    null -> Unit
                }
            }
            if (destination != null) {
                Text(
                    when {
                        place == null -> "'${destination.label}'은 등록된 장소가 아니라서 연결된 인계가 없어요."
                        ui.arrived -> "${place.name}에 도착했어요."
                        else -> "${place.name}에 도착해 주차하면 그곳 인계를 다시 알려드려요."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = destination == null,
                    onClick = { model.chooseDestination(null) },
                    enabled = enabled,
                    label = { Text("정하지 않음", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 12.dp)) },
                )
                model.places.forEach { option ->
                    FilterChip(
                        selected = place?.id == option.id,
                        onClick = { model.chooseDestination(option) },
                        enabled = enabled,
                        label = { Text(option.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 12.dp)) },
                    )
                }
                if (place != null && !ui.arrived) {
                    OutlinedButton(onClick = model::confirmArrival, enabled = enabled, modifier = Modifier.heightIn(min = TouchHeight)) {
                        Text("도착했어요")
                    }
                }
            }
        }
    }
}
