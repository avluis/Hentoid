package me.devsaki.hentoid.json;

import com.squareup.moshi.Json;

import java.util.List;

import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;

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
        private AlertStatus status;
        private String message;

        public Site getSite() {
            return Site.valueOf(sourceName);
        }

        public AlertStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
