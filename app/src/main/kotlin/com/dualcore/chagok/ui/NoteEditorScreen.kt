package com.dualcore.chagok.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dualcore.chagok.core.handover.HandoverService
import com.dualcore.chagok.core.handover.NoteAnchor
import com.dualcore.chagok.core.handover.NoteKind
import com.dualcore.chagok.core.vehicle.describeSnapshot

/** 가로로 넓은 차량 화면에서는 내용(왼쪽)과 선택지(오른쪽)를 나눠 스크롤 없이 보이게 한다. */
@Composable
fun NoteEditorScreen(ui: HandoverUiState, editor: EditorState, model: HandoverViewModel) {
    val enabled = !ui.busy && ui.inputAllowed
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth > 720.dp) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(
                        Modifier.weight(1.1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { ContentSection(ui, editor, model) }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { OptionsSection(editor, model) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContentSection(ui, editor, model)
                    OptionsSection(editor, model)
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(onClick = model::cancelEditor, modifier = Modifier.weight(1f).heightIn(min = TouchHeight)) { Text("취소") }
                Button(
                    onClick = model::saveEditor,
                    enabled = enabled && !editor.listening && editor.text.isNotBlank() && (editor.anchor != NoteAnchor.PLACE || editor.placeId != null),
                    modifier = Modifier.weight(1f).heightIn(min = TouchHeight),
                ) { IconLabel(Icons.Rounded.Check, "저장") }
            }
        }
    }
}

@Composable
private fun ContentSection(ui: HandoverUiState, editor: EditorState, model: HandoverViewModel) {
    val max = HandoverService.MAX_TEXT
    Text(if (editor.editing) "인계 고치기" else "인계 남기기", style = MaterialTheme.typography.headlineMedium)
    if (editor.editing) {
        Text(
            "고치면 다른 운전자에게 '내용 바뀜'으로 다시 보여요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedTextField(
        value = editor.text,
        onValueChange = { text -> model.updateEditor { copy(text = text.take(max)) } },
        label = { Text("내용") },
        placeholder = {
            Text(
                if (editor.kind == NoteKind.TASK) "예: 운전석 와이퍼에서 소리가 나요. 정비 예약이 필요해요."
                else "예: 충전 케이블은 본사 B2 관리실 선반에 있어요.",
            )
        },
        supportingText = { Text("${editor.text.length}/$max") },
        minLines = 3,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    DictationRow(ui, editor, model)
    if (!editor.editing) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("함께 남는 차량 상태", style = MaterialTheme.typography.titleMedium)
                Text(ui.snapshot?.let(::describeSnapshot) ?: "차량 값 미수신", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 말로 남기기. 알아들은 말은 입력칸에 넣기만 하고, 저장은 운전자가 확인한 뒤 누른다. */
@Composable
private fun DictationRow(ui: HandoverUiState, editor: EditorState, model: HandoverViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (editor.listening) {
                Button(onClick = model::stopListening, modifier = Modifier.heightIn(min = TouchHeight)) { IconLabel(Icons.Rounded.Close, "멈추기") }
                Text("듣고 있어요…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            } else {
                FilledTonalButton(
                    onClick = model::startListening,
                    enabled = !ui.busy && ui.inputAllowed && editor.text.length < HandoverService.MAX_TEXT,
                    modifier = Modifier.heightIn(min = TouchHeight),
                ) { IconLabel(AppIcons.Mic, "말로 남기기") }
            }
        }
        if (editor.listening && editor.partial.isNotBlank()) {
            Text("“${editor.partial}”", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
        if (editor.dictated && !editor.listening) {
            Text(
                "숫자·층수(B1, B2)는 잘못 들릴 수 있어요. 저장 전에 확인해주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.tertiary,
            )
        }
    }
}

@Composable
private fun OptionsSection(editor: EditorState, model: HandoverViewModel) {
    ChoiceGroup("무엇을 남기나요?") {
        NoteKind.entries.forEach { kind ->
            Choice(kindLabel(kind), editor.kind == kind, !editor.editing, if (kind == NoteKind.TASK) Icons.Rounded.CheckCircle else Icons.Rounded.Info) { model.updateEditor { copy(kind = kind) } }
        }
    }
    ChoiceGroup("무엇에 관한 건가요?") {
        listOf(NoteAnchor.VEHICLE to "차량", NoteAnchor.PLACE to "장소", NoteAnchor.ITEM to "물건").forEach { (anchor, label) ->
            Choice(label, editor.anchor == anchor, !editor.editing, anchorIcon(anchor)) { model.updateEditor { copy(anchor = anchor) } }
        }
    }
    if (editor.anchor != NoteAnchor.VEHICLE) {
        ChoiceGroup(if (editor.anchor == NoteAnchor.PLACE) "어느 장소인가요?" else "어디로 가져가나요? (선택)") {
            if (editor.anchor == NoteAnchor.ITEM) {
                Choice("지정 안 함", editor.placeId == null, true) { model.updateEditor { copy(placeId = null) } }
            }
            model.places.forEach { place ->
                Choice(place.name, editor.placeId == place.id, true) { model.updateEditor { copy(placeId = place.id) } }
            }
        }
    }
    // 작은 스위치만이 아니라 글자 줄 전체를 눌러도 켜지고 꺼지게 한다. 차 화면에서는 정확히 누르기 어렵다.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TouchHeight)
            .toggleable(value = editor.important, role = Role.Switch) { checked -> model.updateEditor { copy(important = checked) } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("중요한 일로 표시", style = MaterialTheme.typography.titleMedium)
            Text(
                "먼저 보여주고, 끝날 때까지 남겨요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = editor.important, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        label = { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 12.dp)) },
    )
}
