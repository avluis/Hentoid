package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.support.v7.view.ContextThemeWrapper;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Button;

import com.google.android.flexbox.FlexboxLayout;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.constants.AttributeTable;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;

/**
 * Created by Robb on 2018/11
 */
public class SearchActivity extends BaseActivity {

    HentoidDB db;

    // Bar containing attribute selectors
    private FlexboxLayout attrSelector;

    // Currently selected tab
    private AttributeType selectedTab = AttributeType.TAG;

    // Mode : show library or show Mikan search
    private int mode = DownloadsFragment.MODE_LIBRARY;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO - Execute DB calls in a worker thread
        db = HentoidDB.getInstance(this);
        setContentView(R.layout.activity_search);

        SparseIntArray attrCount = db.countAttributesPerType();
        attrCount.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());

        // Create category buttons
        attrSelector = findViewById(R.id.search_tabs);
        attrSelector.removeAllViews();
        attrSelector.addView(createAttributeSectionButton(AttributeType.LANGUAGE, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.ARTIST, attrCount)); // TODO circle in the same tag
        attrSelector.addView(createAttributeSectionButton(AttributeType.TAG, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.CHARACTER, attrCount));
        attrSelector.addView(createAttributeSectionButton(AttributeType.SERIE, attrCount));
        if (DownloadsFragment.MODE_LIBRARY == mode)
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

/*
        // Reset color of every tab
        for (View v : attrSelector.getTouchables())
            v.setBackgroundResource(R.drawable.btn_attribute_section_off);
        // Set color of selected tab
        button.setBackgroundResource(R.drawable.btn_attribute_section_on);
*/
/*
        // Set hint on search bar
        SearchView tagSearchView = searchPane.findViewById(R.id.tag_filter);
        tagSearchView.setVisibility(View.VISIBLE);
        tagSearchView.setQuery("", false);
        tagSearchView.setQueryHint("Search " + selectedTab.name().toLowerCase());
        // Remove previous tag suggestions
        attributeMosaic.removeAllViews();
        // Run search
        searchMasterData(selectedTab, "");
*/
    }
}
