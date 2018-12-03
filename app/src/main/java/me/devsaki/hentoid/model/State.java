package me.devsaki.hentoid.model;

import java.util.Collections;
import java.util.List;

/**
 * Prescriptive object that represents the state of a ViewModel for use with
 * SearchBottomSheetFragment. This is only an example.
 * <p>
 * This should be immutable. In case that this should change due to a user interaction, the
 * ViewModel should be notified of the user interaction, create a new State that represents it's new
 * state, then notify the view about the change in the viewmodel's state.
 */
public class State {

    /** all is well */
    public static final int STATUS_SUCCESS = 0;

    /**
     * the view may interpret this as to show a loading indicator
     */
    public static final int STATUS_LOADING = 1;

    /**
     * may be used as a default error status. the view may interpret this as a generic error and
     * display a generic error message
     */
    public static final int STATUS_ERROR_1 = 2;

    /**
     * may be used for other error state that the view may be interested in. in case that a
     * different display message should be used for different types of errors
     */
    public static final int STATUS_ERROR_2 = 3;

    /**
     * some error states may be interpreted by the view as a recoverable error that may offer a
     * 'retry' affordance
     */
    public static final int STATUS_ERROR_3 = 4;

    private final int status;

    private final List<Choice> choices;

    public State(int status, List<Choice> choices) {
        this.status = status;
        this.choices = Collections.unmodifiableList(choices);
    }

    /**
     * It is up to the ViewModel to declare the status while it is up to the View to interpret how
     * to display that status such as what error message to display, or what layout to use
     * <p>
     * Should be one of the ff: {@link #STATUS_SUCCESS}, {@link #STATUS_LOADING}, {@link
     * #STATUS_ERROR_1}, {@link #STATUS_ERROR_2}, {@link #STATUS_ERROR_3}
     */
    public int getStatus() {
        return status;
    }

    /**
     * The view may choose to call this depending on the value of {@link #status}
     *
     * @return an immutable list of choices which each represent a choice for the user to select
     */
    public List<Choice> getChoices() {
        return choices;
    }
}
