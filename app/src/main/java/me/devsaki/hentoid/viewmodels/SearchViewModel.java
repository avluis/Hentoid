package me.devsaki.hentoid.viewmodels;

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

import static java.util.Objects.requireNonNull;


public class SearchViewModel extends ViewModel {

    private static final String ERROR_INIT = "SearchViewModel has to be initialized by calling initAndStart first";

    private final MutableLiveData<List<Attribute>> selectedAttributes = new MutableLiveData<>();
    private final MutableLiveData<CollectionDAO.AttributeQueryResult> proposedAttributes = new MutableLiveData<>();
    private final MutableLiveData<SparseIntArray> attributesPerType = new MutableLiveData<>();

    private LiveData<Integer> currentCountSource = null;
    private final MediatorLiveData<Integer> selectedContentCount = new MediatorLiveData<>();

    private CollectionDAO collectionDAO;

    private List<AttributeType> category;

    private Disposable countDisposable = Disposables.empty();

    private Disposable filterDisposable = Disposables.disposed();

    private int attributeSortOrder = -1;


    // === INIT METHODS

    public SearchViewModel(CollectionDAO collectionDAO) {
        this.collectionDAO = collectionDAO;
        selectedAttributes.setValue(new ArrayList<>());
    }

    @NonNull
    public LiveData<CollectionDAO.AttributeQueryResult> getProposedAttributesData() {
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

    public void initAndStart(int attributeSortOrder) {
        this.attributeSortOrder = attributeSortOrder;
        countAttributesPerType();
        updateSelectionResult();
    }

    public void onCategoryChanged(List<AttributeType> category) {
        this.category = category;
    }

    public void onCategoryFilterChanged(String query, int pageNum, int itemsPerPage) {
        if (-1 == attributeSortOrder)
            throw new IllegalStateException(ERROR_INIT);

        filterDisposable.dispose();
        filterDisposable = collectionDAO
                .getAttributeMasterDataPaged(
                        category,
                        query,
                        selectedAttributes.getValue(),
                        false,
                        pageNum,
                        itemsPerPage,
                        attributeSortOrder
                )
                .subscribe(proposedAttributes::postValue);
    }

    public void onAttributeSelected(Attribute a) {
        if (-1 == attributeSortOrder)
            throw new IllegalStateException(ERROR_INIT);

        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.add(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
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
        if (-1 == attributeSortOrder)
            throw new IllegalStateException(ERROR_INIT);

        List<Attribute> selectedAttributesList = new ArrayList<>(requireNonNull(selectedAttributes.getValue())); // Create new instance to make ListAdapter.submitList happy

        // Direct impact on selectedAttributes
        selectedAttributesList.remove(a);
        selectedAttributes.setValue(selectedAttributesList);

        // Indirect impact on attributesPerType and availableAttributes
        countAttributesPerType();
        updateSelectionResult();
    }

    private void countAttributesPerType() {
        countDisposable.dispose();
        countDisposable = collectionDAO.countAttributesPerType(selectedAttributes.getValue())
                .subscribe(results -> {
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
                });
    }

    private void updateSelectionResult() {
        if (currentCountSource != null) selectedContentCount.removeSource(currentCountSource);
        currentCountSource = collectionDAO.countBooks("", selectedAttributes.getValue(), false);
        selectedContentCount.addSource(currentCountSource, selectedContentCount::setValue);
    }

    @Override
    protected void onCleared() {
        filterDisposable.dispose();
        countDisposable.dispose();
        super.onCleared();
    }
}
