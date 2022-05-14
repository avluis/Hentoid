package me.devsaki.hentoid.viewmodels;

import static java.util.Objects.requireNonNull;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;

/**
 * ViewModel for the advanced search screen
 */
public class SearchViewModel extends ViewModel {

    private final CollectionDAO collectionDAO;

    // LIVEDATAS

    // Results of queries
    private final MutableLiveData<CollectionDAO.AttributeQueryResult> availableAttributes = new MutableLiveData<>();
    private final MutableLiveData<SparseIntArray> nbAttributesPerType = new MutableLiveData<>();
    private LiveData<Integer> currentSelectedContentCountInternal = null;
    private final MediatorLiveData<Integer> selectedContentCount = new MediatorLiveData<>();

    // Selected attributes (passed between SearchBottomSheetFragment and SearchActivity as LiveData via the ViewModel)
    private final MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();


    // Currently active attribute types
    private List<AttributeType> attributeTypes;

    // Disposables (to cleanup Rx calls and avoid memory leaks)
    private Disposable countDisposable = Disposables.empty();
    private Disposable filterDisposable = Disposables.disposed();

    // Sort order for attributes
    // (used as a variable rather than a direct call to Preferences to facilitate unit testing)
    private final int attributeSortOrder;
    private long selectedGroup = -1;


    public SearchViewModel(@NonNull CollectionDAO collectionDAO, int attributeSortOrder) {
        this.collectionDAO = collectionDAO;
        this.attributeSortOrder = attributeSortOrder;
        selectedAttributes.setValue(new ArrayList<>());
    }

    @Override
    protected void onCleared() {
        filterDisposable.dispose();
        countDisposable.dispose();
        collectionDAO.cleanup();
        super.onCleared();
    }


    @NonNull
    public LiveData<CollectionDAO.AttributeQueryResult> getAvailableAttributesData() {
        return availableAttributes;
    }

    @NonNull
    public LiveData<List<Attribute>> getSelectedAttributesData() {
        return selectedAttributes;
    }

    @NonNull
    public LiveData<SparseIntArray> getAttributesCountData() {
        return nbAttributesPerType;
    }

    @NonNull
    public LiveData<Integer> getSelectedContentCount() {
        return selectedContentCount;
    }


    /**
     * Update the viewmodel according to current query properties
     */
    public void update() {
        countAttributesPerType();
        updateSelectionResult();
    }

    /**
     * Set the attributes type to search in the Atttribute search
     *
     * @param attributeTypes Attribute types the searches will be performed for
     */
    public void setAttributeTypes(@NonNull List<AttributeType> attributeTypes) {
        this.attributeTypes = attributeTypes;
    }

    public void setGroup(long groupId) {
        this.selectedGroup = groupId;
    }

    /**
     * Set and run the query to perform the Attribute search
     *
     * @param query        Content of the attribute name to search (%s%)
     * @param pageNum      Number of the "paged" result to fetch
     * @param itemsPerPage Number of items per result "page"
     */
    public void setAttributeQuery(String query, int pageNum, int itemsPerPage) {
        filterDisposable.dispose();
        filterDisposable = collectionDAO
                .selectAttributeMasterDataPaged(
                        attributeTypes,
                        query,
                        selectedAttributes.getValue(),
                        pageNum,
                        itemsPerPage,
                        attributeSortOrder
                )
                .subscribe(availableAttributes::postValue);
    }

    /**
     * Add the given attribute to the attribute selection for the Content and Attribute searches
     * - Only books tagged with all selected attributes will be among Content search results
     * - Only attributes contained in these books will be among Attribute search results
     *
     * @param attr Attribute to add to current selection
     */
    public void addSelectedAttribute(@NonNull final Attribute attr) {
        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.add(attr);
        setSelectedAttributes(selectedAttributesList);
    }

    /**
     * Set the selected attributes for the Content and Attribute searches
     * - Only books tagged with all selected attributes will be among Content search results
     * - Only attributes contained in these books will be among Attribute search results
     *
     * @param attrs Selected attributes
     */
    public void setSelectedAttributes(@NonNull List<Attribute> attrs) {
        selectedAttributes.setValue(attrs);
        update();
    }

    /**
     * Remove the given attribute from the current selection for the Content and Attribute searches
     * - Only books tagged with all selected attributes will be among Content search results
     * - Only attributes contained in these books will be among Attribute search results
     *
     * @param attr Attribute to remove from current selection
     */
    public void removeSelectedAttribute(@NonNull final Attribute attr) {
        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.remove(attr);
        setSelectedAttributes(selectedAttributesList);
    }

    /**
     * Run the query to get the number of attributes per type
     */
    private void countAttributesPerType() {
        countDisposable.dispose();
        countDisposable = collectionDAO.countAttributesPerType(selectedAttributes.getValue())
                .subscribe(results -> {
                    // Result has to take into account the number of attributes already selected (hence unavailable)
                    List<Attribute> selectedAttrs = selectedAttributes.getValue();
                    if (selectedAttrs != null) {
                        for (Attribute a : selectedAttrs) {
                            // if attribute is excluded already, there's no need to reduce attrPerType value,
                            // since attr is no longer amongst results
                            if (!a.isExcluded()) {
                                int countForType = results.get(a.getType().getCode());
                                if (countForType > 0)
                                    results.put(a.getType().getCode(), --countForType);
                            }
                        }
                    }

                    nbAttributesPerType.postValue(results);
                });
    }

    /**
     * Run the query to get the available Attributes and Content
     */
    private void updateSelectionResult() {
        if (currentSelectedContentCountInternal != null)
            selectedContentCount.removeSource(currentSelectedContentCountInternal);
        currentSelectedContentCountInternal = collectionDAO.countBooks(selectedGroup, selectedAttributes.getValue());
        selectedContentCount.addSource(currentSelectedContentCountInternal, selectedContentCount::setValue);
    }
}
