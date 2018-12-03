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
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import timber.log.Timber;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;

/**
 * Created by Robb on 2018/11
 * <p>
 * TODO - use a RecyclerView for the input and choice chips.
 * Implement an adapter for each recyclerview and feed both adapters with the same data list
 * modeling the currently selected filters. Whenever the filter list is modified, notify both
 * adapters independently to update views. This should cleanup selection behavior and delegate
 * managing views to the RecyclerView framework.
 */
public class SearchActivity extends BaseActivity {

    // Category buttons
    private TextView anyCategoryText;
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


    // TODO - Activity state save/restore (onSaveInstanceState / onViewStateRestored)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        List<Attribute> preSelectedAttributes = null;
        if (intent != null) {
            mode = intent.getIntExtra("mode", MODE_LIBRARY);
            String searchUriStr = intent.getStringExtra("searchUri");
            if (searchUriStr != null && !searchUriStr.isEmpty())
            {
                Uri searchUri = Uri.parse(searchUriStr);
                preSelectedAttributes = Helper.extractAttributesFromUri(searchUri);
            }
        }

        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        startCaption = findViewById(R.id.startCaption);

        anyCategoryText = findViewById(R.id.textCategoryAny);
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
        viewModel.getAttributesCountData().observe(this, this::onTypeCountReady);
        viewModel.getSelectedAttributesData().observe(this, this::onAttributeSelected);
        viewModel.getSelectedContentData().observe(this, this::onBooksReady);
        if (preSelectedAttributes != null) viewModel.setSelectedAttributes(preSelectedAttributes);
    }

    public void onTypeCountReady(SparseIntArray results) {
        if (results != null && results.size() > 0) onQueryUpdated(results);
    }

    private void onQueryUpdated(SparseIntArray attrCount) {
        int tagCount = attrCount.get(AttributeType.TAG.getCode(), 0);
        tagCategoryText.setText(getString(R.string.category_tag, tagCount));

        int artistCount = attrCount.get(AttributeType.ARTIST.getCode(), 0) + attrCount.get(AttributeType.CIRCLE.getCode(), 0);
        artistCategoryText.setText(getString(R.string.category_artist, artistCount));

        int serieCount = attrCount.get(AttributeType.SERIE.getCode(), 0);
        seriesCategoryText.setText(getString(R.string.category_series, serieCount));

        int characterCount = attrCount.get(AttributeType.CHARACTER.getCode(), 0);
        characterCategoryText.setText(getString(R.string.category_character, characterCount));

        int languageCount = attrCount.get(AttributeType.LANGUAGE.getCode(), 0);
        languageCategoryText.setText(getString(R.string.category_language, languageCount));

        if (MODE_LIBRARY == mode) {
            anyCategoryText.setText(getString(R.string.category_any));

            int sourceCount = attrCount.get(AttributeType.SOURCE.getCode(), 0);
            sourceCategoryText.setText(getString(R.string.category_source, sourceCount));
        }
    }

    private void onAttrButtonClick(AttributeType... attributeTypes) {
        SearchBottomSheetFragment.show(getSupportFragmentManager(), mode, attributeTypes);
    }

    private void addInputChip(ViewGroup parent, Attribute attribute) {
        String type = attribute.getType().name().toLowerCase();
        String name = attribute.getName();

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_input, parent, false);
        chip.setText(format("%s: %s", type, name));
        chip.setId(Math.abs(attribute.getId()));
        chip.setOnClickListener(v -> viewModel.unselectAttribute(attribute));

        parent.addView(chip);
    }

    /**
     * Handler for Attribute button click
     *
     * @param attributes list of currently selected attributes
     */
    public void onAttributeSelected(List<Attribute> attributes) {

        searchTags.removeAllViews();
        searchTags.setVisibility(attributes.isEmpty() ? View.GONE : View.VISIBLE);
        startCaption.setVisibility(attributes.isEmpty() ? View.VISIBLE : View.GONE);

        for (Attribute a : attributes) addInputChip(searchTags, a);

        // Launch book search according to new attribute selection
        viewModel.countBooks();
    }

    private void onBooksReady(SearchViewModel.ContentSearchResult result) {
        if (result.success) {
            searchButton.setText(getString(R.string.search_button).replace("%1", result.totalSelected + "").replace("%2", 1 == result.totalSelected ? "" : "s"));
            searchButton.setVisibility(View.VISIBLE);
        } else {
            searchButton.setVisibility(View.GONE);
            Snackbar.make(startCaption, result.message, Snackbar.LENGTH_LONG);
        }
    }

    private void validateForm() {
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.add(viewModel.getSelectedAttributesData().getValue());

        Uri.Builder searchUri = new Uri.Builder();
        searchUri.scheme("search").authority("hentoid");

        for (AttributeType attrType : metadataMap.keySet()) {
            List<Attribute> attrs = metadataMap.get(attrType);
            for (Attribute attr : attrs) searchUri.appendQueryParameter(attrType.name(), attr.getName());
        }

        Intent returnIntent = new Intent();
        returnIntent.putExtra("searchUri", searchUri.build().toString());
        Timber.d("URI :%s", searchUri.build().toString());

        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
