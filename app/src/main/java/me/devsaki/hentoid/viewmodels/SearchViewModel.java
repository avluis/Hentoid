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
    private MutableLiveData<AttributeSearchResult> proposedAttributes;
    private MutableLiveData<AttributeSearchResult> availableAttributes;
    private MutableLiveData<ContentSearchResult> selectedContent;
    private MutableLiveData<SparseIntArray> attributesPerType;

    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;

    private AttributesResultListener availableAttributesListener;
    private AttributesResultListener proposedAttributesListener;


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
    }


    // === FUNCTIONAL METHODS

    public LiveData<SparseIntArray> countAttributesPerType() {
        if (null == attributesPerType) {
            attributesPerType = new MutableLiveData<>();
        }
        collectionAccessor.countAttributesPerType(selectedAttributes.getValue(), countResultListener);
        return attributesPerType;
    }

    public LiveData<AttributeSearchResult> searchAttributes(List<AttributeType> types, String query) {
        if (null == proposedAttributes) {
            proposedAttributes = new MutableLiveData<>();
            proposedAttributesListener = new AttributesResultListener(proposedAttributes);
        }
        collectionAccessor.getAttributeMasterData(types, query, proposedAttributesListener);
        return proposedAttributes;
    }

    public LiveData<AttributeSearchResult> getAvailableAttributes(List<AttributeType> types) {
        if (null == availableAttributes) {
            availableAttributes = new MutableLiveData<>();
            availableAttributesListener = new AttributesResultListener(availableAttributes);
        }
        collectionAccessor.getAvailableAttributes(types, selectedAttributes.getValue(), false, availableAttributesListener);
        return availableAttributes;
    }

    public LiveData<ContentSearchResult> searchBooks() {
        if (null == selectedContent) {
            selectedContent = new MutableLiveData<>();
        }
        collectionAccessor.searchBooks("", selectedAttributes.getValue(), 1, 1, 1, false, contentResultListener);
        return selectedContent;
    }

    public LiveData<List<Attribute>> getSelectedAttributes() {
        return selectedAttributes;
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
