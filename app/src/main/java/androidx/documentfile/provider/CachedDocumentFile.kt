package androidx.documentfile.provider

import android.net.Uri

// Must stay inside androidx.documentfile.provider to be able to call DocumentFile constructor which is package-private
class CachedDocumentFile(
    val wrapped: DocumentFile,
    var mName: String?,
    var mLength: Long? = null,
    var mIsDirectory: Boolean? = null,
    var mLastModified: Long? = null
) : DocumentFile(wrapped.parentFile) {

    var mCanRead: Boolean? = null
    var mCanWrite: Boolean? = null
    var mExists: Boolean? = null
    var mIsVirtual: Boolean? = null
    var mType: String? = null

    fun invalidate() {
        mCanRead = null
        mCanWrite = null
        mExists = null
        mIsVirtual = null
        mName = null
        mType = null
        mIsDirectory = null
        mLastModified = null
        mLength = null
    }

    override fun canRead(): Boolean {
        if (mCanRead == null) {
            mCanRead = wrapped.canRead()
        }
        return mCanRead!!
    }

    override fun canWrite(): Boolean {
        if (mCanWrite == null) {
            mCanWrite = wrapped.canWrite()
        }
        return mCanWrite!!
    }

    override fun createDirectory(arg0: String): DocumentFile? {
        return wrapped.createDirectory(arg0)
    }

    override fun createFile(arg0: String, arg1: String): DocumentFile? {
        return wrapped.createFile(arg0, arg1)
    }

    override fun delete(): Boolean {
        if (wrapped.delete()) {
            invalidate()
            return true
        }
        return false
    }

    override fun exists(): Boolean {
        if (mExists == null) {
            mExists = wrapped.exists()
        }
        return mExists!!
    }

    override fun listFiles(): Array<DocumentFile> {
        return wrapped.listFiles()
    }

    override fun getName(): String? {
        if (mName == null) {
            mName = wrapped.name
        }
        return mName
    }

    override fun getType(): String? {
        if (mType == null) {
            mType = wrapped.type
        }
        return mType
    }

    override fun getUri(): Uri {
        return wrapped.uri
    }

    override fun isDirectory(): Boolean {
        if (mIsDirectory == null) {
            mIsDirectory = wrapped.isDirectory
        }
        return mIsDirectory!!
    }

    override fun isFile(): Boolean {
        return !isDirectory()
    }

    override fun isVirtual(): Boolean {
        if (mIsVirtual == null) {
            mIsVirtual = wrapped.isVirtual
        }
        return mIsVirtual!!
    }

    override fun lastModified(): Long {
        if (mLastModified == null) {
            mLastModified = wrapped.lastModified()
        }
        return mLastModified!!
    }

    override fun length(): Long {
        if (mLength == null) {
            mLength = wrapped.length()
        }
        return mLength!!
    }

    override fun renameTo(displayName: String): Boolean {
        if (wrapped.renameTo(displayName)) {
            invalidate() // We can't value mName because renaming to an existing name in the same folder can result in unspecified behaviour (e.g. "newName (1).ext")
            return true
        }
        return false
    }
}