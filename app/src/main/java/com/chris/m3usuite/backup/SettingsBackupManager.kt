package com.chris.m3usuite.backup

import android.content.Context
import com.chris.m3usuite.backup.BackupFormat.Manifest
import com.chris.m3usuite.backup.BackupFormat.Payload
import com.chris.m3usuite.backup.BackupFormat.ProfileExport
import com.chris.m3usuite.backup.BackupFormat.ResumeEpisodeMark
import com.chris.m3usuite.backup.BackupFormat.ResumeVodMark
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.prefs.SettingsSnapshot
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SettingsBackupManager(private val context: Context) {

    data class Report(
        val settingsKeys: Int,
        val profiles: Int,
        val resumeVod: Int,
        val resumeEpisodes: Int
    )

    enum class ImportMode { Merge, Replace }

    private fun nowIsoUtc(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC))

    suspend fun exportAll(progress: suspend (Int, String) -> Unit, passphrase: CharArray?): Pair<String, ByteArray> =
        withContext(Dispatchers.IO) {
            progress(2, "Lese Einstellungen…")
            val settings = SettingsSnapshot.dump(context)

            progress(15, "Lese Profile…")
            val db = DbProvider.get(context)
            val pdao = db.profileDao()
            val profiles = pdao.all()

            val assets = LinkedHashMap<String, ByteArray>()
            val profExports = profiles.map { p ->
                val avatarPath = p.avatarPath
                val entry = if (!avatarPath.isNullOrBlank()) {
                    val f = File(avatarPath)
                    if (f.exists()) {
                        val ext = f.extension.ifBlank { "png" }
                        val pathInZip = "profiles/${p.id}/avatar.$ext"
                        assets[pathInZip] = f.readBytes()
                        pathInZip
                    } else null
                } else null
                ProfileExport(
                    id = p.id,
                    name = p.name,
                    type = p.type,
                    avatarFile = entry
                )
            }

            progress(45, "Lese Weiterschauen…")
            val rdao = db.resumeDao()
            val recentVod = rdao.recentVod(10_000).map {
                ResumeVodMark(mediaId = it.mediaId, positionSecs = it.positionSecs, updatedAt = it.updatedAt)
            }
            val recentEp = rdao.recentEpisodes(10_000).map {
                ResumeEpisodeMark(episodeId = it.episodeId, positionSecs = it.positionSecs, updatedAt = it.updatedAt)
            }

            val manifest = Manifest(
                schema = 1,
                exportedAtUtc = nowIsoUtc(),
                appVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { null },
                encrypted = passphrase != null
            )
            val payload = Payload(settings = settings, profiles = profExports, resumeVod = recentVod, resumeEpisodes = recentEp)

            progress(75, "Packe Backup…")
            val bytes = BackupFormat.pack(manifest, payload, assets, passphrase)
            val filename = "m3usuite-settings-v1-" +
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(java.time.LocalDateTime.now()) +
                    ".msbx"

            progress(100, "Fertig")
            filename to bytes
        }

    suspend fun importAll(msbx: ByteArray, passphrase: CharArray?, mode: ImportMode, progress: suspend (Int, String) -> Unit): Report =
        withContext(Dispatchers.IO) {
            progress(3, "Entpacke Backup…")
            val (manifest, payload, assets) = BackupFormat.unpack(msbx, passphrase)

            progress(12, "Wende Einstellungen an…")
            SettingsSnapshot.restore(context, payload.settings, replace = (mode == ImportMode.Replace))

            progress(35, "Wende Profile an…")
            val db = DbProvider.get(context)
            val pdao = db.profileDao()
            if (mode == ImportMode.Replace) {
                val existing = pdao.all()
                for (p in existing) pdao.delete(p)
            }
            // Insert/update profiles, restore avatars
            for (p in payload.profiles) {
                val existing = pdao.byId(p.id)
                val now = System.currentTimeMillis()
                val avatarPath = p.avatarFile?.let { path ->
                    val data = assets[path] ?: return@let null
                    val dstDir = File(context.filesDir, "profiles/${p.id}")
                    dstDir.mkdirs()
                    val ext = path.substringAfterLast('.', "png")
                    val out = File(dstDir, "avatar.$ext")
                    out.writeBytes(data)
                    out.absolutePath
                }
                if (existing == null) {
                    pdao.insert(Profile(id = 0, name = p.name, type = p.type, avatarPath = avatarPath, createdAt = now, updatedAt = now))
                } else {
                    pdao.update(existing.copy(name = p.name, type = p.type, avatarPath = avatarPath ?: existing.avatarPath, updatedAt = now))
                }
            }

            progress(70, "Setze Weiterschauen…")
            val rdao = db.resumeDao()
            // There is no "replace" semantics for resume; we simply upsert marks
            for (m in payload.resumeVod) {
                rdao.upsert(ResumeMark(id = 0, type = "vod", mediaId = m.mediaId, episodeId = null, positionSecs = m.positionSecs, updatedAt = m.updatedAt))
            }
            for (m in payload.resumeEpisodes) {
                rdao.upsert(ResumeMark(id = 0, type = "series", mediaId = null, episodeId = m.episodeId, positionSecs = m.positionSecs, updatedAt = m.updatedAt))
            }

            // Ensure current profile id points to an existing one
            runCatching {
                val store = SettingsStore(context)
                val currentId = store.currentProfileId.first()
                val all = pdao.all()
                if (all.none { it.id == currentId }) {
                    val fallback = all.firstOrNull { it.type == "adult" }?.id ?: all.firstOrNull()?.id ?: -1L
                    if (fallback > 0) store.setCurrentProfileId(fallback)
                }
            }

            progress(100, "Abgeschlossen")
            Report(
                settingsKeys = payload.settings.size,
                profiles = payload.profiles.size,
                resumeVod = payload.resumeVod.size,
                resumeEpisodes = payload.resumeEpisodes.size
            )
        }
}
