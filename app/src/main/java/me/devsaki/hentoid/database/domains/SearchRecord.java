package me.devsaki.hentoid.database.domains;

import android.net.Uri;

import androidx.annotation.NonNull;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class SearchRecord {

    @Id
    public long id;
    private String searchString;
    private String label;

    public SearchRecord() { // Required by ObjectBox when an alternate constructor exists
    }

    SearchRecord(String searchString, String label) {
        this.searchString = searchString;
        this.label = label;
    }

    public static SearchRecord fromContentUniversalSearch(@NonNull Uri searchUri) {
        return new SearchRecord(searchUri.toString(), searchUri.getPath().substring(1));
    }

    public static SearchRecord fromContentAdvancedSearch(@NonNull Uri searchUri, @NonNull String label) {
        return new SearchRecord(searchUri.toString(), label);
    }


    public String getSearchString() {
        return searchString;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchRecord that = (SearchRecord) o;

        return searchString.equals(that.searchString);
    }

    @Override
    public int hashCode() {
        return searchString.hashCode();
    }
}
