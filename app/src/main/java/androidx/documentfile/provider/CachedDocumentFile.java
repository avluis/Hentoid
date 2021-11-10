package androidx.documentfile.provider;

import android.net.Uri;

import androidx.annotation.NonNull;

public class CachedDocumentFile extends DocumentFile {

    final DocumentFile mWrapped;

    Boolean mCanRead;
    Boolean mCanWrite;
    Boolean mExists;
    String mName;
    String mType;
    Boolean mIsDirectory;
    Long mLastModified;
    Long mLength;

    public void invalidate() {
        mCanRead = null;
        mCanWrite = null;
        mExists = null;
        mName = null;
        mType = null;
        mIsDirectory = null;
        mLastModified = null;
        mLength = null;
    }

    public CachedDocumentFile(DocumentFile wrapped, String name, long length, boolean isDirectory) {
        super(wrapped.getParentFile());
        mWrapped = wrapped;
        mName = name;
        mLength = length;
        mIsDirectory = isDirectory;
    }

    public boolean canRead() {
        if (mCanRead == null) {
            mCanRead = mWrapped.canRead();
        }
        return mCanRead;
    }

    public boolean canWrite() {
        if (mCanWrite == null) {
            mCanWrite = mWrapped.canWrite();
        }
        return mCanWrite;

    }

    public DocumentFile createDirectory(@NonNull String arg0) {
        return mWrapped.createDirectory(arg0);
    }

    public DocumentFile createFile(@NonNull String arg0, @NonNull String arg1) {
        return mWrapped.createFile(arg0, arg1);
    }

    public boolean delete() {
        if (mWrapped.delete()) {
            invalidate();
            return true;
        }
        return false;
    }

    public boolean exists() {
        if (mExists == null) {
            mExists = mWrapped.exists();
        }
        return mExists;
    }

    @NonNull
    @Override
    public DocumentFile[] listFiles() {
        return mWrapped.listFiles();
    }

    public String getName() {
        if (mName == null) {
            mName = mWrapped.getName();
        }
        return mName;
    }

    public String getType() {
        if (mType == null) {
            mType = mWrapped.getType();
        }
        return mType;
    }

    @NonNull
    public Uri getUri() {
        return mWrapped.getUri();
    }

    public boolean isDirectory() {
        if (mIsDirectory == null) {
            mIsDirectory = mWrapped.isDirectory();
        }
        return mIsDirectory;
    }

    public boolean isFile() {
        return !isDirectory();
    }

    public boolean isVirtual() {
        return mWrapped.isVirtual();
    }

    public long lastModified() {
        if (mLastModified == null) {
            mLastModified = mWrapped.lastModified();
        }
        return mLastModified;
    }

    public long length() {
        if (mLength == null) {
            mLength = mWrapped.length();
        }
        return mLength;
    }

    public boolean renameTo(@NonNull String arg0) {
        if (mWrapped.renameTo(arg0)) {
            invalidate(); // We can't value mName because renaming to an existing name in the same folder can result in unspecified behaviour (e.g. "newName (1).ext")
            return true;
        }
        return false;
    }

}