package me.devsaki.hentoid.activities;

import android.app.Activity;
import androidx.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.SelectedAttributeAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import timber.log.Timber;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;

/**
 * Created by Robb on 2018/11
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
    private SelectedAttributeAdapter selectedAttributeAdapter;
    private RecyclerView searchTags;

    // Mode : show library or show Mikan search
    private int mode;

    // ViewModel of this activity
    private SearchViewModel viewModel;


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();
        builder.setUri(SearchActivityBundle.Builder.buildSearchUri(viewModel.getSelectedAttributesData().getValue()));
        outState.putAll(builder.getBundle());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        Uri searchUri = new SearchActivityBundle.Parser(savedInstanceState).getUri();
        if (searchUri != null) {
            List<Attribute> preSelectedAttributes = SearchActivityBundle.Parser.parseSearchUri(searchUri);
            if (preSelectedAttributes != null)
                viewModel.setSelectedAttributes(preSelectedAttributes);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        List<Attribute> preSelectedAttributes = null;
        if (intent != null && intent.getExtras() != null) {

            SearchActivityBundle.Parser parser = new SearchActivityBundle.Parser(intent.getExtras());
            mode = parser.getMode();
            Uri searchUri = parser.getUri();
            if (searchUri != null) preSelectedAttributes = SearchActivityBundle.Parser.parseSearchUri(searchUri);
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
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        searchTags.setLayoutManager(llm);
        selectedAttributeAdapter = new SelectedAttributeAdapter();
        selectedAttributeAdapter.setOnClickListener(this::onAttributeChosen);
        selectedAttributeAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() { // Auto-Scroll to last added item
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                llm.smoothScrollToPosition(searchTags, null, selectedAttributeAdapter.getItemCount());
            }
        });
        searchTags.setAdapter(selectedAttributeAdapter);

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
        if (MODE_LIBRARY == mode)
            updateCategoryButton(sourceCategoryText, attrCount, AttributeType.SOURCE);
    }

    private void updateCategoryButton(TextView button, SparseIntArray attrCount, AttributeType... types) {
        int count = 0;
        for (AttributeType type : types) count += attrCount.get(type.getCode(), 0);

        button.setText(format("%s (%s)", Helper.capitalizeString(types[0].getDisplayName()), count));
        button.setEnabled(count > 0);
    }


    private void onAttrButtonClick(AttributeType... attributeTypes) {
        SearchBottomSheetFragment.show(getSupportFragmentManager(), mode, attributeTypes);
    }

    /**
     * @param attributes list of currently selected attributes
     */
    private void onSelectedAttributesChanged(List<Attribute> attributes) {
        if (attributes.isEmpty()) {
            searchTags.setVisibility(View.GONE);
            startCaption.setVisibility(View.VISIBLE);
        } else {
            searchTags.setVisibility(View.VISIBLE);
            startCaption.setVisibility(View.GONE);

            selectedAttributeAdapter.submitList(attributes);
        }
    }

    private void onAttributeChosen(View button) {
        Attribute a = (Attribute) button.getTag();
        if (a != null) viewModel.onAttributeUnselected(a);
    }

    private void onBooksReady(SearchViewModel.ContentSearchResult result) {
        if (result.success && selectedAttributeAdapter.getItemCount() > 0) {
            searchButton.setText(getString(R.string.search_button).replace("%1", result.totalSelected + "").replace("%2", 1 == result.totalSelected ? "" : "s"));
            searchButton.setVisibility(View.VISIBLE);
        } else {
            searchButton.setVisibility(View.GONE);
            Snackbar.make(startCaption, result.message, Snackbar.LENGTH_LONG);
        }
    }

    private void validateForm() {
        Uri searchUri = SearchActivityBundle.Builder.buildSearchUri(viewModel.getSelectedAttributesData().getValue());
        Timber.d("URI :%s", searchUri);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder().setUri(searchUri);
        Intent returnIntent = new Intent();
        returnIntent.putExtras(builder.getBundle());

        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }
}
