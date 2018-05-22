package me.devsaki.hentoid.dirpicker.ui;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.widget.EditText;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.dirpicker.events.OnMakeDirEvent;
import me.devsaki.hentoid.dirpicker.util.Convert;

/**
 * Created by avluis on 06/12/2016.
 * Create Directory Dialog
 */
class CreateDirDialog {
    private final EditText text;
    private final Context ctx;
    private final EventBus bus;

    CreateDirDialog(Context ctx, EventBus bus, @Nullable String dirName) {
        this.ctx = ctx;
        this.bus = bus;

        text = new EditText(ctx);
        int paddingPx = Convert.dpToPixel(ctx, 16);
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
                    bus.post(new OnMakeDirEvent(currentDir, value.toString()));
                }).setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
