package me.devsaki.hentoid.dirpicker.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * Created by avluis on 06/12/2016.
 * File Builder
 */
public class FileBuilder extends File {
    private String name;

    public FileBuilder(String path) {
        super(path);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileBuilder)) {
            return false;
        }

        FileBuilder fileBuilder = (FileBuilder) o;

        return this == o || Objects.equals(fileBuilder.getName(), name);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
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
