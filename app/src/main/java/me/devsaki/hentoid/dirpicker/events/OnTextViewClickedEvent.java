package me.devsaki.hentoid.dirpicker.events;

/**
 * Created by avluis on 08/07/2016.
 * Text View Clicked Event
 */
public class OnTextViewClickedEvent {
    private final boolean longClick;

    public OnTextViewClickedEvent(boolean longClick) {
        this.longClick = longClick;
    }

    public boolean isLongClick() {
        return longClick;
    }
}
