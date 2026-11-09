package io.github.beomhui.drivecompanion.ui

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.beomhui.drivecompanion.BuildConfig
import io.github.beomhui.drivecompanion.core.handover.Driver

/** 운전자를 이름 카드로 고른다. 설명 문장 대신 작은 상태 표시만 둔다. */
@Composable
fun ProfileScreen(ui: HandoverUiState, model: HandoverViewModel) {
    val scheme = MaterialTheme.colorScheme
    val current = ui.driver
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("누가 운전하나요?", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) Tag("${current.name}님 사용 중", scheme.primaryContainer, scheme.onPrimaryContainer)
                if (ui.openTaskCount > 0) Tag("남은 일 ${ui.openTaskCount}건", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                else Tag("남은 일 없음", scheme.secondaryContainer, scheme.onSecondaryContainer)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 640.dp
            val cards: @Composable (Modifier) -> Unit = { modifier ->
                model.drivers.forEachIndexed { index, driver ->
                    DriverCard(driver, index, current?.id == driver.id, !ui.busy && ui.inputAllowed, modifier) { model.selectDriver(driver) }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { cards(Modifier.weight(1f)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { cards(Modifier.fillMaxWidth()) }
            }
        }
        Text(
            "시연 버전은 PIN 없이 이름으로 시작해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (BuildConfig.DEBUG) {
            DeveloperTools(ui, model)
            SimulationTools(ui, model)
        }
    }
}

@Composable
private fun DriverCard(driver: Driver, index: Int, inUse: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 운전자마다 다른 색 동그라미에 이름 두 글자. 한눈에 자기 카드를 찾게 한다.
    val (circle, onCircle) = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
    )[index % 3]
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.heightIn(min = 96.dp).then(if (inUse) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.large) else Modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = circle, contentColor = onCircle, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(driver.name.drop(1).ifEmpty { driver.name }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(driver.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                if (inUse) Tag("이어서 사용", scheme.primary, scheme.onPrimary)
                else Text("사용 시작", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
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
