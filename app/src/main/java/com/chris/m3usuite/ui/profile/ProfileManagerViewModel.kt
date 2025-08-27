package com.chris.m3usuite.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.db.DbProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileManagerViewModel(app: Application) : AndroidViewModel(app) {
    fun saveAvatar(kidId: Long, source: Uri, onResult: (Boolean, File?) -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(ctx.filesDir, "avatars/$kidId").apply { mkdirs() }
                val out = File(dir, "avatar.jpg")
                ctx.contentResolver.openInputStream(source).use { input ->
                    FileOutputStream(out).use { output ->
                        if (input == null) throw IllegalStateException("no input stream")
                        input.copyTo(output)
                    }
                }
                val db = DbProvider.get(ctx)
                val p = db.profileDao().byId(kidId)
                if (p != null) {
                    db.profileDao().update(p.copy(avatarPath = out.absolutePath, updatedAt = System.currentTimeMillis()))
                }
                withContext(Dispatchers.Main) { onResult(true, out) }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) { onResult(false, null) }
            }
        }
    }
}

