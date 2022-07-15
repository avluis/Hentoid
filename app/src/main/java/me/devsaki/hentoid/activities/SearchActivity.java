package me.devsaki.hentoid.activities;

import static java.lang.String.format;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.SelectedAttributeAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.databinding.ActivitySearchBinding;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

/**
 * Activity for the advanced search screen
 */
public class SearchActivity extends BaseActivity {

    private ActivitySearchBinding binding;

    // Container where selected attributed are displayed
    private SelectedAttributeAdapter selectedAttributeAdapter;

    // ViewModel of this activity
    private SearchViewModel viewModel;

    private boolean excludeClicked = false;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        SearchActivityBundle builder = new SearchActivityBundle();
        builder.setUri(SearchActivityBundle.Companion.buildSearchUri(viewModel.getSelectedAttributesData().getValue(), "").toString());
        outState.putAll(builder.getBundle());
        outState.putBoolean("exclude", excludeClicked);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        excludeClicked = savedInstanceState.getBoolean("exclude");
        Uri searchUri = Uri.parse(new SearchActivityBundle(savedInstanceState).getUri());
        if (searchUri != null) {
            List<Attribute> preSelectedAttributes = SearchActivityBundle.Companion.parseSearchUri(searchUri);
            viewModel.setSelectedAttributes(preSelectedAttributes);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        List<Attribute> preSelectedAttributes = null;
        if (intent != null && intent.getExtras() != null) {
            SearchActivityBundle parser = new SearchActivityBundle(intent.getExtras());
            Uri searchUri = Uri.parse(parser.getUri());
            excludeClicked = parser.getExcludeMode();
            if (searchUri != null)
                preSelectedAttributes = SearchActivityBundle.Companion.parseSearchUri(searchUri);
        }

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Attribute type buttons
        TextView anyTypeButton = findViewById(R.id.textCategoryAny);
        anyTypeButton.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.TAG, AttributeType.ARTIST,
                AttributeType.CIRCLE, AttributeType.SERIE, AttributeType.CHARACTER, AttributeType.LANGUAGE)); // Everything but source !
        anyTypeButton.setEnabled(true);

        binding.textCategoryTag.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.TAG));
        binding.textCategoryArtist.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.ARTIST, AttributeType.CIRCLE));
        binding.textCategorySeries.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.SERIE));
        binding.textCategoryCharacter.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.CHARACTER));
        binding.textCategoryLanguage.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.LANGUAGE));
        binding.textCategorySource.setOnClickListener(v -> onAttrButtonClick(excludeClicked, AttributeType.SOURCE));

        CheckBox excludeCheckBox = findViewById(R.id.checkBox);
        excludeCheckBox.setOnClickListener(this::onExcludeClick);
        excludeCheckBox.setChecked(excludeClicked);


        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.searchTags.setLayoutManager(llm);
        selectedAttributeAdapter = new SelectedAttributeAdapter();
        selectedAttributeAdapter.setOnClickListener(this::onSelectedAttributeClick);
        selectedAttributeAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() { // Auto-Scroll to last added item
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                llm.smoothScrollToPosition(binding.searchTags, null, selectedAttributeAdapter.getItemCount());
            }
        });
        binding.searchTags.setAdapter(selectedAttributeAdapter);

        binding.searchFab.setOnClickListener(v -> searchBooks());

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(SearchViewModel.class);
        viewModel.getAttributesCountData().observe(this, this::onQueryUpdated);
        viewModel.getSelectedAttributesData().observe(this, this::onSelectedAttributesChanged);
        viewModel.getSelectedContentCount().observe(this, this::onBooksCounted);

        if (preSelectedAttributes != null) viewModel.setSelectedAttributes(preSelectedAttributes);
        else viewModel.update();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    /**
     * Observer for changes in the entry count inside each attribute type
     *
     * @param attrCount Entry count in every attribute type (key = attribute type code; value = count)
     */
    private void onQueryUpdated(@NonNull final SparseIntArray attrCount) {
        updateAttributeTypeButton(binding.textCategoryTag, attrCount, AttributeType.TAG);
        updateAttributeTypeButton(binding.textCategoryArtist, attrCount, AttributeType.ARTIST, AttributeType.CIRCLE);
        updateAttributeTypeButton(binding.textCategorySeries, attrCount, AttributeType.SERIE);
        updateAttributeTypeButton(binding.textCategoryCharacter, attrCount, AttributeType.CHARACTER);
        updateAttributeTypeButton(binding.textCategoryLanguage, attrCount, AttributeType.LANGUAGE);
        updateAttributeTypeButton(binding.textCategorySource, attrCount, AttributeType.SOURCE);
    }

    public void onExcludeClick(View view) {
        excludeClicked = ((CheckBox) view).isChecked();
    }

    /**
     * Update the text of a given attribute type button based on the given SparseIntArray and relevant type(s)
     *
     * @param button    Button whose text to update
     * @param attrCount Entry count in every attribute type (key = attribute type code; value = count)
     * @param types     Type(s) to fetch the count for
     */
    private void updateAttributeTypeButton(@NonNull final TextView button, @NonNull final SparseIntArray attrCount, AttributeType... types) {
        if (0 == types.length) return;

        int count = 0;
        for (AttributeType type : types) count += attrCount.get(type.getCode(), 0);

        button.setText(format("%s (%s)", StringHelper.capitalizeString(getString(types[0].getDisplayName())), count));
        button.setEnabled(count > 0);
    }

    /**
     * Handler for the click on a attribute type button
     * Opens the bottom dialog for a given attribute type
     *
     * @param attributeTypes Attribute type(s) to select
     */
    private void onAttrButtonClick(boolean excludeClicked, AttributeType... attributeTypes) {
        SearchBottomSheetFragment.invoke(this, getSupportFragmentManager(), attributeTypes, excludeClicked);
    }

    /**
     * Observer for changes in the selected attributes
     *
     * @param attributes list of currently selected attributes
     */
    private void onSelectedAttributesChanged(List<Attribute> attributes) {
        if (attributes.isEmpty()) {
            binding.searchTags.setVisibility(View.GONE);
            binding.startCaption.setVisibility(View.VISIBLE);
        } else {
            binding.searchTags.setVisibility(View.VISIBLE);
            binding.startCaption.setVisibility(View.GONE);

            selectedAttributeAdapter.submitList(attributes);
        }
    }

    /**
     * Handler for click on a selected attribute
     *
     * @param button Button that has been clicked on; contains the corresponding attribute as its tag
     */
    private void onSelectedAttributeClick(View button) {
        Attribute a = (Attribute) button.getTag();
        if (a != null) viewModel.removeSelectedAttribute(a);
    }

    /**
     * Observer for changes in the entry count of selected content
     * i.e. count of all books of the library matching the currently selected attributes
     *
     * @param count Current book count matching the currently selected attributes
     */
    private void onBooksCounted(int count) {
        if (count >= 0) {
            binding.searchFab.setText(getResources().getQuantityString(R.plurals.search_button, count, count));
            binding.searchFab.setVisibility(View.VISIBLE);
        } else {
            binding.searchFab.setVisibility(View.GONE);
        }
    }

    /**
     * Handler for the click on the "Search books" button
     * Transmit the search query to the library screen and close the advanced search screen
     */
    private void searchBooks() {
        Uri searchUri = SearchActivityBundle.Companion.buildSearchUri(viewModel.getSelectedAttributesData().getValue(), "");
        Timber.d("URI :%s", searchUri);

        SearchActivityBundle builder = new SearchActivityBundle();
        builder.setUri(searchUri.toString());
        builder.setExcludeMode(excludeClicked);

        Intent returnIntent = new Intent();
        returnIntent.putExtras(builder.getBundle());
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
