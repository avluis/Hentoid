package me.devsaki.hentoid.fragments.intro;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.paolorotolo.appintro.ISlidePolicy;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.IntroActivity;

import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;

public class PermissionIntroFragment extends Fragment implements ISlidePolicy {

    private static final int PERMISSION_REQUEST_CODE = 0;

    private IntroActivity parentActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof IntroActivity) {
            parentActivity = (IntroActivity) context;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) return;
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

    private void invokeAskPermission() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    private void invokeOpenSettings() {
        Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        startActivity(intent);
    }
}
