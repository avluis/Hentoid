package me.devsaki.hentoid.events

class CommunicationEvent(val type: Int, val recipient: Int, val message: String = "") {
    companion object {
        const val EV_SEARCH = 1
        const val EV_ADVANCED_SEARCH = 2
        const val EV_UPDATE_TOOLBAR = 4
        const val EV_CLOSED = 5
        const val EV_ENABLE = 6
        const val EV_DISABLE = 7
        const val EV_BROADCAST = 8
        const val EV_UPDATE_EDIT_MODE = 9
        const val EV_SCROLL_TOP = 10

        const val RC_ALL = 0
        const val RC_GROUPS = 1
        const val RC_CONTENTS = 2
        const val RC_DRAWER = 3
        const val RC_DUPLICATE_MAIN = 4
        const val RC_DUPLICATE_DETAILS = 5
        const val RC_PREFS = 6
    }
}