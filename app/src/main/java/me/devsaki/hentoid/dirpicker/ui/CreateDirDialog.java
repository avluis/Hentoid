package me.devsaki.hentoid.dirpicker.ui;

import android.content.Context;
import android.text.Editable;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.dirpicker.events.OnMakeDirEvent;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by avluis on 06/12/2016.
 * Create Directory Dialog
 */
class CreateDirDialog {
    private final EditText text;
    private final Context ctx;

    CreateDirDialog(Context ctx, @Nullable String dirName) {
        this.ctx = ctx;

        text = new EditText(ctx);
        int paddingPx = Helper.dpToPixel(ctx, 16);
        text.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        if (dirName != null) {
            text.setText(dirName);
        }
    }

    void dialog(final File currentDir) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.dir_name)
                .setMessage(R.string.dir_name_inst)
                .setView(text)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Editable value = text.getText();
                    EventBus.getDefault().post(new OnMakeDirEvent(currentDir, value.toString()));
                }).setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
