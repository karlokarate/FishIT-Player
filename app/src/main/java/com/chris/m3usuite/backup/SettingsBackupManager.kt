package com.chris.m3usuite.backup

import android.content.Context
import com.chris.m3usuite.backup.BackupFormat.Manifest
import com.chris.m3usuite.backup.BackupFormat.Payload
import com.chris.m3usuite.backup.BackupFormat.ProfileExport
import com.chris.m3usuite.backup.BackupFormat.ResumeEpisodeMark
import com.chris.m3usuite.backup.BackupFormat.ResumeVodMark
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxProfile
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

    data class Report(val settingsKeys: Int, val profiles: Int, val resumeVod: Int, val resumeEpisodes: Int)
    enum class ImportMode { Merge, Replace }

    private fun nowIsoUtc(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC))

    suspend fun exportAll(progress: suspend (Int, String) -> Unit, passphrase: CharArray?): Pair<String, ByteArray> =
        withContext(Dispatchers.IO) {
            progress(2, "Lese Einstellungen…")
            val settings = SettingsSnapshot.dump(context)

            progress(15, "Lese Profile…")
            val box = ObxStore.get(context).boxFor(ObxProfile::class.java)
            val profiles = box.all
            val assets = LinkedHashMap<String, ByteArray>()
            val profExports = profiles.map { p ->
                val entry = p.avatarPath?.let { ap ->
                    val f = File(ap)
                    if (f.exists()) {
                        val ext = f.extension.ifBlank { "png" }
                        val pathInZip = "profiles/${p.id}/avatar.$ext"; assets[pathInZip] = f.readBytes(); pathInZip
                    } else null
                }
                ProfileExport(id = p.id, name = p.name, type = p.type, avatarFile = entry)
            }

            progress(45, "Lese Weiterschauen…")
            val resumeRepo = com.chris.m3usuite.data.repo.ResumeRepository(context)
            val recentVod = resumeRepo.recentVod(10_000).map { ResumeVodMark(it.mediaId, it.positionSecs, it.updatedAt) }
            val recentEp = resumeRepo.recentEpisodes(10_000).map {
                val key = it.seriesId * 1_000_000 + it.season * 1_000 + it.episodeNum
                ResumeEpisodeMark(key, it.positionSecs, it.updatedAt)
            }

            val manifest = Manifest(schema = 1, exportedAtUtc = nowIsoUtc(), appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Throwable) { null }, encrypted = passphrase != null)
            val payload = Payload(settings = settings, profiles = profExports, resumeVod = recentVod, resumeEpisodes = recentEp)

            progress(75, "Packe Backup…")
            val bytes = BackupFormat.pack(manifest, payload, assets, passphrase)
            val filename = "m3usuite-settings-v1-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(java.time.LocalDateTime.now()) + ".msbx"
            progress(100, "Fertig")
            filename to bytes
        }

    suspend fun importAll(msbx: ByteArray, passphrase: CharArray?, mode: ImportMode, progress: suspend (Int, String) -> Unit): Report =
        withContext(Dispatchers.IO) {
            progress(3, "Entpacke Backup…")
            val (_, payload, assets) = BackupFormat.unpack(msbx, passphrase)

            progress(12, "Wende Einstellungen an…")
            SettingsSnapshot.restore(context, payload.settings, replace = (mode == ImportMode.Replace))

            progress(35, "Wende Profile an…")
            val obx = ObxStore.get(context)
            val pbox = obx.boxFor(ObxProfile::class.java)
            if (mode == ImportMode.Replace) pbox.removeAll()
            for (p in payload.profiles) {
                val existing = pbox.get(p.id)
                val now = System.currentTimeMillis()
                val avatarPath = p.avatarFile?.let { path ->
                    val data = assets[path] ?: return@let null
                    val outDir = File(context.filesDir, "profiles/${p.id}").apply { mkdirs() }
                    val ext = path.substringAfterLast('.', "png")
                    val out = File(outDir, "avatar.$ext"); out.writeBytes(data); out.absolutePath
                }
                val row = existing ?: ObxProfile(id = 0, name = p.name, type = p.type, avatarPath = avatarPath, createdAt = now, updatedAt = now)
                if (existing != null) {
                    row.name = p.name; row.type = p.type; row.avatarPath = avatarPath ?: existing.avatarPath; row.updatedAt = now
                }
                pbox.put(row)
            }

            progress(70, "Setze Weiterschauen…")
            val resumeRepo2 = com.chris.m3usuite.data.repo.ResumeRepository(context)
            payload.resumeVod.forEach { m -> resumeRepo2.setVodResume(m.mediaId, m.positionSecs) }
            payload.resumeEpisodes.forEach { m ->
                val seriesId = (m.episodeId / 1_000_000)
                val season = (m.episodeId % 1_000_000) / 1_000
                val epnum = m.episodeId % 1_000
                resumeRepo2.setSeriesResume(seriesId, season, epnum, m.positionSecs)
            }

            runCatching {
                val store = SettingsStore(context)
                val currentId = store.currentProfileId.first()
                val all = pbox.all
                if (all.none { it.id == currentId }) {
                    val fallback = all.firstOrNull { it.type == "adult" }?.id ?: all.firstOrNull()?.id ?: -1L
                    if (fallback > 0) store.setCurrentProfileId(fallback)
                }
            }

            progress(100, "Abgeschlossen")
            Report(payload.settings.size, payload.profiles.size, payload.resumeVod.size, payload.resumeEpisodes.size)
        }
}
