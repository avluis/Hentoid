package me.devsaki.hentoid.events;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.core.UpdateInfo;

public class UpdateEvent {
    public final boolean hasNewVersion;
    public final Map<Site, UpdateInfo.SourceAlert> sourceAlerts;

    public UpdateEvent(boolean hasNewVersion, List<UpdateInfo.SourceAlert> sourceAlerts) {
        this.hasNewVersion = hasNewVersion;
        this.sourceAlerts = new EnumMap<>(Site.class);
        for (UpdateInfo.SourceAlert alert : sourceAlerts)
            this.sourceAlerts.put(alert.getSite(), alert);
    }
}
