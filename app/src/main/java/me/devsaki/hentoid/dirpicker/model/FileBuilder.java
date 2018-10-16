package me.devsaki.hentoid.dirpicker.model;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Created by avluis on 06/12/2016.
 * File Builder
 */
public class FileBuilder extends File {
    private String name;

    public FileBuilder(String path) {
        super(path);
    }

    @NonNull
    @Override
    public String getName() {
        if (this.name != null) {
            return this.name;
        }

        return super.getName();
    }

    public void setName(String name) {
        this.name = name;
    }
}
