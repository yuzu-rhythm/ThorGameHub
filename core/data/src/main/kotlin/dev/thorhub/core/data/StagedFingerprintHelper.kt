package dev.thorhub.core.data

import android.content.ContentResolver
import android.net.Uri
import dev.thorhub.core.database.ContentFingerprintEntity
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.CheapContentIdentity
import dev.thorhub.core.model.ContentFingerprint
import dev.thorhub.core.model.ContentHash
import dev.thorhub.core.model.FingerprintAlgorithm
import dev.thorhub.core.model.FingerprintRequest
import dev.thorhub.core.model.FingerprintTier
import dev.thorhub.core.model.FingerprintWork
import dev.thorhub.core.model.StagedFingerprintPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class FingerprintResult(
    val decision: dev.thorhub.core.model.FingerprintDecision,
    val fingerprint: ContentFingerprint,
)

/** A cache abstraction keeps the scanner independent from Room in JVM tests. */
interface FingerprintCache {
    suspend fun find(identity: CheapContentIdentity): ContentFingerprint?

    suspend fun save(fingerprint: ContentFingerprint)
}

object EmptyFingerprintCache : FingerprintCache {
    override suspend fun find(identity: CheapContentIdentity): ContentFingerprint? = null

    override suspend fun save(fingerprint: ContentFingerprint) = Unit
}

class InMemoryFingerprintCache : FingerprintCache {
    private val values = ConcurrentHashMap<String, ContentFingerprint>()

    override suspend fun find(identity: CheapContentIdentity): ContentFingerprint? = values[cacheKey(identity)]

    override suspend fun save(fingerprint: ContentFingerprint) {
        values[cacheKey(fingerprint.identity)] = fingerprint
    }

    private fun cacheKey(identity: CheapContentIdentity): String = listOf(
        identity.sourceKey,
        identity.rootId?.value.orEmpty(),
        identity.sizeBytes.toString(),
        identity.modifiedAt?.toString().orEmpty(),
        identity.extension.orEmpty(),
        identity.normalizedName,
    ).joinToString("\u0000")
}

/**
 * Computes the staged fingerprint selected by [StagedFingerprintPolicy].
 *
 * The default helper request never permits a full hash. Callers must pass
 * [allowFullHash] explicitly (normally from a user-requested or idle worker
 * operation) before a full stream is read.
 */
class StagedFingerprintHelper(
    private val contentResolver: ContentResolver,
    private val cache: FingerprintCache = EmptyFingerprintCache,
    private val quickWindowBytes: Int = DEFAULT_QUICK_WINDOW_BYTES,
    private val clock: () -> Instant = { Instant.now() },
) {
    init {
        require(quickWindowBytes > 0) { "Quick fingerprint window must be positive" }
    }

    fun decide(
        identity: CheapContentIdentity,
        previous: ContentFingerprint? = null,
        allowFullHash: Boolean = false,
        contentAmbiguous: Boolean = false,
    ): dev.thorhub.core.model.FingerprintDecision {
        return StagedFingerprintPolicy.decide(
            current = identity,
            previous = previous,
            request = policyRequest(
                allowFullHash = allowFullHash,
                contentAmbiguous = contentAmbiguous,
            ),
        )
    }

    suspend fun compute(
        uri: Uri,
        identity: CheapContentIdentity,
        allowFullHash: Boolean = false,
        contentAmbiguous: Boolean = false,
        previous: ContentFingerprint? = null,
    ): FingerprintResult = withContext(Dispatchers.IO) {
        val cachedPrevious = previous ?: cache.find(identity)
        val decision = decide(identity, cachedPrevious, allowFullHash, contentAmbiguous)
        val fingerprint = when (decision.work) {
            FingerprintWork.REUSE_EXISTING -> decision.reuse
                ?: error("A reuse decision must contain a cached fingerprint")

            FingerprintWork.COMPUTE_QUICK -> computeQuick(uri, identity)
            FingerprintWork.COMPUTE_FULL -> {
                check(allowFullHash) { "Full fingerprint computation requires explicit permission" }
                computeFull(uri, identity)
            }

            FingerprintWork.UNKNOWN -> computeQuick(uri, identity)
        }
        cache.save(fingerprint)
        FingerprintResult(decision, fingerprint)
    }

    private fun policyRequest(
        allowFullHash: Boolean,
        contentAmbiguous: Boolean,
    ): FingerprintRequest = FingerprintRequest(
        requireFull = allowFullHash,
        contentAmbiguous = contentAmbiguous && allowFullHash,
        allowExpensiveWork = allowFullHash,
        // Full hashing is opt-in even for small cartridge images.
        preferFullForSmallContent = false,
    )

    private fun computeQuick(uri: Uri, identity: CheapContentIdentity): ContentFingerprint {
        val first = readWindow(uri, offset = 0L, length = quickWindowBytes)
        val tailOffset = (identity.sizeBytes - quickWindowBytes).coerceAtLeast(0L)
        val tail = if (tailOffset > 0L) {
            readWindow(uri, offset = tailOffset, length = quickWindowBytes)
        } else {
            first
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("thorhub-quick-v1\u0000".toByteArray())
        digest.update(identity.sizeBytes.toString().toByteArray())
        digest.update('\u0000'.code.toByte())
        digest.update(first)
        digest.update('\u0000'.code.toByte())
        digest.update(tail)
        return ContentFingerprint(
            identity = identity,
            tier = FingerprintTier.QUICK,
            algorithm = FingerprintAlgorithm.QUICK_WINDOW,
            hash = ContentHash.of(LibraryRecordIds.sha256Hex(digest.digest())),
            computedAt = clock(),
        )
    }

    private fun computeFull(uri: Uri, identity: CheapContentIdentity): ContentFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(FULL_HASH_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        } ?: error("Content provider returned no stream for ${DocumentIdentifierRedactor.redact(uri)}")
        return ContentFingerprint(
            identity = identity,
            tier = FingerprintTier.FULL,
            algorithm = FingerprintAlgorithm.SHA256,
            hash = ContentHash.of(LibraryRecordIds.sha256Hex(digest.digest())),
            computedAt = clock(),
        )
    }

    private fun readWindow(uri: Uri, offset: Long, length: Int): ByteArray {
        require(offset >= 0L) { "Window offset must not be negative" }
        return contentResolver.openInputStream(uri)?.use { input ->
            skipFully(input, offset)
            val result = ByteArray(length)
            var total = 0
            while (total < result.size) {
                val count = input.read(result, total, result.size - total)
                if (count < 0) break
                if (count == 0) continue
                total += count
            }
            if (total == result.size) result else result.copyOf(total)
        } ?: error("Content provider returned no stream for ${DocumentIdentifierRedactor.redact(uri)}")
    }

    private fun skipFully(input: InputStream, requested: Long) {
        var remaining = requested
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            if (input.read() < 0) return
            remaining -= 1L
        }
    }

    private companion object {
        const val DEFAULT_QUICK_WINDOW_BYTES = 64 * 1024
        const val FULL_HASH_BUFFER_BYTES = 128 * 1024
    }
}

/**
 * Room-backed cache lookup for a root. The existing database API has no direct
 * identity lookup, so this adapter reuses the indexed root installations and
 * their fingerprint IDs without changing core:database.
 */
class DatabaseFingerprintCache(
    private val database: ThorHubDatabase,
    private val rootId: String,
) : FingerprintCache {
    override suspend fun find(identity: CheapContentIdentity): ContentFingerprint? {
        val installation = database.installationDao()
            .observeForRoot(rootId)
            .first()
            .firstOrNull { it.sourceLocator == identity.sourceKey }
            ?: return null
        val fingerprintId = installation.contentFingerprintId ?: return null
        val stored = database.fingerprintDao().observeById(fingerprintId).first() ?: return null
        if (stored.identityKey != identity.sourceKey || stored.sizeBytes != identity.sizeBytes) return null
        if (stored.modifiedAt != identity.modifiedAt?.toEpochMilli()) return null
        val algorithm = runCatching { FingerprintAlgorithm.valueOf(stored.algorithm) }
            .getOrDefault(FingerprintAlgorithm.UNKNOWN)
        val tier = when (algorithm) {
            FingerprintAlgorithm.QUICK_WINDOW,
            FingerprintAlgorithm.UNKNOWN,
            -> FingerprintTier.QUICK

            FingerprintAlgorithm.CRC32,
            FingerprintAlgorithm.SHA1,
            FingerprintAlgorithm.SHA256,
            -> FingerprintTier.FULL
        }
        return ContentFingerprint(
            identity = identity,
            tier = tier,
            algorithm = algorithm,
            hash = ContentHash.of(stored.value),
            computedAt = stored.createdAt.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(fingerprint: ContentFingerprint) {
        database.fingerprintDao().upsert(
            ContentFingerprintEntity(
                id = LibraryRecordIds.fingerprint(
                    identity = fingerprint.identity,
                    algorithm = fingerprint.algorithm.name,
                    value = fingerprint.hash.value,
                ),
                algorithm = fingerprint.algorithm.name,
                value = fingerprint.hash.value,
                identityKey = fingerprint.identity.sourceKey,
                sizeBytes = fingerprint.identity.sizeBytes,
                modifiedAt = fingerprint.identity.modifiedAt?.toEpochMilli(),
                createdAt = fingerprint.computedAt.toEpochMilli(),
            ),
        )
    }
}
