package com.mobilecode.app

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Shows the sandbox's projects folder in Android's file picker and the Files app, so projects can be opened in other apps,
 * copied out, or replaced. It exposes only /root/projects: never the signing key, settings, or the rest of the sandbox.
 */
class SandboxDocumentsProvider : DocumentsProvider() {
    private val rootDir: File get() = File(Sandbox.paths(context!!).rootfs + Sandbox.WORKSPACE).apply { mkdirs() }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: ROOT_COLS).apply {
        newRow().add(Root.COLUMN_ROOT_ID, "projects").add(Root.COLUMN_TITLE, "Orlon Code projects")
            .add(Root.COLUMN_DOCUMENT_ID, ID_ROOT).add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY)
    }

    private fun file(id: String): File {
        val f = if (id == ID_ROOT) rootDir else File(rootDir, id.removePrefix("$ID_ROOT/"))
        if (f != rootDir && !f.canonicalPath.startsWith(rootDir.canonicalPath + File.separator)) throw FileNotFoundException(id)
        if (!f.exists()) throw FileNotFoundException(id)
        return f
    }

    private fun idOf(f: File) = ID_ROOT + "/" + f.relativeTo(rootDir).path

    private fun MatrixCursor.addFile(f: File) {
        val dir = f.isDirectory
        var flags = if (dir) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        if (f != rootDir) flags = flags or Document.FLAG_SUPPORTS_DELETE
        newRow().add(Document.COLUMN_DOCUMENT_ID, if (f == rootDir) ID_ROOT else idOf(f))
            .add(Document.COLUMN_DISPLAY_NAME, if (f == rootDir) "projects" else f.name)
            .add(Document.COLUMN_SIZE, if (dir) null else f.length()).add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, flags).add(Document.COLUMN_MIME_TYPE, if (dir) Document.MIME_TYPE_DIR else mime(f))
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOC_COLS).apply { addFile(file(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DOC_COLS).apply { file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { addFile(it) } }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        runCatching { file(documentId).canonicalPath.startsWith(file(parentDocumentId).canonicalPath) }.getOrDefault(false)

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = file(parentDocumentId)
        val name = File(displayName).name.ifEmpty { throw FileNotFoundException(displayName) }
        var f = File(parent, name); var i = 1
        while (f.exists()) f = File(parent, "${name.substringBeforeLast('.', name)} ($i)${if ('.' in name) "." + name.substringAfterLast('.') else ""}".also { i++ })
        if (mimeType == Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
        return idOf(f)
    }

    override fun deleteDocument(documentId: String) { if (documentId != ID_ROOT) file(documentId).deleteRecursively() }

    private fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    companion object {
        const val ID_ROOT = "root"
        private val ROOT_COLS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_FLAGS)
        private val DOC_COLS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_MIME_TYPE)
    }
}
