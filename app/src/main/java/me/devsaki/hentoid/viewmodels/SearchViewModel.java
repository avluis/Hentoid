package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.util.Preferences;

import static java.util.Objects.requireNonNull;


public class SearchViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();
    private final MutableLiveData<AttributeSearchResult> proposedAttributes = new MutableLiveData<>();
    private final MutableLiveData<SparseIntArray> attributesPerType = new MutableLiveData<>();

    private LiveData<Integer> currentCountSource = null;
    private final MediatorLiveData<Integer> selectedContentCount = new MediatorLiveData<>();

    private CollectionDAO collectionDAO;

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
        Context ctx = application.getApplicationContext();
        collectionDAO = new ObjectBoxDAO(ctx);
        selectedAttributes.setValue(new ArrayList<>());
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
    public LiveData<Integer> getSelectedContentCount() {
        return selectedContentCount;
    }

    // === VERB METHODS

    public void onCategoryChanged(List<AttributeType> category) {
        this.category = category;
    }

    public void onCategoryFilterChanged(String query, int pageNum, int itemsPerPage) {
        collectionDAO.getAttributeMasterDataPaged(category, query, selectedAttributes.getValue(), false, pageNum, itemsPerPage, Preferences.getAttributesSortOrder(), new AttributesResultListener(proposedAttributes));
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

    public void emptyStart() {
        countAttributesPerType();
        updateSelectionResult();
    }

    public void setSelectedAttributes(@NonNull List<Attribute> attrs) {
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
        collectionDAO.countAttributesPerType(selectedAttributes.getValue(), countPerTypeResultListener);
    }

    private void updateSelectionResult() {
        if (currentCountSource != null) selectedContentCount.removeSource(currentCountSource);
        currentCountSource = collectionDAO.countBooks("", selectedAttributes.getValue(), false);
        selectedContentCount.addSource(currentCountSource, selectedContentCount::setValue);
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
        if (collectionDAO != null) collectionDAO.dispose();
        super.onCleared();
    }
}
