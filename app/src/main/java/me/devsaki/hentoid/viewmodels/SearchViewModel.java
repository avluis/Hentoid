package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanAccessor;
import me.devsaki.hentoid.database.DatabaseAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

import static java.util.Objects.requireNonNull;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;


public class SearchViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();
    private final MutableLiveData<AttributeSearchResult> proposedAttributes = new MutableLiveData<>();
    private final MutableLiveData<AttributeSearchResult> availableAttributes = new MutableLiveData<>();
    private final MutableLiveData<ContentSearchResult> selectedContent = new MutableLiveData<>();
    private final MutableLiveData<SparseIntArray> attributesPerType = new MutableLiveData<>();

    /** @see #setMode(int) */
    private CollectionAccessor collectionAccessor;

    private List<AttributeType> category;


    // === LISTENER HELPERS
    private class AttributesResultListener implements ResultListener<List<Attribute>> {
        private final MutableLiveData<AttributeSearchResult> list;

        AttributesResultListener(MutableLiveData<AttributeSearchResult> list) {
            this.list = list;
        }

        @Override
        public void onResultReady(List<Attribute> results, int totalContent) {
            AttributeSearchResult result = new AttributeSearchResult(results);
            list.postValue(result);
        }

        @Override
        public void onResultFailed(String message) {
            AttributeSearchResult result = new AttributeSearchResult();
            result.success = false;
            result.message = message;
            list.postValue(result);
        }
    }

    private ContentListener contentResultListener = new ContentListener() {
        @Override
        public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) {
            ContentSearchResult result = new ContentSearchResult(results);
            result.totalSelected = totalSelectedContent;
            selectedContent.postValue(result);
        }

        @Override
        public void onContentFailed(Content content, String message) {
            ContentSearchResult result = new ContentSearchResult();
            result.success = false;
            result.message = message;
            selectedContent.postValue(result);
        }
    };

    private ResultListener<SparseIntArray> countResultListener = new ResultListener<SparseIntArray>() {
        @Override
        public void onResultReady(SparseIntArray results, int totalContent) {
            attributesPerType.postValue(results);
        }

        @Override
        public void onResultFailed(String message) {
            SparseIntArray result = new SparseIntArray();
            attributesPerType.postValue(result);
        }
    };


    // === INIT METHODS

    public SearchViewModel(@NonNull Application application) {
        super(application);
        selectedAttributes.setValue(new ArrayList<>());
    }

    public void setMode(int mode) {
        Context ctx = getApplication().getApplicationContext();
        collectionAccessor = (MODE_LIBRARY == mode) ? new DatabaseAccessor(ctx) : new MikanAccessor(ctx);
        countAttributesPerType();
    }

    @NonNull
    public LiveData<AttributeSearchResult> getAvailableAttributesData() {
        return availableAttributes;
    }

    @NonNull
    public LiveData<AttributeSearchResult> getProposedAttributesData() {
        return proposedAttributes;
    }

    @NonNull
    public LiveData<List<Attribute>> getSelectedAttributesData() {
        return selectedAttributes;
    }

    @NonNull
    public LiveData<SparseIntArray> getAttributesCountData() {
        return attributesPerType;
    }

    @NonNull
    public LiveData<ContentSearchResult> getSelectedContentData() {
        return selectedContent;
    }


    // === VERB METHODS

    public void onCategoryChanged(List<AttributeType> category) {
        this.category = category;
        getAvailableAttributes();
    }

    public void onCategoryFilterChanged(String query) {
        collectionAccessor.getAttributeMasterData(category, query, new AttributesResultListener(proposedAttributes));
    }

    public void onAttributeSelected(Attribute a) {
        List<Attribute> selectedAttributesList = requireNonNull(selectedAttributes.getValue());

        // Direct impact on selectedAttributes
        selectedAttributesList.add(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        getAvailableAttributes();
        updateSelectionResult();
    }

    public void onAttributeUnselected(Attribute a) {
        List<Attribute> selectedAttributesList = requireNonNull(selectedAttributes.getValue());

        // Direct impact on selectedAttributes
        selectedAttributesList.remove(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        getAvailableAttributes();
        updateSelectionResult();
    }

    private void countAttributesPerType() {
        collectionAccessor.countAttributesPerType(selectedAttributes.getValue(), countResultListener);
    }

    private void getAvailableAttributes() {
        collectionAccessor.getAvailableAttributes(category, selectedAttributes.getValue(), false, new AttributesResultListener(availableAttributes));
    }

    private void updateSelectionResult() {
        collectionAccessor.searchBooks("", selectedAttributes.getValue(), 1, 1, 1, false, contentResultListener);
    }

    public class AttributeSearchResult {
        public List<Attribute> attributes;
        public boolean success = true;
        public String message;


        AttributeSearchResult() {
            this.attributes = new ArrayList<>();
        }

        AttributeSearchResult(List<Attribute> attributes) {
            this.attributes = attributes;
        }
    }

    public class ContentSearchResult {
        public List<Content> contents;
        public int totalSelected;
        public boolean success = true;
        public String message;

        ContentSearchResult() {
            this.contents = new ArrayList<>();
        }

        ContentSearchResult(List<Content> contents) {
            this.contents = contents;
        }
    }
}
