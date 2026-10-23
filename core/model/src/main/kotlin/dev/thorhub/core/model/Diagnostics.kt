package dev.thorhub.core.model

import java.time.Instant

enum class LifecycleEventType {
    APP_STARTED,
    APP_STOPPED,
    DISPLAY_TOPOLOGY_CHANGED,
    HOME_ROLE_CHANGED,
    COMPANION_LAUNCHED,
    COMPANION_LOST,
    SCREEN_STATE_CHANGED,
    LID_SIGNAL_RECEIVED,
    SESSION_CREATED,
    SESSION_ACTIVATED,
    SESSION_SUSPENDED,
    SESSION_RESUMED,
    SESSION_RECOVERED,
    SESSION_ENDED,
    EXTERNAL_EXIT_REQUESTED,
    UNKNOWN,
}

data class LifecycleEvent(
    val type: LifecycleEventType,
    val observedAt: Instant = Instant.EPOCH,
    val sessionId: GameSessionId? = null,
    val displayId: DisplayId? = null,
    val detail: String? = null,
)

enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

enum class DiagnosticCategory {
    DISPLAY,
    HOME_ROLE,
    LAUNCH,
    LIBRARY,
    SAVE,
    SYNC,
    SESSION,
    CAPABILITY,
    LIFECYCLE,
    SYSTEM,
    UNKNOWN,
}

enum class DiagnosticSensitivity {
    PUBLIC,
    PRIVATE,
    PRIVATE_PATH,
    SECRET,
}

sealed interface DiagnosticValue {
    data class Text(val value: String) : DiagnosticValue
    data class Integer(val value: Long) : DiagnosticValue
    data class BooleanValue(val value: Boolean) : DiagnosticValue
    data object Null : DiagnosticValue
    data object Redacted : DiagnosticValue
}

data class DiagnosticAttribute(
    val key: String,
    val value: DiagnosticValue,
    val sensitivity: DiagnosticSensitivity = DiagnosticSensitivity.PUBLIC,
) {
    init {
        require(key.isNotBlank()) { "Diagnostic attribute key must not be blank" }
    }
}

data class DiagnosticEvent(
    val id: DiagnosticEventId,
    val occurredAt: Instant,
    val severity: DiagnosticSeverity,
    val category: DiagnosticCategory,
    val code: String,
    val message: String,
    val attributes: List<DiagnosticAttribute> = emptyList(),
    val lifecycle: LifecycleEvent? = null,
) {
    init {
        require(code.isNotBlank()) { "Diagnostic event code must not be blank" }
        require(message.isNotBlank()) { "Diagnostic event message must not be blank" }
    }
}

data class DiagnosticProbeSummary(
    val adapterId: LauncherAdapterId,
    val state: ProbeState,
    val version: String? = null,
    val evidence: List<DiagnosticAttribute> = emptyList(),
)

data class DiagnosticReport(
    val appVersion: String,
    val buildFingerprint: BuildFingerprint?,
    val topology: DisplayTopology?,
    val homeRole: HomeRoleStatus,
    val probes: List<DiagnosticProbeSummary> = emptyList(),
    val capabilities: List<CapabilityRecord> = emptyList(),
    val recentEvents: List<DiagnosticEvent> = emptyList(),
    val generatedAt: Instant = Instant.EPOCH,
) {
    init {
        require(appVersion.isNotBlank()) { "Diagnostic app version must not be blank" }
    }
}

/** A report explicitly marked as safe for export after sensitive values are redacted. */
data class RedactedDiagnosticReport(
    val report: DiagnosticReport,
    val redactedAttributeCount: Int,
)

object DiagnosticRedactor {
    private val sensitiveKeyFragments = setOf("password", "secret", "token", "api_key", "apikey", "credential")
    private val privateKeyFragments = setOf("path", "uri", "document", "root")

    fun redact(report: DiagnosticReport): RedactedDiagnosticReport {
        var count = 0
        fun redactAttribute(attribute: DiagnosticAttribute): DiagnosticAttribute {
            val key = attribute.key.lowercase()
            val inferred = when {
                sensitiveKeyFragments.any(key::contains) -> DiagnosticSensitivity.SECRET
                privateKeyFragments.any(key::contains) -> DiagnosticSensitivity.PRIVATE_PATH
                else -> attribute.sensitivity
            }
            if (inferred == DiagnosticSensitivity.PUBLIC) return attribute
            count += 1
            return attribute.copy(value = DiagnosticValue.Redacted, sensitivity = inferred)
        }

        val redactedEvents = report.recentEvents.map { event ->
            event.copy(attributes = event.attributes.map(::redactAttribute))
        }
        val redactedProbes = report.probes.map { probe ->
            probe.copy(evidence = probe.evidence.map(::redactAttribute))
        }
        return RedactedDiagnosticReport(
            report = report.copy(
                probes = redactedProbes,
                recentEvents = redactedEvents,
            ),
            redactedAttributeCount = count,
        )
    }
}

/**
 * In-memory bounded event storage.  Persistence belongs to the data module;
 * this type makes the bound and eviction behavior testable in the JVM core.
 */
class DiagnosticEventRingBuffer(capacity: Int) {
    private val maxSize = capacity.also { require(it > 0) { "Diagnostic ring capacity must be positive" } }
    private val events = ArrayDeque<DiagnosticEvent>(maxSize)

    @Synchronized
    fun append(event: DiagnosticEvent) {
        if (events.size == maxSize) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
