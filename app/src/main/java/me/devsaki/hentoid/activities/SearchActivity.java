package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanAccessor;
import me.devsaki.hentoid.database.DatabaseAccessor;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment;
import me.devsaki.hentoid.listener.ContentListener;

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
public class SearchActivity extends BaseActivity implements ContentListener, View.OnClickListener {

    HentoidDB db;

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

    // Current search tags
    private List<Attribute> selectedSearchTags = new ArrayList<>();


    // Mode : show library or show Mikan search
    private int mode;
    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;


    public static final int TAGFILTER_ACTIVE = 0;
    public static final int TAGFILTER_SELECTED = 1;
    public static final int TAGFILTER_INACTIVE = 3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            mode = intent.getIntExtra("refresh", MODE_LIBRARY);
        }

        collectionAccessor = (MODE_LIBRARY == mode) ? new DatabaseAccessor(this) : new MikanAccessor(this);

        // TODO - Execute DB calls in a worker thread
        db = HentoidDB.getInstance(this);
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

        // Create category buttons
        SparseIntArray attrCount = db.countAttributesPerType();
        attrCount.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());
        onQueryUpdated(attrCount);

        searchTags = findViewById(R.id.search_tags);
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
            // TODO "any" button

            int sourceCount = attrCount.get(AttributeType.SOURCE.getCode(), 0);
            sourceCategoryText.setText(getString(R.string.category_source, sourceCount));
        }
    }

    private void onAttrButtonClick(AttributeType... attributeTypes) {
        SearchBottomSheetFragment.show(getSupportFragmentManager(), mode, attributeTypes, this);
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     * @see #removeSearchFilter(View, Attribute)
     */
    @Override
    public void onClick(View button) {
        Attribute a = (Attribute) button.getTag();

        // Add new tag to the selection
        if (!selectedSearchTags.contains(a)) {
            addInputChip(searchTags, a);
            selectedSearchTags.add(a);
            startCaption.setVisibility(View.GONE);
            searchTags.setVisibility(View.VISIBLE);
        } else { // Remove selected tagsearchTags.removeView(v);
            searchTags.removeView(searchTags.findViewById(Math.abs(a.getId())));
            selectedSearchTags.remove(a);
            if (selectedSearchTags.isEmpty()) {
                searchTags.setVisibility(View.GONE);
                startCaption.setVisibility(View.VISIBLE);
            }
        }

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
        chip.setOnClickListener(v -> removeSearchFilter(v, attribute));

        parent.addView(chip);
    }

    private void removeSearchFilter(View v, Attribute a) {
        searchTags.removeView(v);
        selectedSearchTags.remove(a);
        if (selectedSearchTags.isEmpty()) {
            searchTags.setVisibility(View.GONE);
            startCaption.setVisibility(View.VISIBLE);
        }

        // Launch book search according to new attribute selection
        //searchLibrary(MODE_MIKAN == mode);
        // TODO - count results here

        // All AttributeTypes in the background
        SparseIntArray attrCount = db.countAttributesPerType(); // TODO - create a variant query using searchTags and searchSites as input to make it display actually _available_ items
        attrCount.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());

        onQueryUpdated(attrCount);
        /*
        // Update attribute mosaic buttons state according to available metadata  <-- does not happen, the modal fragment is not visible anymore
        updateAttributeMosaic();
        */
    }

    public List<Attribute> getSelectedSearchTags()
    {
        return selectedSearchTags;
    }

    @Override
    public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) {

    }

    @Override
    public void onContentFailed(Content content, String message) {

    }
}
