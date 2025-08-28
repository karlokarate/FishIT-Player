package com.chris.m3usuite.drive

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File

object DriveDefaults { const val DEFAULT_FOLDER_ID = "18UrvlyDSHdmmf3jBm5A0jUGzzDo38-4E" }

object DriveClient {
    private fun signInClient(ctx: Context): GoogleSignInClient =
        GoogleSignIn.getClient(ctx, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE)).build())

    fun isSignedIn(ctx: Context): Boolean = GoogleSignIn.getLastSignedInAccount(ctx) != null

    fun signIn(activity: Activity, onResult: (Boolean) -> Unit) {
        val client = signInClient(activity)
        val acct = GoogleSignIn.getLastSignedInAccount(activity)
        if (acct != null) { onResult(true); return }
        client.silentSignIn().addOnCompleteListener {
            if (it.isSuccessful) onResult(true) else { activity.startActivityForResult(client.signInIntent, 9910); onResult(true) }
        }
    }

    private fun service(ctx: Context): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(ctx) ?: error("Not signed in")
        val credential = GoogleAccountCredential.usingOAuth2(ctx, setOf(DriveScopes.DRIVE_FILE)).apply { selectedAccount = account.account }
        return Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential).setApplicationName("m3uSuite").build()
    }

    fun uploadBytes(ctx: Context, folderId: String, name: String, mime: String, bytes: ByteArray): String {
        val drive = service(ctx)
        val md = File().apply { parents = listOf(folderId); this.name = name; mimeType = mime }
        val content = ByteArrayContent(mime, bytes)
        val res = drive.Files().create(md, content).setFields("id,webViewLink").execute()
        return res.id
    }

    fun downloadLatestByPrefix(ctx: Context, folderId: String, namePrefix: String): ByteArray? {
        val drive = service(ctx)
        val q = "'$folderId' in parents and name contains '$namePrefix'"
        val list = drive.Files().list().setQ(q).setOrderBy("createdTime desc").setPageSize(1).execute()
        val item = list.files?.firstOrNull() ?: return null
        val out = java.io.ByteArrayOutputStream()
        drive.Files().get(item.id).executeMediaAndDownloadTo(out)
        return out.toByteArray()
    }
}

