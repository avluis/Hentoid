package me.devsaki.hentoid.events

import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.core.UpdateInfo.SourceAlert

class UpdateEvent(val hasNewVersion: Boolean, sourceAlerts: List<SourceAlert>) {
    val sourceAlerts = HashMap<Site, SourceAlert>()

    init {
        for (alert in sourceAlerts) this.sourceAlerts[alert.site] = alert
    }
}