package me.devsaki.hentoid.events

class CommunicationEvent(val type: Type, val recipient: Recipient, val message: String = "") {

    enum class Type {
        SEARCH,
        ADVANCED_SEARCH,
        UPDATE_TOOLBAR,
        CLOSED,
        ENABLE,
        DISABLE,
        BROADCAST,
        UPDATE_EDIT_MODE,
        SCROLL_TOP
    }

    enum class Recipient {
        ALL,
        GROUPS,
        CONTENTS,
        DRAWER,
        DUPLICATE_MAIN,
        DUPLICATE_DETAILS,
        PREFS
    }
}