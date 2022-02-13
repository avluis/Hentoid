package me.devsaki.hentoid.json;

import java.util.Map;

public class JsonSiteSettings {

    public Map<String, JsonSite> sites;

    public static class JsonSite {
        public Boolean useMobileAgent = null;
        public Boolean useHentoidAgent = null;
        public Boolean useWebviewAgent = null;
        public Boolean hasImageProcessing = null;
        public Boolean hasBackupURLs = null;
        public Boolean hasCoverBasedPageUpdates = null;
        public Boolean useCloudflare = null;
        public Integer parallelDownloadCap = null;
        public Integer requestsCapPerSecond = null;
    }
}
