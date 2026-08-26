package com.athea.app.data

import com.athea.app.core.journal.SessionJournal
import com.athea.app.core.model.DisplayMode
import com.athea.app.core.model.FavoriteCommand
import com.athea.app.util.writeTextAtomic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Persistable per-session metadata (the transcript itself lives in the journal). */
@Serializable
data class SessionMeta(
    val id: Long,
    val name: String,
    val pinned: Boolean = false,
    val displayMode: DisplayMode = DisplayMode.BLOCKS,
    val draft: String = "",
)

@Serializable
internal data class SessionsIndex(
    val items: List<SessionMeta> = emptyList(),
    val nextSessionId: Long = 1,
)

@Serializable
internal data class FavoritesIndex(
    val items: List<FavoriteCommand> = emptyList(),
    val nextFavoriteId: Long = 1,
)

/** User-facing application preferences. */
@Serializable
data class AtheaSettings(
    val keyRowVisible: Boolean = true,
    val enterSends: Boolean = true,
    val autoScrollOnSend: Boolean = true,
    val rawStream: Boolean = false,
    val outputFontSizeSp: Int = 13,
    val previewLines: Int = com.athea.app.core.model.PREVIEW_LINES,
    val bubbleFontSizeSp: Int = 16,
    val autocompleteEnabled: Boolean = true,
    val pinchZoomEnabled: Boolean = true,
)

/**
 * Owns every byte Athea keeps on disk: session index, favorites, journals
 * and the shell integration rc. Pure file operations; orchestration lives
 * in the ViewModel, so this class stays trivially testable.
 */
class AtheaStorage(root: File) {

    private val json = Json { ignoreUnknownKeys = true }

    private val sessionsDir = File(root, "sessions")
    private val shellDir = File(root, "shell")
    private val homeDir = File(root, "home")

    private val indexFile get() = File(sessionsDir, "index.json")
    private val favoritesFile get() = File(sessionsDir, "favorites.json")

    init {
        sessionsDir.mkdirs()
        homeDir.mkdirs()
    }

    // ----------------------------------------------------------------- index

    internal fun loadIndex(): SessionsIndex =
        runCatching {
            json.decodeFromString(SessionsIndex.serializer(), indexFile.readText())
        }.getOrDefault(SessionsIndex())

    internal fun saveIndex(index: SessionsIndex) {
        sessionsDir.mkdirs()
        indexFile.writeTextAtomic(json.encodeToString(SessionsIndex.serializer(), index))
    }

    // -------------------------------------------------------------- journals

    fun journalFor(id: Long): SessionJournal =
        SessionJournal(File(sessionsDir, id.toString()).resolve("journal.log"))

    fun deleteSession(id: Long) {
        File(sessionsDir, id.toString()).deleteRecursively()
    }

    // ------------------------------------------------------------- favorites

    internal fun loadFavorites(): FavoritesIndex =
        runCatching {
            json.decodeFromString(FavoritesIndex.serializer(), favoritesFile.readText())
        }.getOrDefault(FavoritesIndex())

    internal fun saveFavorites(favorites: FavoritesIndex) {
        sessionsDir.mkdirs()
        favoritesFile.writeTextAtomic(json.encodeToString(FavoritesIndex.serializer(), favorites))
    }

    // -------------------------------------------------------------- settings

    fun loadSettings(): AtheaSettings =
        runCatching {
            json.decodeFromString(
                AtheaSettings.serializer(),
                File(sessionsDir, "settings.json").readText(),
            )
        }.getOrDefault(AtheaSettings())

    fun saveSettings(settings: AtheaSettings) {
        sessionsDir.mkdirs()
        File(sessionsDir, "settings.json")
            .writeTextAtomic(json.encodeToString(AtheaSettings.serializer(), settings))
    }

    // ------------------------------------------------------------- key row

    fun loadKeys(): KeysIndex =
        runCatching {
            json.decodeFromString(KeysIndex.serializer(), File(sessionsDir, "keys.json").readText())
        }.getOrDefault(KeysIndex())

    fun saveKeys(keys: KeysIndex) {
        sessionsDir.mkdirs()
        File(sessionsDir, "keys.json")
            .writeTextAtomic(json.encodeToString(KeysIndex.serializer(), keys))
    }

    // ----------------------------------------------------------------- files

    /**
     * Materializes the shell rc from packaged assets and returns its path.
     * Rewritten on every call so updates ship with app updates.
     */
    fun ensureShellRc(assetBytes: ByteArray): File {
        shellDir.mkdirs()
        return File(shellDir, "mkshrc").apply { writeBytes(assetBytes) }
    }

    fun shellHome(): File = homeDir
}
