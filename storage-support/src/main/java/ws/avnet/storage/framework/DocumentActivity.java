package ws.avnet.storage.framework;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.annotation.IdRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public abstract class DocumentActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = DocumentActivity.class.getSimpleName();

    private static final int REQUEST_PREFIX = 256;  // Permissions have 8-bit limit
    protected static final int REQUEST_CODE_WRITE_PERMISSION = REQUEST_PREFIX - 1;
    private static final int REQUEST_STORAGE_PERMISSION = REQUEST_PREFIX - 2;
    /**
     * The name of the primary volume (LOLLIPOP).
     */
    private static final String PRIMARY_VOLUME_NAME = "primary";
    /**
     * Permissions required to read and write to storage.
     */
    private static String[] PERMISSIONS_STORAGE = {
            permission.READ_EXTERNAL_STORAGE,
            permission.WRITE_EXTERNAL_STORAGE};
    protected Enum mCallingMethod;
    protected Object[] mCallingParameters;
    private boolean mRequestStoragePermissionEnabled = false;
    private int mStorageRationale = R.string.permissionStorageRationale;
    /**
     * All roots for which this app has permission
     */
    private List<UriPermission> mRootPermissions = new ArrayList<>();

    /**
     * The permissions necessary to read and write to storage
     *
     * @return the permissions necessary to read and write to storage
     */
    public static String[] getStoragePermissions() {
        return PERMISSIONS_STORAGE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissions();

        boolean needsRead = ActivityCompat.checkSelfPermission(this,
                permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;

        boolean needsWrite = ActivityCompat.checkSelfPermission(this,
                permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;

        if (mRequestStoragePermissionEnabled && (needsRead || needsWrite))
            requestStoragePermission();
    }

    private void updatePermissions() {
        mRootPermissions = getContentResolver().getPersistedUriPermissions();
    }

    /**
     * Sets whether DocumentActivity will manage storage permission for your activity.
     * You may also set {@link #setStoragePermissionRationale(int)} to customize the
     * rationale for the permission if a user initially declines
     *
     * @param enabled to allow DocumentActivity to manage storage permission
     */
    protected void setStoragePermissionRequestEnabled(boolean enabled) {
        mRequestStoragePermissionEnabled = enabled;
    }

    /**
     * Requests the ability to read or write to external storage.
     * <p/>
     * Can be activated with {@link #setStoragePermissionRequestEnabled(boolean)}
     * <p/>
     * If a user has denied the permission you can supply a rationale that will be
     * displayed in a Snackbar by setting {@link #setStoragePermissionRequestEnabled(boolean)} ()}
     */
    private void requestStoragePermission() {
        Log.i(TAG, "STORAGE permission has NOT been granted. Requesting permission.");

        boolean justifyWrite = ActivityCompat.shouldShowRequestPermissionRationale(this,
                permission.WRITE_EXTERNAL_STORAGE);
        boolean justifyRead = ActivityCompat.shouldShowRequestPermissionRationale(this,
                permission.READ_EXTERNAL_STORAGE);

        // Storage permission has been requested and denied, show a Snackbar with the option to
        // grant permission
        if (justifyRead || justifyWrite) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            View mainView = findViewById(android.R.id.content);
            if (mainView == null)
                return; // Should just Toast
            Snackbar.make(mainView, mStorageRationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(DocumentActivity.this,
                                    PERMISSIONS_STORAGE, REQUEST_STORAGE_PERMISSION);
                        }
                    })
                    .show();
        } else {
            // Storage permission has not been request yet. Request for first time.
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * Customizes the rationale for the storage permission, which appears as a snackbar if the
     * user initially denies the permission.
     */
    protected void setStoragePermissionRationale(@IdRes int stringId) {
        mStorageRationale = stringId;
    }

    /**
     * Copy a file within the constraints of SAF.
     *
     * @param source The source uri
     * @param target The target uri
     * @return true if the copying was successful.
     * @throws WritePermissionException if the app does not have permission to write to target
     * @throws IOException              if an I/O error occurs
     */
    public boolean copyFile(final Uri source, final Uri target)
            throws IOException {
        InputStream inStream = null;
        OutputStream outStream = null;

        UsefulDocumentFile destinationDoc = getDocumentFile(target, false, true);
        UsefulDocumentFile.FileData destinationData = destinationDoc.getData();
        if (!destinationData.exists) {
            destinationDoc.getParentFile().createFile(null, destinationData.name);
            // Note: destinationData is invalidated upon creation of the new file,
            // so make a direct call following
        }
        if (!destinationDoc.exists()) {
            throw new WritePermissionException(
                    "Write permission not found. This indicates a SAF write permission was requested. " +
                            "The app should store any parameters necessary to resume write here.");
        }

        try {
            inStream = FileUtil.getInputStream(this, source);
            outStream = getContentResolver().openOutputStream(target);

            Util.copy(inStream, outStream);
        } catch (ArithmeticException e) {
            Log.d(TAG, "File larger than 2GB copied.");
        } catch (Exception e) {
            throw new IOException("Failed to copy " + source.getPath() + ": " + e.toString());
        } finally {
            Util.closeSilently(inStream);
            Util.closeSilently(outStream);
        }
        return true;
    }

    /**
     * Copy a file within the constraints of SAF.
     *
     * @param source The source file
     * @param target The target file
     * @return true if the copying was successful.
     */
    public boolean copyFile(final File source, final File target)
            throws WritePermissionException {
        FileInputStream inStream = null;
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);

            // First try the normal way
            if (FileUtil.isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } else {
                if (Util.hasLollipop()) {
                    // Storage Access Framework
                    UsefulDocumentFile targetDocument = getDocumentFile(target, false, true);
                    if (targetDocument == null)
                        return false;
                    outStream = getContentResolver().openOutputStream(targetDocument.getUri());
                } else {
                    return false;
                }

                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[4096]; // MAGIC_NUMBER
                    int bytesRead;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                }

            }
        } catch (Exception e) {
            Log.e(TAG, "Error when copying file to " + target.getAbsolutePath(), e);
            return false;
        } finally {
            Util.closeSilently(inStream);
            Util.closeSilently(outStream);
            Util.closeSilently(inChannel);
            Util.closeSilently(outChannel);
        }
        return true;
    }

    /**
     * Delete a file within the constraints of SAF.
     *
     * @param file the uri to be deleted.
     * @return True if successfully deleted.
     */
    public boolean deleteFile(final Uri file)
            throws WritePermissionException {
        if (FileUtil.isFileScheme(file)) {
            return deleteFile(new File(file.getPath()));
        } else {
            UsefulDocumentFile document = getDocumentFile(file, false, true);
            return document != null && document.delete();
        }
    }

    /**
     * Delete a file within the constraints of SAF.
     *
     * @param file the file to be deleted.
     * @return True if successfully deleted.
     */
    private boolean deleteFile(final File file)
            throws WritePermissionException {
        if (!file.exists())
            return false;

        // First try the normal deletion.
        if (file.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Util.hasLollipop()) {
            UsefulDocumentFile document = getDocumentFile(file, false, true);
            return document != null && document.delete();
        }

        return !file.exists();
    }

    /**
     * Move a file within the constraints of SAF.
     *
     * @param source The source uri
     * @param target The target uri
     * @return true if the copying was successful.
     * @throws WritePermissionException if the app does not have permission to write to target
     * @throws IOException              if an I/O error occurs
     */
    public boolean moveFile(final Uri source, final Uri target) throws IOException {
        if (FileUtil.isFileScheme(target) && FileUtil.isFileScheme(target)) {
            File from = new File(source.getPath());
            File to = new File(target.getPath());
            return moveFile(from, to);
        } else {
            boolean success = copyFile(source, target);
            if (success) {
                success = deleteFile(source);
            }
            return success;
        }
    }

    /**
     * Move a file within the constraints of SAF.
     *
     * @param source The source file
     * @param target The target file
     * @return true if the copying was successful.
     */
    public boolean moveFile(final File source, final File target) throws WritePermissionException {
        // First try the normal rename.
        if (source.renameTo(target)) {
            return true;
        }

        boolean success = copyFile(source, target);
        if (success) {
            success = deleteFile(source);
        }
        return success;
    }

    /**
     * Rename a folder within the constraints of SAF.
     *
     * @param source The source folder.
     * @param target The target folder.
     * @return true if the renaming was successful.
     */
    public boolean renameFolder(final File source,
                                final File target)
            throws WritePermissionException {
        // First try the normal rename.
        if (source.renameTo(target)) {
            return true;
        }
        if (target.exists()) {
            return false;
        }

        // Try the Storage Access Framework if it is just a rename within the same parent folder.
        if (Util.hasLollipop() && source.getParent().equals(target.getParent())) {
            UsefulDocumentFile document = getDocumentFile(source, true, true);
            if (document == null)
                return false;
            if (document.renameTo(target.getName())) {
                return true;
            }
        }

        // Try the manual way, moving files individually.
        if (!mkDir(target)) {
            return false;
        }

        File[] sourceFiles = source.listFiles();

        if (sourceFiles == null) {
            return true;
        }

        for (File sourceFile : sourceFiles) {
            String fileName = sourceFile.getName();
            File targetFile = new File(target, fileName);
            if (!copyFile(sourceFile, targetFile)) {
                // stop on first error
                return false;
            }
        }
        // Only after successfully copying all files, delete files on source folder.
        for (File sourceFile : sourceFiles) {
            if (!deleteFile(sourceFile)) {
                // stop on first error
                return false;
            }
        }
        return true;
    }

    /**
     * Create a folder within the constraints of the SAF.
     *
     * @param folder The folder to be created.
     * @return True if creation was successful.
     */
    public boolean mkDir(final File folder) throws WritePermissionException {
        if (folder.exists()) {
            // nothing to create.
            return folder.isDirectory();
        }

        // Try the normal way
        if (folder.mkdir()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Util.hasLollipop()) {
            UsefulDocumentFile document = getDocumentFile(folder, true, true);
            if (document == null)
                return false;
            // getLollipopDocument implicitly creates the directory.
            return document.exists();
        }

        return false;
    }

    /**
     * Create a folder within the constraints of the SAF.
     *
     * @param folder The folder to be created.
     * @return True if creation was successful.
     */
    public boolean mkDir(final Uri folder) throws WritePermissionException {
        UsefulDocumentFile document = getDocumentFile(folder, true, true);
        if (document == null)
            return false;
        // getLollipopDocument implicitly creates the directory.
        return document.exists();
    }

    /**
     * Delete a folder within the constraints of SAF
     *
     * @param folder The folder
     * @return true if successful.
     */
    public boolean rmDir(final File folder) throws WritePermissionException {
        if (!folder.exists()) {
            return true;
        }
        if (!folder.isDirectory()) {
            return false;
        }
        String[] fileList = folder.list();
        if (fileList != null && fileList.length > 0) {
            // Delete only empty folder.
            return false;
        }

        // Try the normal way
        if (folder.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Util.hasLollipop()) {
            UsefulDocumentFile document = getDocumentFile(folder, true, true);
            return document != null && document.delete();
        }

        return !folder.exists();
    }

    /**
     * Delete a folder within the constraints of SAF
     *
     * @param folder The folder
     * @return true if successful.
     */
    public boolean rmDir(final Uri folder) throws WritePermissionException {
        UsefulDocumentFile folderDoc = getDocumentFile(folder, true, true);
        UsefulDocumentFile.FileData file = folderDoc.getData();
        return !file.exists || file.isDirectory && folderDoc.listFiles().length <= 0 && folderDoc.delete();
    }

    /**
     * Delete all files in a folder.
     *
     * @param folder the folder
     * @return true if successful.
     */
    public boolean deleteFilesInFolder(final File folder) throws WritePermissionException {
        boolean totalSuccess = true;

        String[] children = folder.list();
        if (children != null) {
            for (String aChildren : children) {
                File file = new File(folder, aChildren);
                if (!file.isDirectory()) {
                    boolean success = deleteFile(file);
                    if (!success) {
                        Log.w(TAG, "Failed to delete file" + aChildren);
                        totalSuccess = false;
                    }
                }
            }
        }
        return totalSuccess;
    }

    /**
     * Delete all files in a folder.
     *
     * @param folder the folder
     * @return true if successful.
     */
    public boolean deleteFilesInFolder(final Uri folder) throws WritePermissionException {
        boolean totalSuccess = true;
        UsefulDocumentFile folderDoc = getDocumentFile(folder, true, true);
        UsefulDocumentFile[] children = folderDoc.listFiles();
        for (UsefulDocumentFile child : children) {
            if (!child.isDirectory()) {
                if (!child.delete()) {
                    Log.w(TAG, "Failed to delete file" + child);
                    totalSuccess = false;
                }
            }
        }
        return totalSuccess;
    }

    /**
     * Get a list of external SD card paths. (Kitkat or higher.)
     *
     * @return A list of external SD card paths.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected String[] getExtSdCardPaths() {
        List<String> paths = new ArrayList<>();
        for (File file : getExternalFilesDirs("external")) {
            if (file != null && !file.equals(getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Log.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        return paths.toArray(new String[paths.size()]);
    }

    public UsefulDocumentFile getDocumentFile(final File file,
                                              final boolean isDirectory,
                                              final boolean createDirectories)
            throws WritePermissionException {
        return getDocumentFile(Uri.fromFile(file), isDirectory, createDirectories);
    }

    /**
     * Get a DocumentFile corresponding to the given file.  If the file does not exist, it is created.
     *
     * @param uri               The file.
     * @param isDirectory       flag indicating if the file should be a directory.
     * @param createDirectories flag indicating if intermediate path directories should be created if not existing.
     * @return The DocumentFile
     */
    public UsefulDocumentFile getDocumentFile(final Uri uri,
                                              final boolean isDirectory,
                                              final boolean createDirectories)
            throws WritePermissionException {
        /* The logic flow here may seem a bit odd, but the goal is to ensure minimal resolver calls
        Most file data calls to a DocumentFile involve a resolver, so we split the logic a bit
		to avoid additional .canWrite and .exists calls. */
        UsefulDocumentFile targetDoc = UsefulDocumentFile.fromUri(this, uri);
        UsefulDocumentFile.FileData targetData = targetDoc.getData();

        // TODO: I believe null will always supersede .exists, check
        if (targetData == null || !targetData.exists) {
            String name;
            if (targetData == null) //target likely doesn't exist, get the name
                name = targetDoc.getName();
            else
                name = targetData.name;

            UsefulDocumentFile parent = targetDoc.getParentFile();
            if (parent == null && !createDirectories)
                return null;

			/* This next part is necessary because DocumentFile.findFile is extremely slow in large
            folders, so what we do instead is track up the tree what folders need creation
			and place them in a stack (Using the convenient *working* UsefulDocumentFile.getParentFile).
			We then walk the stack back down creating folders as needed. */

            Stack<UsefulDocumentFile> hierarchyTree = new Stack<>();
            // Create an hierarchical tree stack of folders that need creation
            // Stop if the parent exists or we've reached the root
            while (parent != null && !parent.exists()) {
                hierarchyTree.push(parent);
                parent = parent.getParentFile();
            }

            // We should be at the top of the tree
            if (parent != null && !hierarchyTree.empty()) {
                UsefulDocumentFile outerDirectory = parent;
                // Now work back down to create the directories if needed
                while (!hierarchyTree.empty()) {
                    UsefulDocumentFile innerDirectory = hierarchyTree.pop();
                    outerDirectory = outerDirectory.createDirectory(innerDirectory.getName());
                    if (outerDirectory == null) {
                        // TODO: Should we assume write permission is the issue here?
                        requestWritePermission();
                        throw new WritePermissionException(
                                "Write permission not found. This indicates a SAF write permission was requested. " +
                                        "The app should store any parameters necessary to resume write here.");
                    }
                }
            }

            parent = targetDoc.getParentFile();

            if (isDirectory) {
                targetDoc = parent.createDirectory(name);
            } else {
                targetDoc = parent.createFile(null, name);
            }

            // If the target could not be created we don't have write permission. Possible other reasons?
            if (targetDoc == null) {
                throw new WritePermissionException(
                        "Write permission not found. This indicates a SAF write permission was requested. " +
                                "The app should store any parameters necessary to resume write here.");
            }
        } else {
            // If we can't write and don't have permission yet
            // It's possible we can write without permission, so don't rely on just permission
            // TODO: Can we do this without a resolver call?
            if (!targetData.canWrite && !hasPermission(uri)) {
                throw new WritePermissionException(
                        "Write permission not found. This indicates a SAF write permission was requested. " +
                                "The app should store any parameters necessary to resume write here.");
            }
        }

        return targetDoc;
    }

    /**
     * Determines if a given uri has a permission in its root
     *
     * @return true if the uri has permission
     */
    public boolean hasPermission(Uri uri) {
        return getPermissibleRoot(uri) != null;
    }

    /**
     * Returns the permissible root uri if one exists, null if not.
     *
     * @return The tree URI.
     */
    public Uri getPermissibleRoot(Uri uri) {
        if (uri == null)
            return null;

        // Files can't be correlated to UriPermissions so rely on canWrite?
        if (FileUtil.isFileScheme(uri)) {
            File f = new File(uri.getPath());

            while (f != null && !f.canWrite()) {
                if (f.canWrite())
                    return Uri.fromFile(f);
                f = f.getParentFile();
            }
            return null;
        } else {
            for (UriPermission permission : mRootPermissions) {
                String permissionTreeId = DocumentsContract.getTreeDocumentId(permission.getUri());
                String uriTreeId = DocumentsContract.getTreeDocumentId(uri);

                if (uriTreeId.startsWith(permissionTreeId)) {
                    return permission.getUri();
                }
            }
        }

        return null;
    }

    public List<UriPermission> getRootPermissions() {
        return Collections.unmodifiableList(mRootPermissions);
    }

    @TargetApi(21)
    protected void requestWritePermission() {
        if (Util.hasLollipop()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView image = new ImageView(DocumentActivity.this);
                    image.setImageDrawable(getDrawable(R.drawable.document_api_guide));
                    AlertDialog.Builder builder =
                            new AlertDialog.Builder(DocumentActivity.this)
                                    .setTitle(R.string.dialogWriteRequestTitle)
                                    .setView(image);
                    final AlertDialog dialog = builder.create();
                    image.setOnClickListener(new View.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.allowWrite));
                            startActivityForResult(intent, REQUEST_CODE_WRITE_PERMISSION);
                            dialog.dismiss();
                        }
                    });
                    dialog.show();
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_WRITE_PERMISSION:
                if (resultCode == RESULT_OK && data != null) {
                    Uri treeUri = data.getData();
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    updatePermissions();

                    // This will resume any actions pending write permission
                    onResumeWriteAction(mCallingMethod, mCallingParameters);
                }
                break;
        }
    }

    /**
     * Override this method to handle the resuming of any write actions that were interrupted
     * by a SAF write request.
     *
     * @param callingMethod     The method to resume
     * @param callingParameters the parameters to pass to callingMethod
     */
    protected abstract void onResumeWriteAction(Enum callingMethod, Object[] callingParameters);

    /**
     * The following methods handle the wiring for resuming write actions that are interrupted by
     * requesting write permission.  Essentially you will overload
     */

    /**
     * Use this method to set the method currently involved in a write action to allow it to be
     * resume in the event of a permission request
     *
     * @param callingMethod enum values defining the method to restart
     */
    protected void setWriteMethod(Enum callingMethod) {
        mCallingMethod = callingMethod;
    }

    /**
     * Use this method to set the parameters to pass to the method set in {@link #setWriteMethod(Enum)}
     *
     * @param callingParameters any parameters that would be passed to {@link #setWriteMethod(Enum)}
     */
    protected void setWriteParameters(Object[] callingParameters) {
        mCallingParameters = callingParameters;
    }

    /**
     * Use this method to set the write method and appropriate parameters to resume an interrupted
     * write action when write permission must be requested.
     *
     * @param callingMethod     enum values defining the method to restart
     * @param callingParameters any parameters that will be passed to callingMethod
     */
    protected void setWriteResume(Enum callingMethod, Object[] callingParameters) {
        setWriteMethod(callingMethod);
        setWriteParameters(callingParameters);
    }

    /**
     * Clear any pending write actions.
     */
    protected void clearWriteResume() {
        mCallingMethod = null;
        mCallingParameters = null;
    }

    /**
     * This error is thrown when the application does not have appropriate permission to write.<br><br>
     * <p/>
     * It is recommended that any process that can catch this exception save the calling method via:<br>
     * {@link #setWriteMethod(Enum)},<br>
     * {@link #setWriteParameters(Object[])},<br>
     * {@link #setWriteResume(Enum, Object[])} (convenience method)<br><br>
     * <p/>
     * When this error is thrown the {@link DocumentActivity} will attempt
     * to request permission causing the activity to break at that point.  Upon receiving a
     * successful result it will attempt to restart a saved method.<br><br>
     * <p/>
     * As the described process requires a break to the activity in the form of
     * {@link Activity#startActivityForResult(Intent, int)} it is recommended that the
     * calling method break upon receiving this exception with the intention that it will
     * be completed by {@link DocumentActivity#onResumeWriteAction(Enum, Object[])}
     * after receiving permission.  It is the responsibility of the {@link DocumentActivity}
     * subclass to define {@link DocumentActivity#onResumeWriteAction(Enum, Object[])}.
     */
    public class WritePermissionException extends IOException {
        public WritePermissionException(String message) {
            super(message);
        }
    }
}
