package me.devsaki.hentoid.model;

/**
 * Prescriptive object that represents a choice chip for use with SearchBottomSheetFragment. This is
 * only an example.
 * <p>
 * Should be immutable. In case that a Choice should change due to a user interaction, the ViewModel
 * should be notified of the user interaction, create a new Choice that represents it's new state,
 * then notify the view about the change in the viewmodel's state.
 */
public class Choice {

    /**
     * the string that is displayed to the user
     */
    private String label;

    /**
     * may be interpreted by the view as a view that is not clickable or may have a different
     * appearance.
     */
    private boolean isEnabled;

    /**
     * may be interpreted by the view as a view that is not clickable or may have a different
     * appearance.
     */
    private boolean isSelected;

    public Choice(String label, boolean isEnabled, boolean isSelected) {
        this.label = label;
        this.isEnabled = isEnabled;
        this.isSelected = isSelected;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isSelected() {
        return isSelected;
    }
}
