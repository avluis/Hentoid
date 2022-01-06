package me.devsaki.hentoid.parsers;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;

public class ParseHelper {

    private ParseHelper() {
        throw new IllegalStateException("Utility class");
    }

    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern SQUARE_BRACKETS = Pattern.compile("\\[[^]]*\\]");

    /**
     * Remove counters from given string (e.g. "Futanari (2660)" => "Futanari")
     *
     * @param s String to clean up
     * @return String with removed brackets
     */
    public static String removeBrackets(String s) {
        if (null == s || s.isEmpty()) return "";

        int bracketPos = s.lastIndexOf('(');
        if (bracketPos > 1 && ' ' == s.charAt(bracketPos - 1)) bracketPos--;
        if (bracketPos > -1) {
            return s.substring(0, bracketPos);
        }

        return s;
    }

    /**
     * Remove all terms between square brackets that are used
     * to "tag" book titles
     * (e.g. "[Author] Title [English] [Digital]" => "Title")
     *
     * @param s String to clean up
     * @return String with removed terms
     */
    public static String removeTextualTags(String s) {
        if (null == s || s.isEmpty()) return "";

        Matcher m = SQUARE_BRACKETS.matcher(s);
        return m.replaceAll("").replace("  ", " ").trim();
    }

    /**
     * Remove trailing numbers from given string (e.g. "Futanari 2660" => "Futanari")
     * Only works when the numbers come after a space, so that tags ending with numbers
     * are not altered (e.g. "circle64")
     *
     * @param s String to clean up
     * @return String with removed trailing numbers
     */
    public static String removeTrailingNumbers(String s) {
        if (null == s || s.isEmpty()) return "";

        String[] parts = s.split(" ");
        if (parts.length > 1 && StringHelper.isNumeric(parts[parts.length - 1])) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) sb.append(parts[i]).append(" ");
            return sb.toString().trim();
        }

        return s;
    }

    public static void parseAttributes(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            List<Element> elements,
            boolean removeTrailingNumbers,
            @NonNull Site site) {
        if (elements != null)
            for (Element a : elements) parseAttribute(map, type, a, removeTrailingNumbers, site);
    }

    public static void parseAttributes(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            List<Element> elements,
            boolean removeTrailingNumbers,
            @NonNull String childElementClass,
            @NonNull Site site) {
        if (elements != null)
            for (Element a : elements)
                parseAttribute(map, type, a, removeTrailingNumbers, childElementClass, site);
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean removeTrailingNumbers,
            @NonNull Site site) {
        parseAttribute(map, type, element, removeTrailingNumbers, null, site, "");
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean removeTrailingNumbers,
            @NonNull String childElementClass,
            @NonNull Site site) {
        parseAttribute(map, type, element, removeTrailingNumbers, childElementClass, site, "");
    }

    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean removeTrailingNumbers,
            @Nullable String childElementClass,
            @NonNull Site site,
            @NonNull final String prefix) {
        String name;
        if (null == childElementClass) {
            name = element.ownText();
        } else {
            Element e = element.selectFirst("." + childElementClass);
            if (e != null) name = e.ownText();
            else name = "";
        }
        name = StringHelper.removeNonPrintableChars(name);
        name = removeBrackets(name);
        if (removeTrailingNumbers) name = removeTrailingNumbers(name);
        if (name.isEmpty() || name.equals("-") || name.equals("/")) return;

        if (!prefix.isEmpty()) name = prefix + ":" + name;
        Attribute attribute = new Attribute(type, name, element.attr("href"), site);

        map.add(attribute);
    }

    public static ImageFile urlToImageFile(
            @Nonnull String imgUrl,
            int order,
            int nbPages,
            @NonNull final StatusContent status) {
        return urlToImageFile(imgUrl, order, nbPages, status, null);
    }

    public static ImageFile urlToImageFile(
            @Nonnull String imgUrl,
            int order,
            int maxPages,
            @NonNull final StatusContent status,
            final Chapter chapter) {
        ImageFile result = new ImageFile();

        int nbMaxDigits = (int) (Math.floor(Math.log10(maxPages)) + 1);
        result.setOrder(order).setUrl(imgUrl).setStatus(status).computeName(nbMaxDigits);
        if (chapter != null) result.setChapter(chapter);

        return result;
    }

    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull String coverUrl,
            @NonNull final StatusContent status
    ) {
        return urlsToImageFiles(imgUrls, coverUrl, status, null);
    }

    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull String coverUrl,
            @NonNull final StatusContent status,
            final Chapter chapter
    ) {
        List<ImageFile> result = new ArrayList<>();

        result.add(ImageFile.newCover(coverUrl, status));
        result.addAll(urlsToImageFiles(imgUrls, 1, status, chapter, imgUrls.size()));

        return result;
    }

    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            int initialOrder,
            @NonNull final StatusContent status,
            final Chapter chapter,
            int maxPages
    ) {
        List<ImageFile> result = new ArrayList<>();

        int order = initialOrder;
        // Remove duplicates before creationg the ImageFiles
        List<String> imgUrlsUnique = Stream.of(imgUrls).distinct().toList();
        for (String s : imgUrlsUnique)
            result.add(urlToImageFile(s.trim(), order++, maxPages, status, chapter));

        return result;
    }


    public static void signalProgress(long contentId, long storedId, int current, int max) {
        EventBus.getDefault().post(new DownloadPreparationEvent(contentId, storedId, current, max));
    }

    public static String getSavedCookieStr(String downloadParams) {
        Map<String, String> downloadParamsMap = ContentHelper.parseDownloadParams(downloadParams);
        if (downloadParamsMap.containsKey(HttpHelper.HEADER_COOKIE_KEY))
            return StringHelper.protect(downloadParamsMap.get(HttpHelper.HEADER_COOKIE_KEY));

        return "";
    }

    public static void addSavedCookiesToHeader(String downloadParams, @NonNull List<Pair<String, String>> headers) {
        String cookieStr = getSavedCookieStr(downloadParams);
        if (!cookieStr.isEmpty())
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
    }

    // Save download params for future use during download
    public static void setDownloadParams(@NonNull final List<ImageFile> imgs, @NonNull final String referrer) {
        Map<String, String> params = new HashMap<>();
        for (ImageFile img : imgs) {
            params.clear();
            String cookieStr = HttpHelper.getCookies(img.getUrl());
            if (!cookieStr.isEmpty()) params.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);
            params.put(HttpHelper.HEADER_REFERER_KEY, referrer);
            img.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
        }
    }

    // TODO doc
    public static String getExtensionFromFormat(Map<String, String> imgFormat, int i) {
        String format = imgFormat.get((i + 1) + "");
        if (format != null) {
            switch (format.charAt(0)) {
                case 'p':
                    return "png";
                case 'g':
                    return "gif";
                case 'j':
                default:
                    return "jpg";
            }

        } else return "";
    }

    public static List<Chapter> getChaptersFromLinks(@NonNull List<Element> chapterLinks, long contentId) {
        List<Chapter> result = new ArrayList<>();
        Set<String> urls = new HashSet<>();

        int order = 0;
        for (Element e : chapterLinks) {
            String url = e.attr("href");
            String name = StringHelper.removeNonPrintableChars(e.ownText());
            // Make sure we're not adding duplicates
            if (!urls.contains(url)) {
                urls.add(url);
                Chapter chp = new Chapter(order++, url, name);
                chp.setContentId(contentId);
                result.add(chp);
            }
        }

        return result;
    }

    private static String getLastPathPart(@NonNull final String url) {
        String[] parts = url.split("/");
        return (parts[parts.length - 1].isEmpty()) ? parts[parts.length - 2] : parts[parts.length - 1];
    }

    public static List<Chapter> getExtraChaptersbyUrl(
            @NonNull List<Chapter> storedChapters,
            @NonNull List<Chapter> detectedChapters
    ) {
        List<Chapter> result = new ArrayList<>();
        Map<String, List<Chapter>> storedChps = Stream.of(storedChapters).collect(Collectors.groupingBy(c -> getLastPathPart(c.getUrl())));
        Map<String, List<Chapter>> detectedChps = Stream.of(detectedChapters).collect(Collectors.groupingBy(c -> getLastPathPart(c.getUrl())));

        if (null == storedChps || null == detectedChps) return result;

        Set<String> storedUrlParts = storedChps.keySet();
        for (Map.Entry<String, List<Chapter>> detectedEntry : detectedChps.entrySet()) {
            if (!storedUrlParts.contains(detectedEntry.getKey())) {
                List<Chapter> chps = detectedEntry.getValue();
                if (!chps.isEmpty()) result.add(chps.get(0));
            }
        }
        return Stream.of(result).sortBy(Chapter::getOrder).toList();
    }

    public static List<String> getExtraChaptersbyId(
            @NonNull List<Chapter> storedChapters,
            @NonNull List<String> detectedIds
    ) {
        List<String> result = new ArrayList<>();
        Set<String> storedIds = new HashSet<>();
        for (Chapter c : storedChapters) storedIds.add(c.getUniqueId());

        for (String detectedId : detectedIds) {
            if (!storedIds.contains(detectedId)) {
                result.add(detectedId);
            }
        }
        return result;
    }

    public static int getMaxImageOrder(@NonNull List<Chapter> storedChapters) {
        if (!storedChapters.isEmpty()) {
            Optional<Integer> optOrder = Stream.of(storedChapters)
                    .map(Chapter::getImageFiles)
                    .withoutNulls()
                    .flatMap(Stream::of)
                    .map(ImageFile::getOrder)
                    .max(Integer::compareTo);
            if (optOrder.isPresent()) return optOrder.get();
        }
        return 0;
    }

    public static int getMaxChapterOrder(@NonNull List<Chapter> storedChapters) {
        if (!storedChapters.isEmpty()) {
            Optional<Integer> optOrder = Stream.of(storedChapters)
                    .withoutNulls()
                    .map(Chapter::getOrder)
                    .max(Integer::compareTo);
            if (optOrder.isPresent()) return optOrder.get();
        }
        return 0;
    }

    public static String getImgSrc(Element e) {
        String result = e.attr("data-src").trim();
        if (result.isEmpty()) result = e.attr("src").trim();
        if (result.isEmpty()) result = e.attr("data-cfsrc").trim(); // Cloudflare-served image
        return result;
    }
}
