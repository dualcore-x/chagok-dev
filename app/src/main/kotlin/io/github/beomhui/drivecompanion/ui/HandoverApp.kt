package io.github.beomhui.drivecompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.beomhui.drivecompanion.R
import io.github.beomhui.drivecompanion.core.handover.NoteKind

/** 내용 폭을 제한해 가로로 넓은 차량 화면에서도 한눈에 읽히게 한다. */
private val ContentMaxWidth = 960.dp

@Composable
fun HandoverApp(model: HandoverViewModel = viewModel(factory = HandoverViewModel.Factory)) {
    val ui = model.ui
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            model.consumeMessage()
        }
    }
    BackHandler(enabled = ui.screen == Screen.EDITOR) { model.cancelEditor() }

    Scaffold(
        topBar = { TopBar(ui, model.vehicleName) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                ui.warning?.let { Banner(it, "닫기", model::dismissWarning) }
                // 앱을 켠 직후 연결 중일 때는 경고하지 않는다. 오류처럼 보이기 때문이다(입력은 계속 막는다).
                if (!ui.loading && ui.loadError == null && !ui.inputAllowed && !(ui.parked == null && ui.connecting)) {
                    Banner(
                        if (ui.parked == false) "주행 중에는 입력할 수 없어요. 주차한 뒤 이어서 진행해주세요."
                        else "차량 상태를 확인하지 못해 입력을 잠시 막았어요.",
                    )
                }
                when {
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ui.loadError != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(ui.loadError, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "남겨둔 인계는 지우지 않았어요. 다시 시도해도 안 되면 앱을 껐다 켜주세요.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = model::retryLoad, modifier = Modifier.heightIn(min = TouchHeight)) { Text("다시 시도") }
                        }
                    }
                    // 화면이 바뀔 때 살짝 밀리며 겹쳐 사라진다. 앞으로 갈수록 오른쪽에서, 돌아갈 때는 왼쪽에서 들어온다.
                    else -> AnimatedContent(
                        targetState = ui.screen,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { (if (forward) it else -it) / 10 }) togetherWith
                                fadeOut(tween(160))
                        },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.PROFILES -> ProfileScreen(ui, model)
                            Screen.BRIEFING -> BriefingScreen(ui, model)
                            Screen.EDITOR -> ui.editor?.let { NoteEditorScreen(ui, it, model) }
                        }
                    }
                }
            }
        }
    }

    ui.takeover?.let { (next, previous) ->
        AppAlertDialog(
            onDismissRequest = model::dismissTakeover,
            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            title = { Text("${previous?.name ?: "다른 운전자"}님의 사용이 반납되지 않았어요") },
            text = { Text("대신 반납 처리하고 ${next.name}님으로 시작할까요? 반납 기록에는 '대신 종료'로 남아요.") },
            confirmButton = { Button(onClick = model::confirmTakeover) { Text("대신 종료하고 시작") } },
            dismissButton = { TextButton(onClick = model::dismissTakeover) { Text("취소") } },
        )
    }

    ui.arrival?.let { place ->
        val items = ui.arrivalItems
        val hasTask = items.any { it.note.kind == NoteKind.TASK }
        AppAlertDialog(
            onDismissRequest = model::dismissArrival,
            icon = { Icon(Icons.Rounded.Place, contentDescription = null) },
            title = { Text("${place.name}에 도착했어요") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (hasTask) "끝낸 일은 처리 완료로 기록해주세요." else "이곳에 남겨진 참고 정보예요.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${kindLabel(item.note.kind)} · ${item.author?.name ?: "알 수 없는 운전자"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(item.note.text, style = MaterialTheme.typography.bodyLarge)
                            if (item.note.kind == NoteKind.TASK) {
                                FilledTonalButton(
                                    onClick = { model.resolveFromArrival(item.note) },
                                    enabled = !ui.busy && ui.inputAllowed,
                                    modifier = Modifier.heightIn(min = TouchHeight),
                                ) { IconLabel(Icons.Rounded.CheckCircle, "처리 완료로 기록") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::dismissArrival) { Text(if (hasTask) "나중에" else "확인") } },
        )
    }

    ui.resolving?.let { note ->
        var comment by remember(note.id) { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = model::dismissResolve,
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
            title = { Text("처리 완료로 기록할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(note.text, style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.take(200) },
                        label = { Text("처리 메모 (선택)") },
                        supportingText = { Text("예: 구매팀 김OO님께 전달 · ${comment.length}/200") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { Button(onClick = { model.resolve(comment) }, enabled = !ui.busy) { Text("완료") } },
            dismissButton = { TextButton(onClick = model::dismissResolve) { Text("취소") } },
        )
    }

    ui.archiving?.let { note ->
        AppAlertDialog(
            onDismissRequest = model::dismissArchive,
            icon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            title = { Text("이 참고 정보를 내릴까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(note.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "다음 운전자에게 더는 보이지 않고 되돌릴 수 없어요. 내용이 바뀐 거라면 '고치기'로 고쳐주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { Button(onClick = model::archive, enabled = !ui.busy) { Text("내리기") } },
            dismissButton = { TextButton(onClick = model::dismissArchive) { Text("취소") } },
        )
    }

    if (ui.confirmReturn) {
        AppAlertDialog(
            onDismissRequest = model::dismissReturn,
            icon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null) },
            title = { Text("반납할까요?") },
            text = {
                Text(
                    if (ui.openTaskCount > 0) "진행 중인 일 ${ui.openTaskCount}건은 다음 운전자에게 그대로 넘어가요. 새로 남길 내용이 있다면 먼저 남겨주세요."
                    else "남은 일이 없어요. 다음 운전자에게 알릴 내용이 있다면 먼저 남겨주세요.",
                )
            },
            confirmButton = { Button(onClick = model::returnVehicle, enabled = !ui.busy) { Text("반납하기") } },
            dismissButton = {
                Row {
                    TextButton(onClick = model::dismissReturn) { Text("취소") }
                    TextButton(onClick = { model.newNote(returnAfterSave = true) }) { Text("인계 먼저 남기기") }
                }
            },
        )
    }
}

@Composable
private fun TopBar(ui: HandoverUiState, vehicleName: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppMark()
            Column(Modifier.weight(1f)) {
                Text("차곡", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    listOfNotNull(vehicleName, ui.driver?.let { "${it.name}님 사용 중" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Tag(runtimeLabel(ui.snapshot), scheme.secondaryContainer, scheme.onSecondaryContainer)
            when (ui.parked) {
                true -> Tag("주차 중", scheme.primaryContainer, scheme.onPrimaryContainer, dot = Color(0xFF22B573))
                false -> Tag("주행 중", scheme.errorContainer, scheme.onErrorContainer, dot = scheme.error)
                null -> if (ui.connecting) Tag("차량 연결 중", scheme.secondaryContainer, scheme.onSecondaryContainer)
                else Tag("차량 상태 미확인", scheme.errorContainer, scheme.onErrorContainer)
            }
        }
    }
}

/** 상단 제목 옆 작은 앱 표시. 런처 아이콘과 같은 그림을 쓰고, 아이콘의 기본 여백을 잘라 꽉 차게 키운다. */
@Composable
private fun AppMark() {
    Box(
        Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(Color(0xFF2F6BEF)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(78.dp))
    }
}
