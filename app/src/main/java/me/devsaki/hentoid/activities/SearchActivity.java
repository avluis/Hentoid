package me.devsaki.hentoid.activities;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment;
import me.devsaki.hentoid.util.BundleManager;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import timber.log.Timber;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;

/**
 * Created by Robb on 2018/11
 * <p>
 * TODO - use a RecyclerView for the input and choice chips. Implement an adapter for each
 * recyclerview and feed both adapters with the same data list modeling the currently selected
 * filters. Whenever the filter list is modified, notify both adapters independently to update
 * views. This should cleanup selection behavior and delegate managing views to the RecyclerView
 * framework.
 */
public class SearchActivity extends BaseActivity {

    private TextView tagCategoryText;
    private TextView artistCategoryText;
    private TextView seriesCategoryText;
    private TextView characterCategoryText;
    private TextView languageCategoryText;
    private TextView sourceCategoryText;

    // Book search button at the bottom of screen
    private TextView searchButton;
    // Caption that says "Select a filter" on top of screen
    private View startCaption;
    // Container where selected attributed are displayed
    private ViewGroup searchTags;

    // Mode : show library or show Mikan search
    private int mode;

    // ViewModel of this activity
    private SearchViewModel viewModel;


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleManager manager = new BundleManager(outState);
        manager.setUri(Helper.buildSearchUri(viewModel.getSelectedAttributesData().getValue()));
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        BundleManager manager = new BundleManager(savedInstanceState);
        Uri searchUri = manager.getUri();

        if (searchUri != null) {
            List<Attribute> preSelectedAttributes = Helper.parseSearchUri(searchUri);
            if (preSelectedAttributes != null)
                viewModel.setSelectedAttributes(preSelectedAttributes);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        List<Attribute> preSelectedAttributes = null;
        if (intent != null) {
            BundleManager manager = new BundleManager(intent.getExtras());
            mode = manager.getMode();
            Uri searchUri = manager.getUri();
            if (searchUri != null) preSelectedAttributes = Helper.parseSearchUri(searchUri);
        }

        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        startCaption = findViewById(R.id.startCaption);

        // Category buttons
        TextView anyCategoryText = findViewById(R.id.textCategoryAny);
        anyCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.TAG, AttributeType.ARTIST,
                AttributeType.CIRCLE, AttributeType.SERIE, AttributeType.CHARACTER, AttributeType.LANGUAGE)); // Everything but source !
        anyCategoryText.setEnabled(MODE_LIBRARY == mode); // Unsupported by Mikan

        tagCategoryText = findViewById(R.id.textCategoryTag);
        tagCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.TAG));

        artistCategoryText = findViewById(R.id.textCategoryArtist);
        artistCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.ARTIST, AttributeType.CIRCLE));

        seriesCategoryText = findViewById(R.id.textCategorySeries);
        seriesCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.SERIE));

        characterCategoryText = findViewById(R.id.textCategoryCharacter);
        characterCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.CHARACTER));

        languageCategoryText = findViewById(R.id.textCategoryLanguage);
        languageCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.LANGUAGE));

        sourceCategoryText = findViewById(R.id.textCategorySource);
        sourceCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.SOURCE));

        searchTags = findViewById(R.id.search_tags);

        searchButton = findViewById(R.id.search_fab);
        searchButton.setOnClickListener(v -> validateForm());

        viewModel = ViewModelProviders.of(this).get(SearchViewModel.class);
        viewModel.setMode(mode);
        viewModel.getAttributesCountData().observe(this, this::onQueryUpdated);
        viewModel.getSelectedAttributesData().observe(this, this::onSelectedAttributesChanged);
        viewModel.getSelectedContentData().observe(this, this::onBooksReady);
        if (preSelectedAttributes != null) viewModel.setSelectedAttributes(preSelectedAttributes);
    }

    private void onQueryUpdated(SparseIntArray attrCount) {
        updateCategoryButton(tagCategoryText, attrCount, AttributeType.TAG);
        updateCategoryButton(artistCategoryText, attrCount, AttributeType.ARTIST, AttributeType.CIRCLE);
        updateCategoryButton(seriesCategoryText, attrCount, AttributeType.SERIE);
        updateCategoryButton(characterCategoryText, attrCount, AttributeType.CHARACTER);
        updateCategoryButton(languageCategoryText, attrCount, AttributeType.LANGUAGE);
        if (MODE_LIBRARY == mode) updateCategoryButton(sourceCategoryText, attrCount, AttributeType.SOURCE);
    }

    private void updateCategoryButton(TextView button, SparseIntArray attrCount, AttributeType... types) {
        int count = 0;
        for (AttributeType type : types) count += attrCount.get(type.getCode(), 0);

        button.setText(format("%s (%s)", Helper.capitalizeString(types[0].name()), count ));
        button.setEnabled(count > 0);
    }


    private void onAttrButtonClick(AttributeType... attributeTypes) {
        SearchBottomSheetFragment.show(getSupportFragmentManager(), mode, attributeTypes);
    }

    /**
     * @param attributes list of currently selected attributes
     */
    private void onSelectedAttributesChanged(List<Attribute> attributes) {
        searchTags.removeAllViews();

        if (attributes.isEmpty()) {
            searchTags.setVisibility(View.GONE);
            startCaption.setVisibility(View.VISIBLE);
        } else {
            searchTags.setVisibility(View.VISIBLE);
            startCaption.setVisibility(View.GONE);

            // TODO: 04/12/2018 this should be replaced by a RecyclerViewAdapter
            for (Attribute a : attributes) {
                String type = a.getType().name().toLowerCase();
                String name = a.getName();

                TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_input, searchTags, false);
                chip.setText(format("%s: %s", type, name));
                chip.setOnClickListener(v -> viewModel.onAttributeUnselected(a));

                searchTags.addView(chip);
            }
        }
    }

    private void onBooksReady(SearchViewModel.ContentSearchResult result) {
        if (result.success && searchTags.getChildCount() > 0) {
            searchButton.setText(getString(R.string.search_button).replace("%1", result.totalSelected + "").replace("%2", 1 == result.totalSelected ? "" : "s"));
            searchButton.setVisibility(View.VISIBLE);
        } else {
            searchButton.setVisibility(View.GONE);
            Snackbar.make(startCaption, result.message, Snackbar.LENGTH_LONG);
        }
    }

    private void validateForm() {
        Uri searchUri = Helper.buildSearchUri(viewModel.getSelectedAttributesData().getValue());
        Timber.d("URI :%s", searchUri);

        Intent returnIntent = new Intent();
        BundleManager manager = new BundleManager();
        manager.setUri(searchUri);
        returnIntent.putExtras(manager.getBundle());

        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
