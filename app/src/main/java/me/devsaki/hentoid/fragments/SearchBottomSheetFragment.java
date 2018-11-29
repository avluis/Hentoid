package me.devsaki.hentoid.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
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
import java.util.Objects;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.collection.mikan.MikanAccessor;
import me.devsaki.hentoid.database.DatabaseAccessor;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_LIBRARY;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_MIKAN;
import static me.devsaki.hentoid.activities.SearchActivity.TAGFILTER_ACTIVE;
import static me.devsaki.hentoid.activities.SearchActivity.TAGFILTER_INACTIVE;
import static me.devsaki.hentoid.activities.SearchActivity.TAGFILTER_SELECTED;

public class SearchBottomSheetFragment extends BottomSheetDialogFragment implements AttributeListener {
    // Panel that displays the "waiting for metadata info" visuals
    private View tagWaitPanel;
    // Image that displays current metadata type title (e.g. "Character search")
    private TextView tagWaitTitle;
    // Image that displays current metadata type icon (e.g. face icon for character)
    private ImageView tagWaitImage;
    // Image that displays metadata search message (e.g. loading up / too many results / no result)
    private TextView tagWaitMessage;
    // Search bar
    private SearchView tagSearchView;
    // Container where all available attributes are loaded
    private ViewGroup attributeMosaic;

    // Attributes sort order
    private int attributesSortOrder = Preferences.getAttributesSortOrder();

    // Mode : show library or show Mikan search
    private int mode;
    // Collection accessor (DB or external, depending on mode)
    private CollectionAccessor collectionAccessor;

    // TODO this is ugly
    List<Attribute> selectedAttributes; // Reference to the actual list maintained by SearchActivity

    private List<AttributeType> attributeTypes = new ArrayList<>();
    private AttributeType mainAttr;

    private View.OnClickListener clickListener;


    // ======== UTIL OBJECTS
    // Handler for text searches; needs to be there to be cancelable upon new key press
    private final Handler searchHandler = new Handler();


    // ======== CONSTANTS
    protected static final int MAX_ATTRIBUTES_DISPLAYED = 40;

    public static SearchBottomSheetFragment newInstance(int mode, AttributeType[] types, View.OnClickListener clickListener) {
        SearchBottomSheetFragment searchBottomSheetFragment = new SearchBottomSheetFragment();

        Bundle bundle = new Bundle();
        bundle.putInt("mode", mode);

        ArrayList<Integer> selectedTypes = new ArrayList<>();
        for (AttributeType type : types) selectedTypes.add(type.getCode());
        bundle.putSerializable("attributeTypes", selectedTypes);

        searchBottomSheetFragment.setArguments(bundle);

        searchBottomSheetFragment.setClickListener(clickListener);

        return searchBottomSheetFragment;
    }

    protected void setClickListener(View.OnClickListener listener)
    {
        this.clickListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle != null)
        {
            mode = bundle.getInt("mode", -1);
            List<Integer> attrTypesList = bundle.getIntegerArrayList("attributeTypes");
            if (-1 == mode || null == attrTypesList || attrTypesList.isEmpty()) {
                Timber.d("Initialization failed");
                return;
            }

            for (Integer i : attrTypesList) attributeTypes.add(AttributeType.searchByCode(i));
            mainAttr = attributeTypes.get(0);
        }
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme; // XML declaration doesn't seem to work
    }

    private void submitAttributeSearchQuery(List<AttributeType> a, String s) {
        submitAttributeSearchQuery(a, s, 0);
    }

    private void submitAttributeSearchQuery(List<AttributeType> a, final String s, long delay) {
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> searchMasterData(a, s), delay);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.include_search_filter_category, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = Objects.requireNonNull(getActivity());

        selectedAttributes = ((SearchActivity)activity).getSelectedSearchTags(); // TODO - this is ugly
        collectionAccessor = (MODE_LIBRARY == mode) ? new DatabaseAccessor(activity) : new MikanAccessor(activity);

        View rootView = Objects.requireNonNull(getView());

        tagWaitPanel = rootView.findViewById(R.id.tag_wait_panel);
        tagWaitPanel.setVisibility(View.GONE);
        tagWaitImage = rootView.findViewById(R.id.tag_wait_image);
        tagWaitMessage = rootView.findViewById(R.id.tag_wait_description);
        tagWaitTitle = rootView.findViewById(R.id.tag_wait_title);

        attributeMosaic = rootView.findViewById(R.id.tag_suggestion);

        tagSearchView = rootView.findViewById(R.id.tag_filter);
        // Associate searchable configuration with the SearchView
        final SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            tagSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }
        tagSearchView.setIconifiedByDefault(false);
        tagSearchView.setQueryHint("Search " + mainAttr.name().toLowerCase());
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (MODE_MIKAN == mode && mainAttr.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(Objects.requireNonNull(getView()), R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                } else if (!s.isEmpty()) {
                    submitAttributeSearchQuery(attributeTypes, s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (MODE_MIKAN == mode && mainAttr.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(Objects.requireNonNull(getView()), R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                    searchHandler.removeCallbacksAndMessages(null);
                } else /*if (!s.isEmpty())*/ {
                    submitAttributeSearchQuery(attributeTypes, s, 1000);
                }

                return true;
            }
        });

        searchMasterData(attributeTypes, "");
    }

    /**
     * Loads the attributes corresponding to the given AttributeType, filtered with the given string
     *
     * @param a Attribute Type whose attributes to retrieve
     * @param s Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     */
    protected void searchMasterData(List<AttributeType> a, final String s) {
        tagWaitImage.setImageResource(a.get(0).getIcon());
        tagWaitTitle.setText(format("%s search", Helper.capitalizeString(a.get(0).name())));
        tagWaitMessage.setText(R.string.downloads_loading);

        // Set blinking animation
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(750);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        tagWaitMessage.startAnimation(anim);

        tagWaitPanel.setVisibility(View.VISIBLE);

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
            updateAttributeMosaic(selectedAttributes);
            tagWaitPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttributesFailed(String message) {
        Timber.w(message);
        Snackbar.make(Objects.requireNonNull(getView()), message, Snackbar.LENGTH_SHORT).show(); // TODO: 9/11/2018 consider retry button if applicable
        tagWaitPanel.setVisibility(View.GONE);
    }

    private void addChoiceChip(ViewGroup parent, Attribute attribute) {
        String label = format("%s %s", attribute.getName(), attribute.getCount() > 0 ? "(" + attribute.getCount() + ")" : "");

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_choice, parent, false);
        chip.setText(label);
        chip.setTag(attribute);
//        chip.setId(Math.abs(attribute.getId())); // TODO - is this necessary for choice chips?
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

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private void toggleSearchFilter(View button) {
        Attribute a = (Attribute) button.getTag();

        if (!selectedAttributes.contains(a)) {
            colorChip(button, TAGFILTER_SELECTED);
        } else { // Remove selected tagsearchTags.removeView(v);
            colorChip(button, TAGFILTER_ACTIVE);
        }

        clickListener.onClick(button);

        // Update attribute mosaic buttons state according to available metadata
        updateAttributeMosaic(selectedAttributes);
    }

    /**
     * Refresh attributes list according to selected attributes
     * NB : available in library mode only because Mikan does not provide enough data for it
     */
    private void updateAttributeMosaic(List<Attribute> selectedAttributes) {
        if (MODE_LIBRARY == mode) {
            Activity activity = getActivity();
            if (null == activity) {
                Timber.e("Activity unreachable !");
                return;
            }
            HentoidDB db = HentoidDB.getInstance(activity);

            List<Attribute> searchTags = new ArrayList<>();
            List<Integer> searchSites = new ArrayList<>();

            for (Attribute attr : selectedAttributes) {
                if (attr.getType().equals(AttributeType.SOURCE)) searchSites.add(attr.getId());
                else searchTags.add(attr);
            }

            // TODO run DB transaction on a dedicated thread

            // Attributes within selected AttributeTypes
            List<Attribute> availableAttrs;
            if (mainAttr.equals(AttributeType.SOURCE)) {
                availableAttrs = db.selectAvailableSources();
            } else {
                availableAttrs = new ArrayList<>();
                for (AttributeType type : attributeTypes)
                    availableAttrs.addAll(db.selectAvailableAttributes(type.getCode(), searchTags, searchSites, false)); // No favourites button in SearchActivity
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
                    for (Attribute attr : selectedAttributes)
                        if (attr.getId().equals(displayedAttr.getId())) {
                            selected = true;
                            break;
                        }
                    button.setEnabled(selected || found);
                    colorChip(button, selected ? TAGFILTER_SELECTED : found ? TAGFILTER_ACTIVE : TAGFILTER_INACTIVE);
                    button.setText(label);
                }
            }
        }
    }
}
