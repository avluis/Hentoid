package me.devsaki.hentoid.events

class CommunicationEvent(val type: Type, val recipient: Recipient = Recipient.ALL, val message: String = "") {

    enum class Type {
        SEARCH,
        ADVANCED_SEARCH,
        UPDATE_TOOLBAR,
        CLOSE_DRAWER,
        CLOSED,
        ENABLE,
        DISABLE,
        UNSELECT,
        BROADCAST,
        UPDATE_EDIT_MODE,
        SCROLL_TOP,
        SIGNAL_SITE
    }

    enum class Recipient {
        ALL,
        GROUPS,
        CONTENTS,
        FOLDERS,
        QUEUE,
        ERRORS,
        DRAWER,
        DUPLICATE_MAIN,
        DUPLICATE_DETAILS,
        PREFS
    }
}