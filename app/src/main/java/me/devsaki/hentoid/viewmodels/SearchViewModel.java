package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanAccessor;
import me.devsaki.hentoid.database.DatabaseAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;


public class SearchViewModel extends AndroidViewModel {

    // === VARIABLES
    // Populated by user actions only
    private MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();

    // Populated with DB queries
    private MutableLiveData<AttributeSearchResult> proposedAttributes = new MutableLiveData<>();
    private MutableLiveData<AttributeSearchResult> availableAttributes = new MutableLiveData<>();
    private MutableLiveData<ContentSearchResult> selectedContent = new MutableLiveData<>();
    private MutableLiveData<SparseIntArray> attributesPerType = new MutableLiveData<>();

    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;


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
    }

    public void setMode(int mode) {
        collectionAccessor = (MODE_LIBRARY == mode) ? new DatabaseAccessor(getApplication().getApplicationContext()) : new MikanAccessor(getApplication().getApplicationContext());
        countAttributesPerType();
    }

    public LiveData<AttributeSearchResult> getAvailableAttributesData() {
        return availableAttributes;
    }

    public LiveData<AttributeSearchResult> getProposedAttributesData() {
        return proposedAttributes;
    }

    public LiveData<List<Attribute>> getSelectedAttributesData() {
        return selectedAttributes;
    }

    public LiveData<SparseIntArray> getAttributesCountData() {
        return attributesPerType;
    }

    public LiveData<ContentSearchResult> getSelectedContentData() {
        return selectedContent;
    }

    // === VERB METHODS

    private void countAttributesPerType() {
        collectionAccessor.countAttributesPerType(selectedAttributes.getValue(), countResultListener);
    }

    public void searchAttributes(List<AttributeType> types, String query) {
        collectionAccessor.getAttributeMasterData(types, query, new AttributesResultListener(proposedAttributes));
    }

    public void getAvailableAttributes(List<AttributeType> types) {
        collectionAccessor.getAvailableAttributes(types, selectedAttributes.getValue(), false, new AttributesResultListener(availableAttributes));
    }

    public void searchBooks() {
        collectionAccessor.searchBooks("", selectedAttributes.getValue(), 1, 1, 1, false, contentResultListener);
    }

    public void selectAttribute(List<AttributeType> types, Attribute a) {
        List<Attribute> selectedAttributesList = selectedAttributes.getValue();
        if (null == selectedAttributesList) selectedAttributesList = new ArrayList<>();

        // Direct impact on selectedAttributes
        selectedAttributesList.add(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        getAvailableAttributes(types);
    }

    public void unselectAttribute(Attribute a) {
        unselectAttribute(null, a);
    }

    public void unselectAttribute(List<AttributeType> types, Attribute a) {
        List<Attribute> selectedAttributesList = selectedAttributes.getValue();
        if (null == selectedAttributesList) selectedAttributesList = new ArrayList<>();

        // Direct impact on selectedAttributes
        selectedAttributesList.remove(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        if (types != null) getAvailableAttributes(types);
    }

    // === HELPER RESULT STRUCTURES
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
