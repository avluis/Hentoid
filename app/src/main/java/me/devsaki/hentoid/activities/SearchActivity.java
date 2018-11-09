package me.devsaki.hentoid.activities;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_MIKAN;

/**
 * Created by Robb on 2018/11
 *
 * TODO - use a RecyclerView for the input and choice chips.
 * Implement an adapter for each recyclerview and feed both adapters with the same data list
 * modeling the currently selected filters. Whenever the filter list is modified, notify both
 * adapters independently to update views. This should cleanup selection behavior and delegate
 * managing views to the RecyclerView framework.
 */
public class SearchActivity extends BaseActivity implements ContentListener, AttributeListener {

    HentoidDB db;

    private TextView tagCategoryText;
    private TextView artistCategoryText;
    private TextView seriesCategoryText;
    private TextView characterCategoryText;
    private TextView languageCategoryText;
    private TextView sourceCategoryText;

    private View startCaption;
    // Panel that displays the "waiting for metadata info" visuals
    private View tagWaitPanel;
    // Image that displays current metadata type title (e.g. "Character search")
    private TextView tagWaitTitle;
    // Image that displays current metadata type icon (e.g. face icon for character)
    private ImageView tagWaitImage;
    // Image that displays metadata search message (e.g. loading up / too many results / no result)
    private TextView tagWaitMessage;
    // Container where selected attributed are displayed
    private ViewGroup searchTags;

    private SearchView tagSearchView;

    private View searchResultsShadow;
    private ViewGroup searchResults;

    // Container where all available attributes are loaded
    private ViewGroup attributeMosaic;

    // Currently selected tab
    private AttributeType selectedTab = AttributeType.TAG;
    // Current search tags
    private List<Attribute> selectedSearchTags = new ArrayList<>();


    // Attributes sort order
    private int attributesSortOrder = Preferences.getAttributesSortOrder();
    // Mode : show library or show Mikan search
    private int mode;
    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;


    // ======== UTIL OBJECTS
    // Handler for text searches; needs to be there to be cancelable upon new key press
    private final Handler searchHandler = new Handler();


    protected static final int TAGFILTER_ACTIVE = 0;
    protected static final int TAGFILTER_SELECTED = 1;
    protected static final int TAGFILTER_INACTIVE = 3;

    protected static final int MAX_ATTRIBUTES_DISPLAYED = 40;


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

        tagCategoryText = findViewById(R.id.textCategoryTag);
        tagCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.TAG));

        artistCategoryText = findViewById(R.id.textCategoryArtist);
        artistCategoryText.setOnClickListener(v -> onAttrButtonClick(AttributeType.ARTIST));

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

        searchResultsShadow = findViewById(R.id.search_results_shadow);
        searchResultsShadow.setOnClickListener(
                v -> {
                    searchResultsShadow.setVisibility(View.GONE);
                    searchResults.setVisibility(View.GONE);
                }
        );
        searchResults = findViewById(R.id.search_results);

        tagWaitPanel = findViewById(R.id.tag_wait_panel);
        tagWaitPanel.setVisibility(View.GONE);
        tagWaitImage = findViewById(R.id.tag_wait_image);
        tagWaitMessage = findViewById(R.id.tag_wait_description);
        tagWaitTitle = findViewById(R.id.tag_wait_title);

        attributeMosaic = findViewById(R.id.tag_suggestion);
        searchTags = findViewById(R.id.search_tags);
        tagSearchView = findViewById(R.id.tag_filter);
        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager) this.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            tagSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }
        tagSearchView.setIconifiedByDefault(false);
        tagSearchView.setQueryHint("Search " + selectedTab.name().toLowerCase());
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (MODE_MIKAN == mode && selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(startCaption, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                } else if (!s.isEmpty()) {
                    submitAttributeSearchQuery(selectedTab, s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (MODE_MIKAN == mode && selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(startCaption, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                    searchHandler.removeCallbacksAndMessages(null);
                } else /*if (!s.isEmpty())*/ {
                    submitAttributeSearchQuery(selectedTab, s, 1000);
                }

                return true;
            }
        });

    }

    private void onQueryUpdated(SparseIntArray attrCount) {
        // TODO "any" button

        int tagCount = attrCount.get(AttributeType.TAG.getCode(), 0);
        tagCategoryText.setText(getString(R.string.category_tag, tagCount));

        // TODO circle in the same tag
        int artistCount = attrCount.get(AttributeType.ARTIST.getCode(), 0);
        artistCategoryText.setText(getString(R.string.category_artist, artistCount));

        int serieCount = attrCount.get(AttributeType.SERIE.getCode(), 0);
        seriesCategoryText.setText(getString(R.string.category_series, serieCount));

        int characterCount = attrCount.get(AttributeType.CHARACTER.getCode(), 0);
        characterCategoryText.setText(getString(R.string.category_character, characterCount));

        int languageCount = attrCount.get(AttributeType.LANGUAGE.getCode(), 0);
        languageCategoryText.setText(getString(R.string.category_language, languageCount));

        if (MODE_LIBRARY == mode) {
            int sourceCount = attrCount.get(AttributeType.SOURCE.getCode(), 0);
            sourceCategoryText.setText(getString(R.string.category_source, sourceCount));
        }
    }

    private void onAttrButtonClick(AttributeType attributeType) {
        selectedTab = attributeType;

        // Set hint on search bar
        tagSearchView.setVisibility(View.VISIBLE);
        tagSearchView.setQuery("", false);
        tagSearchView.setQueryHint(selectedTab.name().toLowerCase());
        // Remove previous tag suggestions
        attributeMosaic.removeAllViews();
        // Run search
        searchMasterData(selectedTab, "");
    }

    private void submitAttributeSearchQuery(AttributeType a, String s) {
        submitAttributeSearchQuery(a, s, 0);
    }

    private void submitAttributeSearchQuery(AttributeType a, final String s, long delay) {
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> searchMasterData(a, s), delay);
    }

    /**
     * Loads the attributes corresponding to the given AttributeType, filtered with the given string
     *
     * @param a Attribute Type whose attributes to retrieve
     * @param s Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     */
    protected void searchMasterData(AttributeType a, final String s) {
        tagWaitImage.setImageResource(a.getIcon());
        tagWaitTitle.setText(format("%s search", Helper.capitalizeString(a.name())));
        tagWaitMessage.setText(R.string.downloads_loading);

        // Set blinking animation
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(750);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        tagWaitMessage.startAnimation(anim);

        tagWaitPanel.setVisibility(View.VISIBLE);
        searchResults.setVisibility(View.VISIBLE);
        searchResultsShadow.setVisibility(View.VISIBLE);
        collectionAccessor.getAttributeMasterData(a, s, this);
    }

    /*
     AttributeListener implementation
      */
    @Override
    public void onAttributesReady(List<Attribute> results, int totalContent) {
        attributeMosaic.removeAllViews();

        tagWaitMessage.clearAnimation();

        if (0 == totalContent) {
            tagWaitMessage.setText(R.string.masterdata_no_result);
        } else if (totalContent > MAX_ATTRIBUTES_DISPLAYED) {
            String searchQuery = tagSearchView.getQuery().toString();

            String errMsg = (0 == searchQuery.length()) ? getString(R.string.masterdata_too_many_results_noquery) : getString(R.string.masterdata_too_many_results_query);
            tagWaitMessage.setText(errMsg.replace("%1", searchQuery));
        } else {
            // Sort items according to prefs
            Comparator<Attribute> comparator;
            switch (attributesSortOrder) {
                case Preferences.Constant.PREF_ORDER_ATTRIBUTES_ALPHABETIC:
                    comparator = Attribute.NAME_COMPARATOR;
                    break;
                default:
                    comparator = Attribute.COUNT_COMPARATOR;
            }
            Attribute[] attrs = results.toArray(new Attribute[0]); // Well, yes, since results.sort(comparator) requires API 24...
            Arrays.sort(attrs, comparator);

            // Display buttons
            for (Attribute attr : attrs) {
                addChoiceChip(attributeMosaic, attr);
            }

            // Update attribute mosaic buttons state according to available metadata
            updateAttributeMosaic();
            tagWaitPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttributesFailed(String message) {
        Timber.w(message);
        Snackbar.make(startCaption, message, Snackbar.LENGTH_SHORT).show(); // TODO: 9/11/2018 consider retry button if applicable
        tagWaitPanel.setVisibility(View.GONE);
    }

    private void addInputChip(ViewGroup parent, Attribute attribute) {
        String type = attribute.getType().name().toLowerCase();
        String name = attribute.getName();

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_input, parent, false);
        chip.setText(format("%s: %s", type, name));
        chip.setTag(attribute); // TODO - is this necessary for input chips?
        chip.setId(Math.abs(attribute.getId()));
        chip.setOnClickListener(v -> removeSearchFilter(v, attribute));

        parent.addView(chip);
    }

    private void addChoiceChip(ViewGroup parent, Attribute attribute) {
        String label = format("%s %s", attribute.getName(), attribute.getCount() > 0 ? "(" + attribute.getCount() + ")" : "");

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_choice, parent, false);
        chip.setText(label);
        chip.setTag(attribute);
        chip.setId(Math.abs(attribute.getId())); // TODO - is this necessary for choice chips?
        chip.setOnClickListener(this::toggleSearchFilter);

        FlexboxLayout.LayoutParams lp = (FlexboxLayout.LayoutParams) chip.getLayoutParams();
        lp.setFlexGrow(1);

        parent.addView(chip);
    }

    /**
     * Applies to the edges and text of the given Button the color corresponding to the given state
     * TODO - use a StateListDrawable resource for this
     *
     * @param b        Button to be updated
     * @param tagState Tag state whose color has to be applied
     */
    private void colorChip(View b, int tagState) {
        int color = ResourcesCompat.getColor(getResources(), R.color.red_item, null);
        if (TAGFILTER_SELECTED == tagState) {
            color = ResourcesCompat.getColor(getResources(), R.color.red_accent, null);
        } else if (TAGFILTER_INACTIVE == tagState) {
            color = Color.DKGRAY;
        }
        b.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    private void removeSearchFilter(View v, Attribute a) {
        searchTags.removeView(v);
        colorChip(v, TAGFILTER_ACTIVE); // TODO - this is no-op. what is it for?
        selectedSearchTags.remove(a);
        if (selectedSearchTags.isEmpty()) {
            searchTags.setVisibility(View.GONE);
            startCaption.setVisibility(View.VISIBLE);
        }

        // Launch book search according to new attribute selection
        //searchLibrary(MODE_MIKAN == mode);
        // TODO - count results here
        // Update attribute mosaic buttons state according to available metadata
        updateAttributeMosaic();
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     * @see #removeSearchFilter(View, Attribute)
     */
    private void toggleSearchFilter(View button) {
        Attribute a = (Attribute) button.getTag();

        // Add new tag to the selection
        if (!selectedSearchTags.contains(a)) {
            addInputChip(searchTags, a);
            colorChip(button, TAGFILTER_SELECTED);
            selectedSearchTags.add(a);
            startCaption.setVisibility(View.GONE);
            searchTags.setVisibility(View.VISIBLE);
        } else { // Remove selected tagsearchTags.removeView(v);
            searchTags.removeView(searchTags.findViewById(Math.abs(a.getId())));
            colorChip(button, TAGFILTER_ACTIVE);
            selectedSearchTags.remove(a);
            if (selectedSearchTags.isEmpty()) {
                searchTags.setVisibility(View.GONE);
                startCaption.setVisibility(View.VISIBLE);
            }
        }

        // Launch book search according to new attribute selection
        //searchLibrary(MODE_MIKAN == mode);
        // TODO - count results here
        // Update attribute mosaic buttons state according to available metadata
        updateAttributeMosaic();
    }

    /**
     * Refresh attributes list according to selected attributes
     * NB : available in library mode only because Mikan does not provide enough data for it
     */
    private void updateAttributeMosaic() {
        if (MODE_LIBRARY == mode) {
            List<Attribute> searchTags = new ArrayList<>();
            List<Integer> searchSites = new ArrayList<>();

            for (Attribute attr : selectedSearchTags) {
                if (attr.getType().equals(AttributeType.SOURCE)) searchSites.add(attr.getId());
                else searchTags.add(attr);
            }

            // TODO run DB transaction on a dedicated thread

            // Attributes within selected AttributeType
            List<Attribute> availableAttrs;
            if (selectedTab.equals(AttributeType.SOURCE)) {
                availableAttrs = db.selectAvailableSources();
            } else {
                availableAttrs = db.selectAvailableAttributes(selectedTab.getCode(), searchTags, searchSites, false); // No favourites button in SearchActivity
            }

            // Refresh displayed tag buttons
            boolean found, selected;
            String label = "";
            for (int i = 0; i < attributeMosaic.getChildCount(); i++) {
                TextView button = (TextView) attributeMosaic.getChildAt(i);
                Attribute displayedAttr = (Attribute) button.getTag();
                if (displayedAttr != null) {
                    found = false;
                    for (Attribute attr : availableAttrs)
                        if (attr.getId().equals(displayedAttr.getId())) {
                            found = true;
                            label = attr.getName() + " (" + attr.getCount() + ")";
                            break;
                        }
                    if (!found) {
                        label = displayedAttr.getName() + " (0)";
                    }

                    selected = false;
                    for (Attribute attr : selectedSearchTags)
                        if (attr.getId().equals(displayedAttr.getId())) {
                            selected = true;
                            break;
                        }
                    button.setEnabled(selected || found);
                    colorChip(button, selected ? TAGFILTER_SELECTED : found ? TAGFILTER_ACTIVE : TAGFILTER_INACTIVE);
                    button.setText(label);
                }
            }

            // All AttributeTypes in the background
            SparseIntArray attrCount = db.countAttributesPerType(); // TODO - create a variant query using searchTags and searchSites as input
            attrCount.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());

            // Refreshed displayed attribute buttons
            onQueryUpdated(attrCount);
        }
    }

    @Override
    public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) {

    }

    @Override
    public void onContentFailed(Content content, String message) {

    }
}
