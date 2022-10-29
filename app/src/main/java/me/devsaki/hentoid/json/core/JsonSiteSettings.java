package me.devsaki.hentoid.json.core;

import java.util.List;
import java.util.Map;

public class JsonSiteSettings {

    public Map<String, JsonSite> sites;

    public static class JsonSite {
        public Boolean useMobileAgent = null;
        public Boolean useHentoidAgent = null;
        public Boolean useWebviewAgent = null;
        public Boolean hasBackupURLs = null;
        public Boolean hasCoverBasedPageUpdates = null;
        public Boolean useCloudflare = null;
        public Integer parallelDownloadCap = null;
        public Integer requestsCapPerSecond = null;
        public Integer bookCardDepth = null;
        public List<String> bookCardExcludedParentClasses = null;
    }
}
