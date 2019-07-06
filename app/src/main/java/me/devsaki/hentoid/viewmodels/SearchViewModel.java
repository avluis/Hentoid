package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.content.Context;
import androidx.annotation.NonNull;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.model.State;
import me.devsaki.hentoid.util.Preferences;

import static java.util.Objects.requireNonNull;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;


public class SearchViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();
    private final MutableLiveData<AttributeSearchResult> proposedAttributes = new MutableLiveData<>();
    private final MutableLiveData<ContentSearchResult> selectedContent = new MutableLiveData<>();
    private final MutableLiveData<SparseIntArray> attributesPerType = new MutableLiveData<>();

    /**
     * should only be used as a means to communicate with the view without keeping a reference to
     * it, or knowing about it's lifecycle. {@link LiveData#getValue()} is rarely used due to its
     * cumbersome nulllability.
     */
    private final MutableLiveData<State> stateLiveData = new MutableLiveData<>();

    /**
     * @see #setMode(int)
     */
    private CollectionAccessor collectionAccessor;

    private List<AttributeType> category;

    // === LISTENER HELPERS
    private class AttributesResultListener implements ResultListener<List<Attribute>> {
        private final MutableLiveData<AttributeSearchResult> list;

        AttributesResultListener(MutableLiveData<AttributeSearchResult> list) {
            this.list = list;
        }

        @Override
        public void onResultReady(List<Attribute> results, long totalContent) {
            AttributeSearchResult result = new AttributeSearchResult(results, totalContent);
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
        public void onContentReady(List<Content> results, long totalSelectedContent, long totalContent) {
            ContentSearchResult result = new ContentSearchResult();
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

    private ResultListener<SparseIntArray> countPerTypeResultListener = new ResultListener<SparseIntArray>() {
        @Override
        public void onResultReady(SparseIntArray results, long totalContent) {
            // Result has to take into account the number of attributes already selected (hence unavailable)
            List<Attribute> selectedAttrs = selectedAttributes.getValue();
            if (selectedAttrs != null) {
                for (Attribute a : selectedAttrs) {
                    int countForType = results.get(a.getType().getCode());
                    if (countForType > 0)
                        results.put(a.getType().getCode(), --countForType);
                }
            }

            attributesPerType.postValue(results);
        }

        @Override
        public void onResultFailed(String message) {
            attributesPerType.postValue(new SparseIntArray());
        }
    };


    // === INIT METHODS

    public SearchViewModel(@NonNull Application application) {
        super(application);
        selectedAttributes.setValue(new ArrayList<>());
    }

    public void setMode(int mode) {
        Context ctx = getApplication().getApplicationContext();
        collectionAccessor = (MODE_LIBRARY == mode) ? new ObjectBoxCollectionAccessor(ctx) : new MikanCollectionAccessor(ctx);
        countAttributesPerType();
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

    /**
     * Used by the view to observe changes to this ViewModel's state. It is safe to subscribe to
     * this observable before it is given an initial value.
     *
     * @return LiveData holding the current state
     */
    @NonNull
    public MutableLiveData<State> getStateLiveData() {
        return stateLiveData;
    }

    // === VERB METHODS

    public void onCategoryChanged(List<AttributeType> category) {
        this.category = category;
    }

    public void onCategoryFilterChanged(String query, int pageNum, int itemsPerPage) {
        if (collectionAccessor.supportsAttributesPaging()) {
            if (collectionAccessor.supportsAvailabilityFilter())
                collectionAccessor.getPagedAttributeMasterData(category, query, selectedAttributes.getValue(), false, pageNum, itemsPerPage, Preferences.getAttributesSortOrder(), new AttributesResultListener(proposedAttributes));
            else
                collectionAccessor.getPagedAttributeMasterData(category, query, pageNum, itemsPerPage, Preferences.getAttributesSortOrder(), new AttributesResultListener(proposedAttributes));
        } else {
            if (collectionAccessor.supportsAvailabilityFilter())
                collectionAccessor.getAttributeMasterData(category, query, selectedAttributes.getValue(), false, Preferences.getAttributesSortOrder(), new AttributesResultListener(proposedAttributes));
            else
                collectionAccessor.getAttributeMasterData(category, query, Preferences.getAttributesSortOrder(), new AttributesResultListener(proposedAttributes));
        }
    }

    public void onAttributeSelected(Attribute a) {
        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.add(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        updateSelectionResult();
    }

    public void setSelectedAttributes(List<Attribute> attrs) {
        selectedAttributes.setValue(attrs);

        // Indirect impact on attributesPerType
        countAttributesPerType();
        updateSelectionResult();
    }

    public void onAttributeUnselected(Attribute a) {
        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.remove(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        updateSelectionResult();
    }

    private void countAttributesPerType() {
        collectionAccessor.countAttributesPerType(selectedAttributes.getValue(), countPerTypeResultListener);
    }

    private void updateSelectionResult() {
        collectionAccessor.countBooks("", selectedAttributes.getValue(), false, contentResultListener);
    }

    // === HELPER RESULT STRUCTURES
    public class AttributeSearchResult {
        public final List<Attribute> attributes;
        public final long totalContent;
        public boolean success = true;
        public String message;


        AttributeSearchResult() {
            this.attributes = new ArrayList<>();
            this.totalContent = 0;
        }

        AttributeSearchResult(List<Attribute> attributes, long totalContent) {
            this.attributes = new ArrayList<>(attributes);
            this.totalContent = totalContent;
        }
    }

    public class ContentSearchResult {
        public long totalSelected;
        public boolean success = true;
        public String message;
    }

    @Override
    protected void onCleared() {
        if (collectionAccessor != null) collectionAccessor.dispose();
        super.onCleared();
    }
}
