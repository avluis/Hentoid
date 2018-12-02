package me.devsaki.hentoid.activities;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
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
import me.devsaki.hentoid.viewmodels.SearchViewModel;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;

/**
 * Created by Robb on 2018/11
 *
 * TODO - use a RecyclerView for the input and choice chips.
 * Implement an adapter for each recyclerview and feed both adapters with the same data list
 * modeling the currently selected filters. Whenever the filter list is modified, notify both
 * adapters independently to update views. This should cleanup selection behavior and delegate
 * managing views to the RecyclerView framework.
 */
public class SearchActivity extends BaseActivity {

    private TextView anyCategoryText;
    private TextView tagCategoryText;
    private TextView artistCategoryText;
    private TextView seriesCategoryText;
    private TextView characterCategoryText;
    private TextView languageCategoryText;
    private TextView sourceCategoryText;

    private View startCaption;
    // Container where selected attributed are displayed
    private ViewGroup searchTags;


    // Mode : show library or show Mikan search
    private int mode;
    private SearchViewModel model;


    public static final int TAGFILTER_ACTIVE = 0;
    public static final int TAGFILTER_SELECTED = 1;
    public static final int TAGFILTER_INACTIVE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            mode = intent.getIntExtra("mode", MODE_LIBRARY);
        }

        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        startCaption = findViewById(R.id.startCaption);

        anyCategoryText = findViewById(R.id.textCategoryAny);
        anyCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.values()));
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

        model = ViewModelProviders.of(this).get(SearchViewModel.class);
        model.setMode(mode);
        model.countAttributesPerType().observe(this, this::onTypeCountReady);
        model.getSelectedAttributes().observe(this, this::onAttributeSelected);
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

    /**
     * Handler for Attribute button click
     *
     * @param attributes list of currently selected attributes
     */
    public void onAttributeSelected(List<Attribute> attributes) {

        searchTags.removeAllViews();
        searchTags.setVisibility(attributes.isEmpty()?View.GONE:View.VISIBLE);
        startCaption.setVisibility(attributes.isEmpty()?View.VISIBLE:View.GONE);

        for (Attribute a : attributes) addInputChip(searchTags, a);

        // Launch book search according to new attribute selection
        //searchLibrary(MODE_MIKAN == mode);
        // TODO - count results here
    }

    private void addInputChip(ViewGroup parent, Attribute attribute) {
        String type = attribute.getType().name().toLowerCase();
        String name = attribute.getName();

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_input, parent, false);
        chip.setText(format("%s: %s", type, name));
//        chip.setTag(attribute); // TODO - is this necessary for input chips?
        chip.setId(Math.abs(attribute.getId()));
        chip.setOnClickListener(v -> model.unselectAttribute(attribute));

        parent.addView(chip);
    }
}
