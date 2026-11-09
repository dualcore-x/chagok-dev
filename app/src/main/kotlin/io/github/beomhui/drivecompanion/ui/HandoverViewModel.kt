package io.github.beomhui.drivecompanion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.beomhui.drivecompanion.AppContainer
import io.github.beomhui.drivecompanion.DriveCompanionApp
import io.github.beomhui.drivecompanion.core.handover.ActiveSessionException
import io.github.beomhui.drivecompanion.core.handover.ArrivalEvidence
import io.github.beomhui.drivecompanion.core.handover.Briefing
import io.github.beomhui.drivecompanion.core.handover.BriefingItem
import io.github.beomhui.drivecompanion.core.handover.DemoScenario
import io.github.beomhui.drivecompanion.core.handover.Destination
import io.github.beomhui.drivecompanion.core.handover.DestinationTracker
import io.github.beomhui.drivecompanion.core.handover.DoneEntry
import io.github.beomhui.drivecompanion.core.handover.Driver
import io.github.beomhui.drivecompanion.core.handover.DriveSession
import io.github.beomhui.drivecompanion.core.handover.HandoverException
import io.github.beomhui.drivecompanion.core.handover.HandoverService
import io.github.beomhui.drivecompanion.core.handover.Importance
import io.github.beomhui.drivecompanion.core.handover.Note
import io.github.beomhui.drivecompanion.core.handover.NoteAnchor
import io.github.beomhui.drivecompanion.core.handover.NoteDraft
import io.github.beomhui.drivecompanion.core.handover.NoteKind
import io.github.beomhui.drivecompanion.core.handover.Place
import io.github.beomhui.drivecompanion.core.handover.TaskState
import io.github.beomhui.drivecompanion.core.speech.ListenEvent
import io.github.beomhui.drivecompanion.core.speech.appendHeard
import io.github.beomhui.drivecompanion.core.vehicle.SimulatedVehicleContextSource
import io.github.beomhui.drivecompanion.core.vehicle.VehicleEvent
import io.github.beomhui.drivecompanion.core.vehicle.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { PROFILES, BRIEFING, EDITOR }

/** noteId는 작성 시작 때 한 번 만든다. 저장 버튼을 두 번 눌러도 같은 인계로 처리된다. */
data class EditorState(
    val noteId: String,
    val editing: Boolean,
    val kind: NoteKind = NoteKind.TASK,
    val anchor: NoteAnchor = NoteAnchor.VEHICLE,
    val placeId: String? = null,
    val important: Boolean = false,
    val text: String = "",
    val returnAfterSave: Boolean = false,
    /** 말로 남기는 중. partial은 지금까지 알아들은 말이고, 끝난 결과만 입력칸에 넣는다. */
    val listening: Boolean = false,
    val partial: String = "",
    /** 음성으로 받은 글이 있으면 저장 전에 확인하라고 안내한다. */
    val dictated: Boolean = false,
)

data class HandoverUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val screen: Screen = Screen.PROFILES,
    val session: DriveSession? = null,
    val driver: Driver? = null,
    val briefing: Briefing? = null,
    val recentlyDone: List<DoneEntry> = emptyList(),
    val openTaskCount: Int = 0,
    val parked: Boolean? = null,
    val snapshot: VehicleSnapshot? = null,
    val editor: EditorState? = null,
    val takeover: Pair<Driver, Driver?>? = null,
    val resolving: Note? = null,
    val archiving: Note? = null,
    val confirmReturn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val warning: String? = null,
    val destination: Destination? = null,
    val arrived: Boolean = false,
    val arrivedBy: ArrivalEvidence? = null,
    /** 도착 안내를 띄울 장소. */
    val arrival: Place? = null,
    /** 도착 안내 내용. 안내가 떠 있는 동안 목적지가 바뀌어도 도착한 장소의 인계를 보여준다. */
    val arrivalItems: List<BriefingItem> = emptyList(),
    /** debug 음성 시험 결과. */
    val speechTest: String? = null,
    /** 앱을 켠 직후 차량 서비스에 연결되는 중. 이때 상태를 모르는 건 오류가 아니라서 경고 대신 "연결 중"으로 보여준다. */
    val connecting: Boolean = true,
) {
    /** 주차가 확인됐을 때만 입력을 연다. 미확인도 막는다. */
    val inputAllowed: Boolean get() = parked == true
}

class HandoverViewModel(private val container: AppContainer) : ViewModel() {
    var ui by mutableStateOf(HandoverUiState())
        private set

    val vehicleName: String get() = container.fleet.vehicle.name
    val drivers: List<Driver> get() = container.fleet.drivers
    val places: List<Place> get() = container.fleet.places

    private val service: HandoverService get() = container.service
    private val destinations = DestinationTracker(container.fleet)
    private val subscription = container.vehicle.subscribe { event -> viewModelScope.launch { onVehicleEvent(event) } }

    init {
        load()
        // 연결 완료 신호가 없어서 잠깐 기다린다. 그 뒤에도 상태를 모르면 지금처럼 경고한다.
        viewModelScope.launch {
            delay(CONNECT_GRACE_MS)
            ui = ui.copy(connecting = false)
        }
    }

    /** 불러오기에 실패한 뒤 다시 시도한다. 저장소는 lazy라 실패하면 다음 접근 때 다시 연다. */
    fun retryLoad() {
        if (ui.loading) return
        ui = ui.copy(loading = true, loadError = null)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { container.service } }
                .onSuccess {
                    val warning = container.store.quarantined?.let {
                        "저장된 기록을 읽지 못해 따로 보관하고 새로 시작했어요. (${it.name})"
                    }
                    ui = ui.copy(loading = false, warning = warning)
                    refresh()
                }
                .onFailure { ui = ui.copy(loading = false, loadError = "기록을 불러오지 못했어요.") }
        }
    }

    fun selectDriver(driver: Driver) = guarded {
        lastSelected = driver
        command(onSuccess = { show(Screen.BRIEFING); presentBriefing() }) { startSession(driver.id) }
    }

    fun confirmTakeover() {
        val driver = ui.takeover?.first ?: return
        ui = ui.copy(takeover = null)
        command(onSuccess = { show(Screen.BRIEFING); presentBriefing() }) { startSession(driver.id, takeOver = true) }
    }

    fun dismissTakeover() { ui = ui.copy(takeover = null) }

    fun acknowledge(note: Note) = guarded { command("확인했어요.") { acknowledge(note.id) } }

    fun requestResolve(note: Note) = guarded { ui = ui.copy(resolving = note) }

    fun dismissResolve() { ui = ui.copy(resolving = null) }

    fun resolve(comment: String) {
        val note = ui.resolving ?: return
        command("처리 완료로 기록했어요.", onSuccess = { ui = ui.copy(resolving = null) }) { resolve(note.id, comment) }
    }

    fun reopen(note: Note) = guarded { command("다시 열었어요. 다른 운전자에게도 다시 알려요.") { reopen(note.id) } }

    /** 참고 정보 내리기는 되돌릴 수 없어서 한 번 더 묻는다. */
    fun requestArchive(note: Note) = guarded { ui = ui.copy(archiving = note) }

    fun dismissArchive() { ui = ui.copy(archiving = null) }

    fun archive() {
        val note = ui.archiving ?: return
        command("참고 정보를 내렸어요. 다음 운전자에게 더는 보이지 않아요.", onSuccess = { ui = ui.copy(archiving = null) }) { archive(note.id) }
    }

    /** 목적지가 있으면 장소·물건 인계의 대상 장소를 미리 골라둔다. 차량 인계에서는 저장할 때 버린다. */
    fun newNote(returnAfterSave: Boolean = false) = guarded {
        ui = ui.copy(
            confirmReturn = false,
            editor = EditorState(
                UUID.randomUUID().toString(),
                editing = false,
                placeId = destinations.destination?.place?.id,
                returnAfterSave = returnAfterSave,
            ),
            screen = Screen.EDITOR,
        )
        refreshVehicle()
    }

    fun chooseDestination(place: Place?) = guarded {
        destinations.choose(place)
        refresh()
    }

    fun confirmArrival() = guarded {
        destinations.confirmArrival()
        refresh()
    }

    fun dismissArrival() { ui = ui.copy(arrival = null) }

    fun resolveFromArrival(note: Note) = guarded { ui = ui.copy(arrival = null, resolving = note) }

    fun editNote(note: Note) = guarded {
        ui = ui.copy(
            editor = EditorState(
                noteId = note.id,
                editing = true,
                kind = note.kind,
                anchor = note.anchor,
                placeId = note.targetPlaceId,
                important = note.importance == Importance.HIGH,
                text = note.text,
            ),
            screen = Screen.EDITOR,
        )
    }

    fun updateEditor(change: EditorState.() -> EditorState) {
        ui.editor?.let { ui = ui.copy(editor = it.change()) }
    }

    /** 듣기 요청 번호. 멈춘 뒤 늦게 온 결과나 다른 인계를 쓰던 때의 결과는 버린다. */
    private var listenRequest = 0

    /** 말로 남기기. 알아들은 말은 입력칸 끝에 붙이고, 저장은 운전자가 확인한 뒤 직접 누른다. */
    fun startListening() = guarded {
        val editor = ui.editor?.takeIf { !it.listening } ?: return@guarded
        val request = ++listenRequest
        ui = ui.copy(editor = editor.copy(listening = true, partial = ""))
        container.speech.listen { event -> onListen(request, editor.noteId, event) }
    }

    /** 운전자가 멈추면 그때까지 알아들은 말을 넣는다. */
    fun stopListening() {
        if (ui.editor?.listening != true) return
        container.speech.stop()
        // 포트가 결과를 주지 않고 멈췄을 때도 버튼이 '듣는 중'에 남지 않게 한다.
        ui.editor?.takeIf { it.listening }?.let { onListen(listenRequest, it.noteId, ListenEvent.Cancelled) }
    }

    /** 작성 화면을 떠나거나 주행이 시작되면 듣기를 버린다. */
    private fun abandonListening() {
        if (ui.editor?.listening != true) return
        listenRequest++
        container.speech.stop()
        updateEditor { copy(listening = false, partial = "") }
    }

    private fun onListen(request: Int, noteId: String, event: ListenEvent) {
        val editor = ui.editor?.takeIf { request == listenRequest && it.noteId == noteId && it.listening } ?: return
        if (event is ListenEvent.Partial) {
            ui = ui.copy(editor = editor.copy(partial = event.text))
            return
        }
        val heard = when (event) {
            is ListenEvent.Final -> event.text
            // 시간 초과나 운전자의 멈춤이어도 알아들은 말은 살린다.
            ListenEvent.Cancelled -> editor.partial
            else -> ""
        }
        val result = appendHeard(editor.text, heard, HandoverService.MAX_TEXT)
        ui = ui.copy(
            editor = editor.copy(text = result.text, listening = false, partial = "", dictated = editor.dictated || result.added),
            message = when {
                event is ListenEvent.Error -> "음성을 알아듣지 못했어요. 다시 말하거나 직접 입력해주세요."
                result.clipped -> "${HandoverService.MAX_TEXT}자를 넘는 부분은 넣지 못했어요."
                !result.added -> "알아들은 말이 없어요. 다시 말하거나 직접 입력해주세요."
                else -> null
            } ?: ui.message,
        )
    }

    fun cancelEditor() {
        abandonListening()
        val returnAfter = ui.editor?.returnAfterSave == true
        ui = ui.copy(editor = null, screen = Screen.BRIEFING, confirmReturn = returnAfter)
    }

    fun saveEditor() = guarded {
        abandonListening()
        val editor = ui.editor ?: return@guarded
        val importance = if (editor.important) Importance.HIGH else Importance.NORMAL
        val placeId = editor.placeId.takeIf { editor.anchor != NoteAnchor.VEHICLE }
        command(
            if (editor.editing) "인계를 고쳤어요." else "인계를 남겼어요.",
            onSuccess = { ui = ui.copy(editor = null, screen = Screen.BRIEFING, confirmReturn = editor.returnAfterSave) },
        ) {
            if (editor.editing) {
                editNote(editor.noteId, editor.text, importance, placeId)
            } else {
                createNote(NoteDraft(
                    id = editor.noteId,
                    anchor = editor.anchor,
                    kind = editor.kind,
                    text = editor.text,
                    targetPlaceId = placeId,
                    importance = importance,
                    snapshot = container.vehicle.currentSnapshot(),
                ))
            }
        }
    }

    fun requestReturn() = guarded { ui = ui.copy(confirmReturn = true) }

    fun dismissReturn() { ui = ui.copy(confirmReturn = false) }

    fun returnVehicle() = guarded {
        ui = ui.copy(confirmReturn = false)
        val open = ui.openTaskCount
        command(
            if (open > 0) "반납했어요. 진행 중인 일 ${open}건이 다음 운전자에게 넘어가요." else "반납했어요.",
            onSuccess = { show(Screen.PROFILES) },
        ) { returnVehicle() }
    }

    fun consumeMessage() { ui = ui.copy(message = null) }

    fun dismissWarning() { ui = ui.copy(warning = null) }

    // 개발 도구: debug 빌드 화면에서만 노출한다.
    fun seedDemo() = command("시연 데이터를 넣었어요. 이도윤님으로 시작해보세요.") {
        DemoScenario.seed(this, container.vehicle.currentSnapshot()) { UUID.randomUUID().toString() }
    }

    fun clearAll() = command("모든 기록을 지웠어요.", onSuccess = { show(Screen.PROFILES) }) { clearAll() }

    fun toggleSimulatedParking() {
        (container.vehicle as? SimulatedVehicleContextSource)?.setParked(ui.parked != true)
    }

    val canSimulate: Boolean get() = container.vehicle is SimulatedVehicleContextSource

    fun testSpeak() {
        ui = ui.copy(speechTest = "읽는 중")
        container.speech.speak("이번 목적지 한빛상사에서 할 일이 한 건 있어요. 지난 사용 이후 새로 생기거나 바뀐 인계가 세 건 있어요. 그중 한 건은 중요해요.") { ui = ui.copy(speechTest = "읽기: $it") }
    }

    fun testListen() {
        ui = ui.copy(speechTest = "듣는 중")
        container.speech.listen { ui = ui.copy(speechTest = "듣기: $it") }
    }

    fun testStopSpeech() = container.speech.stop()

    fun simulateNavigation(label: String?) {
        (container.vehicle as? SimulatedVehicleContextSource)?.setDestination(label)
    }

    fun simulateArrival() {
        (container.vehicle as? SimulatedVehicleContextSource)?.arrive()
    }

    private inline fun guarded(block: () -> Unit) {
        if (!ui.inputAllowed) {
            ui = ui.copy(message = if (ui.parked == false) "주행 중에는 할 수 없어요. 주차한 뒤 이어서 해주세요." else "차량 상태를 확인하지 못해 잠시 막았어요.")
            return
        }
        block()
    }

    private fun command(
        success: String? = null,
        onSuccess: () -> Unit = {},
        block: HandoverService.() -> Unit,
    ) {
        if (ui.busy) return
        ui = ui.copy(busy = true)
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { service.block() } }
            ui = ui.copy(busy = false)
            result
                .onSuccess {
                    refresh()
                    onSuccess()
                    if (success != null) ui = ui.copy(message = success)
                }
                .onFailure { error ->
                    refresh()
                    ui = when (error) {
                        is ActiveSessionException -> ui.copy(takeover = pendingDriver(error) to error.driver)
                        is HandoverException -> ui.copy(message = error.message)
                        else -> ui.copy(message = "처리하지 못했어요. 다시 시도해주세요.")
                    }
                }
        }
    }

    private var lastSelected: Driver? = null

    private fun pendingDriver(error: ActiveSessionException): Driver =
        lastSelected ?: container.fleet.drivers.first { it.id != error.session.driverId }

    private fun refresh() {
        val session = service.activeSession()
        val state = service.state()
        // 앱을 켜면 항상 운전자 선택부터. 공용차는 다음에 탄 사람이 앞 사람과 다를 수 있다.
        val screen = if (session == null) Screen.PROFILES else ui.screen
        // 새 세션은 앞 사람의 목적지를 버리고, 이미 설정된 내비 목적지가 있으면 그것부터 쓴다.
        if (destinations.onSession(session?.id) && session != null) destinations.onNavigation(container.vehicle.currentDestination())
        val destination = destinations.destination
        val arrival = ui.arrival.takeIf { session != null }
        ui = ui.copy(
            session = session,
            driver = session?.let { container.fleet.driver(it.driverId) },
            briefing = session?.let { service.briefing(destination?.place) },
            recentlyDone = if (session != null) service.recentlyDone() else emptyList(),
            openTaskCount = state.notes.count { it.taskState == TaskState.OPEN },
            screen = if (screen == Screen.EDITOR && ui.editor == null) Screen.BRIEFING else screen,
            destination = destination,
            arrived = destinations.arrived,
            arrivedBy = destinations.arrivedBy,
            arrival = arrival,
            arrivalItems = arrival?.let { service.briefing(it).atDestination }.orEmpty(),
        )
        refreshVehicle()
        checkArrival()
    }

    private fun refreshVehicle() {
        val parked = container.vehicle.isParked()
        ui = ui.copy(parked = parked, snapshot = container.vehicle.currentSnapshot(), connecting = ui.connecting && parked == null)
        if (!ui.inputAllowed) abandonListening()
    }

    private fun onVehicleEvent(event: VehicleEvent) {
        when (event) {
            is VehicleEvent.DestinationChanged -> destinations.onNavigation(event.label)
            is VehicleEvent.Arrived -> destinations.onArrived(event.label)
            is VehicleEvent.ParkingChanged, is VehicleEvent.AvailabilityChanged -> Unit
        }
        if (ui.loading || ui.loadError != null) refreshVehicle() else refresh()
    }

    /** 도착 근거와 주차가 함께 있을 때 목적지 인계를 한 번 띄운다. 보여준 사실만 남기고 처리는 사용자가 직접 한다. */
    private fun checkArrival() {
        if (ui.session == null) return
        val place = destinations.takeArrival(ui.parked) ?: return
        val items = service.briefing(place).atDestination
        if (items.isEmpty()) return
        ui = ui.copy(arrival = place, arrivalItems = items)
        markPresented(items.filter { it.needsAcknowledgement }.map { it.note.id })
    }

    private fun show(screen: Screen) { ui = ui.copy(screen = screen) }

    /** 화면에 보여준 사실만 기록한다. 확인은 사용자가 직접 누른다. */
    private fun presentBriefing() {
        val briefing = ui.briefing ?: return
        markPresented((briefing.atDestination.filter { it.needsAcknowledgement } + briefing.attention).map { it.note.id })
    }

    private fun markPresented(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { service.markPresented(ids) } } }
    }

    override fun onCleared() {
        abandonListening()
        subscription.close()
    }

    companion object {
        /** Pleos 차량 서비스는 보통 1~2초 안에 붙는다. 여유를 두고 기다린다. */
        private const val CONNECT_GRACE_MS = 6_000L

        val Factory = viewModelFactory {
            initializer { HandoverViewModel((this[APPLICATION_KEY] as DriveCompanionApp).container) }
        }
    }
}
