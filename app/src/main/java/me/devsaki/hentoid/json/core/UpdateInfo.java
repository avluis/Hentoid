package me.devsaki.hentoid.json.core;

import com.squareup.moshi.Json;

import java.util.List;

import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;

/**
 * Structure to describe Hentoid update informations
 * Original source is stored on app/update.json
 */
public class UpdateInfo {

    @Json(name = "updateURL")
    private String updateUrl;
    private int versionCode;
    @Json(name = "updateURL.beta")
    private String updateUrlDebug;
    @Json(name = "versionCode.beta")
    private int versionCodeDebug;
    @Json(name = "sourceAlerts")
    private List<SourceAlert> sourceAlerts;
    @Json(name = "sourceAlerts.beta")
    private List<SourceAlert> sourceAlertsDebug;


    public String getUpdateUrl(boolean isDebug) {
        return isDebug ? updateUrlDebug : updateUrl;
    }

    public int getVersionCode(boolean isDebug) {
        return isDebug ? versionCodeDebug : versionCode;
    }

    public List<SourceAlert> getSourceAlerts(boolean isDebug) {
        return isDebug ? sourceAlertsDebug : sourceAlerts;
    }


    public static class SourceAlert {
        private String sourceName;
        private String status;
        private String fixedByBuild;

        public Site getSite() {
            return Site.searchByName(sourceName);
        }

        public AlertStatus getStatus() {
            return AlertStatus.Companion.searchByName(status);
        }

        public int getFixedByBuild() {
            if (null == fixedByBuild || fixedByBuild.isEmpty()) return Integer.MAX_VALUE;
            else return Integer.parseInt(fixedByBuild);
        }
    }
}
