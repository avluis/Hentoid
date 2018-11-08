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
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_MIKAN;

/**
 * Created by Robb on 2018/11
 */
public class SearchActivity extends BaseActivity implements ContentListener, AttributeListener {

    HentoidDB db;

    private View startCaption;
    // Bar containing attribute selectors
    private FlexboxLayout attrSelector;
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

        // Create category buttons
        attrSelector = findViewById(R.id.search_tabs);
        SparseIntArray attrCount = db.countAttributesPerType();
        attrCount.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());
        populateAttrSelector(attrCount);

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
                    Snackbar.make(attrSelector, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                } else if (!s.isEmpty()) {
                    submitAttributeSearchQuery(selectedTab, s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (MODE_MIKAN == mode && selectedTab.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(attrSelector, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                    searchHandler.removeCallbacksAndMessages(null);
                } else /*if (!s.isEmpty())*/ {
                    submitAttributeSearchQuery(selectedTab, s, 1000);
                }

                return true;
            }
        });

    }

    private void populateAttrSelector(SparseIntArray attrCount)
    {
        attrSelector.removeAllViews();
        attrSelector.addView(createAttributeSectionButton(AttributeType.LANGUAGE, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.ARTIST, attrCount)); // TODO circle in the same tag
        attrSelector.addView(createAttributeSectionButton(AttributeType.TAG, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.CHARACTER, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.SERIE, attrCount));
        if (MODE_LIBRARY == mode)
            attrSelector.addView(createAttributeSectionButton(AttributeType.SOURCE, attrCount));
        // TODO "any" button
    }

    /**
     * Create the button for the given attribute type
     *
     * @param attr Attribute Type the button should represent
     * @return Button representing the given Attribute type
     */
    private Button createAttributeSectionButton(AttributeType attr, SparseIntArray attrCount) {
        Button button = new Button(new ContextThemeWrapper(this, R.style.LargeItem), null, 0);
        button.setText(String.format("%s (%s)", attr.name(), attrCount.get(attr.getCode(), 0)));

        button.setClickable(true);
        button.setFocusable(true);
        button.setEnabled(true);

        FlexboxLayout.LayoutParams flexParams =
                new FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        FlexboxLayout.LayoutParams.WRAP_CONTENT);
        flexParams.setFlexBasisPercent(0.40f);
        button.setLayoutParams(flexParams);

        button.setOnClickListener(v -> selectAttrButton(button));
        button.setTag(attr);

        return button;
    }

    /**
     * Handler for Attribute type button click
     *
     * @param button Button that has been clicked on
     */
    private void selectAttrButton(Button button) {

        selectedTab = (AttributeType) button.getTag();

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
        tagWaitTitle.setText(String.format("%s search", Helper.capitalizeString(a.name())));
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
                View button = createTagSuggestionChip(attr, false);
                attributeMosaic.addView(button);
                FlexboxLayout.LayoutParams lp = (FlexboxLayout.LayoutParams) button.getLayoutParams();
                lp.setFlexGrow(1);
                button.setLayoutParams(lp);
            }

            // Update attribute mosaic buttons state according to available metadata
            updateAttributeMosaic();
            tagWaitPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttributesFailed(String message) {
        Timber.w(message);
        Snackbar.make(attrSelector, message, Snackbar.LENGTH_SHORT).show(); // TODO: 9/11/2018 consider retry button if applicable
        tagWaitPanel.setVisibility(View.GONE);
    }

    /**
     * Create the button for the given attribute
     *
     * @param attribute  Attribute the button should represent
     * @param isSelected True if the button should appear as selected
     * @return Button representing the given Attribute, drawn as selected if needed
     */
    private TextView createTagSuggestionChip(Attribute attribute, boolean isSelected) {
        TextView chip = new TextView(new ContextThemeWrapper(this, isSelected ? R.style.Chip_Input : R.style.Chip_Choice), null, 0);
        chip.setText(MessageFormat.format("{0}{1}{2}",
                isSelected ? attribute.getType().name().toLowerCase() + ":" : "",
                attribute.getName(),
                !isSelected && attribute.getCount() > 0 ? "(" + attribute.getCount() + ")" : "")
        );

        colorChip(chip, isSelected ? TAGFILTER_SELECTED : TAGFILTER_ACTIVE);

        chip.setTag(attribute);
        chip.setId(Math.abs(attribute.getId()));

        chip.setOnClickListener(v -> selectTagSuggestion(chip));

        return chip;
    }

    /**
     * Applies to the edges and text of the given Button the color corresponding to the given state
     *
     * @param b        Button to be updated
     * @param tagState Tag state whose color has to be applied
     */
    private void colorChip(TextView b, int tagState) {
        int color = ResourcesCompat.getColor(getResources(), R.color.red_item, null);
        if (TAGFILTER_SELECTED == tagState) {
            color = ResourcesCompat.getColor(getResources(), R.color.red_accent, null);
        } else if (TAGFILTER_INACTIVE == tagState) {
            color = Color.DKGRAY;
        }
        b.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private void selectTagSuggestion(TextView button) {
        Attribute a = (Attribute) button.getTag();

        // Add new tag to the selection
        if (!selectedSearchTags.contains(a)) {
            searchTags.addView(createTagSuggestionChip(a, true));
            colorChip(button, TAGFILTER_SELECTED);
            selectedSearchTags.add(a);
            startCaption.setVisibility(View.GONE);
            searchTags.setVisibility(View.VISIBLE);
        } else { // Remove selected tag
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
            populateAttrSelector(attrCount);
        }
    }

    @Override
    public void onContentReady(List<Content> results, int totalSelectedContent, int totalContent) {

    }

    @Override
    public void onContentFailed(Content content, String message) {

    }
}
