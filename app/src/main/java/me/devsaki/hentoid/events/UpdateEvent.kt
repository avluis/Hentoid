package me.devsaki.hentoid.events

import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.core.UpdateInfo

class UpdateEvent(val hasNewVersion: Boolean, sourceAlerts: List<UpdateInfo.SourceAlert>) {
    val sourceAlerts = HashMap<Site, UpdateInfo.SourceAlert>()

    init {
        for (alert in sourceAlerts) this.sourceAlerts[alert.getSite()] = alert
    }
}