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
import java.util.Collections;
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

    /**
     * See definition of the main method below
     */
    public static void parseAttributes(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            List<Element> elements,
            boolean removeTrailingNumbers,
            @NonNull Site site) {
        if (elements != null)
            for (Element a : elements) parseAttribute(map, type, a, removeTrailingNumbers, site);
    }

    /**
     * See definition of the main method below
     */
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

    /**
     * See definition of the main method below
     */
    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean removeTrailingNumbers,
            @NonNull Site site) {
        parseAttribute(element, map, type, site, "", removeTrailingNumbers, null);
    }

    /**
     * See definition of the main method below
     */
    public static void parseAttribute(
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Element element,
            boolean removeTrailingNumbers,
            @NonNull String childElementClass,
            @NonNull Site site) {
        parseAttribute(element, map, type, site, "", removeTrailingNumbers, childElementClass);
    }

    /**
     * Extract Attributes from the given Element and put them into the given AttributeMap,
     * using the given properties
     *
     * @param element               Element to parse Attributes from
     * @param map                   Output map where the detected attributes will be put
     * @param type                  AttributeType to give to the detected Attributes
     * @param site                  Site to give to the detected Attributes
     * @param prefix                If set, detected attributes will have this prefix added to their name
     * @param removeTrailingNumbers If true trailing numbers will be removed from the attribute name
     * @param childElementClass     If set, the parser will look for sub-elements of the given class
     */
    public static void parseAttribute(
            @NonNull Element element, @NonNull AttributeMap map,
            @NonNull AttributeType type,
            @NonNull Site site, @NonNull final String prefix, boolean removeTrailingNumbers,
            @Nullable String childElementClass) {
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

    /**
     * See definition of the main method below
     */
    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull String coverUrl,
            @NonNull final StatusContent status
    ) {
        return urlsToImageFiles(imgUrls, coverUrl, status, null);
    }

    /**
     * See definition of the main method below
     */
    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            @NonNull String coverUrl,
            @NonNull final StatusContent status,
            final Chapter chapter
    ) {
        List<ImageFile> result = new ArrayList<>();

        result.add(ImageFile.newCover(coverUrl, status));
        result.addAll(urlsToImageFiles(imgUrls, 1, status, imgUrls.size(), chapter));

        return result;
    }

    /**
     * Build a list of ImageFiles using the given properties
     *
     * @param imgUrls        URLs of the images
     * @param initialOrder   Order of the 1st image to be generated
     * @param status         Status of the resulting ImageFiles
     * @param totalBookPages Total number of pages of the corresponding book
     * @param chapter        Chapter to link to the resulting ImageFiles (optional)
     * @return List of ImageFiles built using all given arguments
     */
    public static List<ImageFile> urlsToImageFiles(
            @Nonnull List<String> imgUrls,
            int initialOrder,
            @NonNull final StatusContent status,
            int totalBookPages, final Chapter chapter
    ) {
        List<ImageFile> result = new ArrayList<>();

        int order = initialOrder;
        // Remove duplicates before creationg the ImageFiles
        List<String> imgUrlsUnique = Stream.of(imgUrls).distinct().toList();
        for (String s : imgUrlsUnique)
            result.add(urlToImageFile(s.trim(), order++, totalBookPages, status, chapter));

        return result;
    }

    /**
     * Build an ImageFile using the given properties
     *
     * @param imgUrl         URL of the image
     * @param order          Order of the image
     * @param totalBookPages Total number of pages of the corresponding book
     * @param status         Status of the resulting ImageFile
     * @return ImageFile built using all given arguments
     */
    public static ImageFile urlToImageFile(
            @Nonnull String imgUrl,
            int order,
            int totalBookPages,
            @NonNull final StatusContent status) {
        return urlToImageFile(imgUrl, order, totalBookPages, status, null);
    }

    /**
     * Build an ImageFile using the given given properties
     *
     * @param imgUrl         URL of the image
     * @param order          Order of the image
     * @param totalBookPages Total number of pages of the corresponding book
     * @param status         Status of the resulting ImageFile
     * @param chapter        Chapter to link to the resulting ImageFile (optional)
     * @return ImageFile built using all given arguments
     */
    public static ImageFile urlToImageFile(
            @Nonnull String imgUrl,
            int order,
            int totalBookPages,
            @NonNull final StatusContent status,
            final Chapter chapter) {
        ImageFile result = new ImageFile();

        int nbMaxDigits = (int) (Math.floor(Math.log10(totalBookPages)) + 1);
        result.setOrder(order).setUrl(imgUrl).setStatus(status).computeName(nbMaxDigits);
        if (chapter != null) result.setChapter(chapter);

        return result;
    }

    /**
     * Signal download preparation event for the given processed elements
     *
     * @param contentId   Online content ID being processed
     * @param storedId    Stored content ID being processed
     * @param currentStep Current processing step
     * @param maxSteps    Maximum processing step
     */
    public static void signalProgress(long contentId, long storedId, int currentStep, int maxSteps) {
        EventBus.getDefault().post(new DownloadPreparationEvent(contentId, storedId, currentStep, maxSteps));
    }

    /**
     * Extract the cookie string, if it exists, from the given download parameters
     *
     * @param downloadParams Download parameters to extract the cookie string from
     * @return Cookie string, if any in the given download parameters; empty string if none
     */
    public static String getSavedCookieStr(String downloadParams) {
        Map<String, String> downloadParamsMap = ContentHelper.parseDownloadParams(downloadParams);
        if (downloadParamsMap.containsKey(HttpHelper.HEADER_COOKIE_KEY))
            return StringHelper.protect(downloadParamsMap.get(HttpHelper.HEADER_COOKIE_KEY));

        return "";
    }

    /**
     * Copy the cookie string, if it exists, from the given download parameters to the given HTTP headers
     *
     * @param downloadParams Download parameters to extract the cookie string from
     * @param headers        HTTP headers to copy the cookie string to, if it exists
     */
    public static void addSavedCookiesToHeader(String downloadParams, @NonNull List<Pair<String, String>> headers) {
        String cookieStr = getSavedCookieStr(downloadParams);
        if (!cookieStr.isEmpty())
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
    }

    /**
     * Save the given referrer and the relevant cookie string as download parameters
     * to each image of the given list for future use during download
     *
     * @param imgs     List of images to save download params to
     * @param referrer Referrer to set
     */
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

    /**
     * Get the image extension from the given ImHentai / Hentaifox format code
     *
     * @param imgFormat Format map provided by the site
     * @param i         index to look up
     * @return Image extension (without the dot), if found; empty string if not
     */
    public static String getExtensionFromFormat(@NonNull Map<String, String> imgFormat, int i) {
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

    /**
     * Extract a list of Chapters from the given list of links, for the given Content ID
     *
     * @param chapterLinks List of HTML links to extract Chapters from
     * @param contentId    Content ID to associate with all extracted Chapters
     * @return Chapters detected from the given list of links, associated with the given Content ID
     */
    public static List<Chapter> getChaptersFromLinks(@NonNull List<Element> chapterLinks, long contentId) {
        List<Chapter> result = new ArrayList<>();
        Set<String> urls = new HashSet<>();

        // First extract data and filter URL duplicates
        List<Pair<String, String>> chapterData = new ArrayList<>();
        for (Element e : chapterLinks) {
            String url = e.attr("href").trim();
            String name = e.attr("title").trim();
            if (name.isEmpty())
                name = StringHelper.removeNonPrintableChars(e.ownText()).trim();
            // Make sure we're not adding duplicates
            if (!urls.contains(url)) {
                urls.add(url);
                chapterData.add(new Pair<>(url, name));
            }
        }
        Collections.reverse(chapterData); // Put unique results in their chronological order

        int order = 0;
        // Build the final list
        for (Pair<String, String> chapter : chapterData) {
            Chapter chp = new Chapter(order++, chapter.first, chapter.second);
            chp.setContentId(contentId);
            result.add(chp);
        }

        return result;
    }

    /**
     * Extract the last useful part of the path of the given URL
     * e.g. if the url is "http://aa.com/look/at/me" or "http://aa.com/look/at/me/", the result will be "me"
     *
     * @param url URL to extract from
     * @return Last useful part of the path of the given URL
     */
    private static String getLastPathPart(@NonNull final String url) {
        String[] parts = url.split("/");
        return (parts[parts.length - 1].isEmpty()) ? parts[parts.length - 2] : parts[parts.length - 1];
    }

    // TODO doc
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

    // TODO doc
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

    // TODO doc
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

    // TODO doc
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

    /**
     * Extract the image URL from the given HTML element
     *
     * @param e HTML element to extract the URL from
     * @return Image URL contained in the given HTML element
     */
    public static String getImgSrc(Element e) {
        String result = e.attr("data-src").trim();
        if (result.isEmpty()) result = e.attr("data-lazy-src").trim();
        if (result.isEmpty()) result = e.attr("data-lazysrc").trim();
        if (result.isEmpty()) result = e.attr("src").trim();
        if (result.isEmpty()) result = e.attr("data-cfsrc").trim(); // Cloudflare-served image
        return result;
    }
}
