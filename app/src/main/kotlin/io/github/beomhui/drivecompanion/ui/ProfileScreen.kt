package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.beomhui.drivecompanion.BuildConfig

@Composable
fun ProfileScreen(ui: HandoverUiState, model: HandoverViewModel) {
    val drivers = model.drivers
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("누가 운전하나요?", style = MaterialTheme.typography.headlineMedium)
        val current = ui.driver
        if (current != null) {
            Text(
                "${current.name}님이 사용 중이에요. 이어서 쓰려면 ${current.name}님을, 다른 분이면 본인 이름을 눌러주세요.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (ui.openTaskCount > 0) "이 차에 진행 중인 일 ${ui.openTaskCount}건이 남아 있어요. 사용을 시작하면 받을 내용부터 보여드려요."
            else "이 차에 남아 있는 일이 없어요.",
            style = MaterialTheme.typography.bodyLarge,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 640.dp
            val buttons: @Composable (Modifier) -> Unit = { modifier ->
                drivers.forEach { driver ->
                    Button(
                        onClick = { model.selectDriver(driver) },
                        enabled = !ui.busy && ui.inputAllowed,
                        modifier = modifier.heightIn(min = 96.dp),
                    ) {
                        Column {
                            Text(driver.name, style = MaterialTheme.typography.titleLarge)
                            Text(if (driver.id == current?.id) "이어서 사용" else "사용 시작", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { buttons(Modifier.weight(1f)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons(Modifier.fillMaxWidth()) }
            }
        }
        Text(
            "시연 버전은 PIN 없이 프로필 선택으로 시작해요. 앞 사람이 반납하지 않았다면 대신 종료할지 물어봐요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.DEBUG) {
            DeveloperTools(ui, model)
            SimulationTools(ui, model)
        }
    }
}

@Composable
private fun DeveloperTools(ui: HandoverUiState, model: HandoverViewModel) {
    HorizontalDivider()
    Text("개발 도구 · debug 빌드에만 보여요", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = model::seedDemo, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = TouchHeight)) {
            Text("시연 데이터 넣기 · 김하늘님이 인계 3건을 남기고 반납")
        }
        OutlinedButton(onClick = model::clearAll, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = TouchHeight)) {
            Text("모든 인계 기록 지우기")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::testSpeak, modifier = Modifier.heightIn(min = TouchHeight)) { Text("음성 시험: 읽기") }
            OutlinedButton(onClick = model::testListen, modifier = Modifier.heightIn(min = TouchHeight)) { Text("음성 시험: 듣기") }
            OutlinedButton(onClick = model::testStopSpeech, modifier = Modifier.heightIn(min = TouchHeight)) { Text("음성 시험: 중지") }
        }
        ui.speechTest?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** 차량·내비 시뮬레이션. 실제 어댑터가 연결되면 보이지 않는다. 주차 ≠ 도착을 나눠 시연하려고 버튼을 따로 둔다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulationTools(ui: HandoverUiState, model: HandoverViewModel) {
    if (!model.canSimulate) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("시뮬레이션 · debug 빌드에만 보여요", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::toggleSimulatedParking, modifier = Modifier.heightIn(min = TouchHeight)) {
                Text(if (ui.parked == true) "주행 중으로 바꾸기" else "주차로 바꾸기")
            }
            model.places.forEach { place ->
                OutlinedButton(onClick = { model.simulateNavigation(place.name) }, modifier = Modifier.heightIn(min = TouchHeight)) {
                    Text("내비 목적지: ${place.name}")
                }
            }
            OutlinedButton(onClick = { model.simulateNavigation(null) }, modifier = Modifier.heightIn(min = TouchHeight)) {
                Text("내비 경로 취소")
            }
            OutlinedButton(onClick = model::simulateArrival, modifier = Modifier.heightIn(min = TouchHeight)) {
                Text("내비 도착 신호")
            }
        }
    }
}
