package me.devsaki.hentoid.fragments.intro;

import androidx.fragment.app.Fragment;

public class PermissionIntroFragment extends Fragment /*implements SlidePolicy*/ {

    /*
    private IntroActivity_ parentActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof IntroActivity_) {
            parentActivity = (IntroActivity_) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.intro_slide_02, container, false);
    }

    @Override
    public boolean isPolicyRespected() {
        Context context = getContext();
        if (context == null) return false;
        int permissionCode = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionCode == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onUserIllegallyRequestedNextPage() {
        invokeAskPermission();
    }

    private void invokeAskPermission() {
        if (parentActivity != null)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionHelper.RQST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PermissionHelper.RQST_STORAGE_PERMISSION) return;
        if (permissions.length == 0) return;
        if (!permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) return;
        if (grantResults.length == 0) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            parentActivity.onPermissionGranted();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showPermissionComplaint();
        } else {
            showPermissionComplaintManual();
        }
    }

    private void showPermissionComplaint() {
        View view = getView();
        if (view == null) return;
        Snackbar.make(view, R.string.permissioncomplaint_snackbar, LENGTH_LONG)
                .setAction(android.R.string.ok, v -> invokeAskPermission())
                .show();
    }

    private void showPermissionComplaintManual() {
        View view = getView();
        if (view == null) return;
        Snackbar.make(view, R.string.permissioncomplaint_snackbar_manual, LENGTH_LONG)
                .setAction(android.R.string.ok, v -> invokeOpenSettings())
                .show();
    }

    private void invokeOpenSettings() {
        Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        startActivity(intent);
    }

     */
}
