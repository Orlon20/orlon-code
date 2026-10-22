package com.mobilecode.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Hands an APK from a project's .orlon/out folder to Android's installer (asking for the "install apps" permission first). */
object ApkInstall {
    fun install(a: Activity, f: File) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow Orlon Code to install apps, then tap Install again.", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))); return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", f)
        a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
