package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanCollectionAccessor;
import me.devsaki.hentoid.database.DatabaseCollectionAccessor;
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
    private final MutableLiveData<AttributeSearchResult> availableAttributes = new MutableLiveData<>();
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
        public void onResultReady(List<Attribute> results, int totalContent) {

            // Sort items according to prefs
            Comparator<Attribute> comparator;
            switch (Preferences.getAttributesSortOrder()) {
                case Preferences.Constant.PREF_ORDER_ATTRIBUTES_ALPHABETIC:
                    comparator = Attribute.NAME_COMPARATOR;
                    break;
                default:
                    comparator = Attribute.COUNT_COMPARATOR;
            }
            Attribute[] attrs = results.toArray(new Attribute[0]); // Well, yes, since results.sort(comparator) requires API 24...
            Arrays.sort(attrs, comparator);

            AttributeSearchResult result = new AttributeSearchResult(attrs);
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
        public void onResultReady(SparseIntArray results, int totalContent) {
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
        collectionAccessor = (MODE_LIBRARY == mode) ? new DatabaseCollectionAccessor(ctx) : new MikanCollectionAccessor(ctx);
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
        getAvailableAttributes();
    }

    public void onCategoryFilterChanged(String query) {
        if (collectionAccessor.supportsAvailabilityFilter())
            collectionAccessor.getAttributeMasterData(category, query, selectedAttributes.getValue(), false, new AttributesResultListener(proposedAttributes));
        else
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

    public void setSelectedAttributes(List<Attribute> attrs) {
        selectedAttributes.setValue(attrs);

        // Indirect impact on attributesPerType
        countAttributesPerType();
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
        collectionAccessor.countAttributesPerType(selectedAttributes.getValue(), countPerTypeResultListener);
    }

    private void getAvailableAttributes() {
        if (collectionAccessor.supportsAvailabilityFilter())
            collectionAccessor.getAvailableAttributes(category, selectedAttributes.getValue(), false, new AttributesResultListener(availableAttributes));
    }

    private void updateSelectionResult() {
        collectionAccessor.countBooks("", selectedAttributes.getValue(), false, contentResultListener);
    }

    // === HELPER RESULT STRUCTURES
    public class AttributeSearchResult {
        public List<Attribute> attributes;
        public boolean success = true;
        public String message;


        AttributeSearchResult() {
            this.attributes = new ArrayList<>();
        }

        AttributeSearchResult(Attribute[] attributes) {
            this.attributes = Arrays.asList(attributes);
        }
    }

    public class ContentSearchResult {
        public int totalSelected;
        public boolean success = true;
        public String message;
    }
}
