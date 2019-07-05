package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ASMHentaiContent;
import me.devsaki.hentoid.parsers.content.DummyContent;
import me.devsaki.hentoid.parsers.content.FakkuContent;
import me.devsaki.hentoid.parsers.content.HentaiCafeContent;
import me.devsaki.hentoid.parsers.content.HitomiContent;
import me.devsaki.hentoid.parsers.content.NexusContent;
import me.devsaki.hentoid.parsers.content.NhentaiContent;
import me.devsaki.hentoid.parsers.content.PururinContent;
import me.devsaki.hentoid.parsers.content.TsuminoContent;

public class ContentParserFactory {

    private static final ContentParserFactory mInstance = new ContentParserFactory();

    private ContentParserFactory() {
    }

    public static ContentParserFactory getInstance() {
        return mInstance;
    }

    public ImageListParser getImageListParser(Content content) {
        return (null == content) ? new DummyParser() : getImageListParser(content.getSite());
    }

    private ImageListParser getImageListParser(Site site) {
        switch (site) {
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
                return new ASMHentaiParser();
            case HENTAICAFE:
                return new HentaiCafeParser();
            case HITOMI:
                return new HitomiParser();
            case TSUMINO:
                return new TsuminoParser();
            case PURURIN:
                return new PururinParser();
            case EHENTAI:
                return new EHentaiParser();
            case FAKKU2:
                return new FakkuParser();
            case NEXUS:
                return new NexusParser();
            default:
                return new DummyParser();
        }
    }

    public Class getContentClass(Site site) {
        switch (site) {
            case NHENTAI:
                return NhentaiContent.class;
            case ASMHENTAI:
            case ASMHENTAI_COMICS:
                return ASMHentaiContent.class;
            case HENTAICAFE:
                return HentaiCafeContent.class;
            case HITOMI:
                return HitomiContent.class;
            case TSUMINO:
                return TsuminoContent.class;
            case PURURIN:
                return PururinContent.class;
            case FAKKU2:
                return FakkuContent.class;
            case NEXUS:
                return NexusContent.class;
            case EHENTAI: // E-H uses the API of the site -> no HTML parser
            default:
                return DummyContent.class;
        }
    }
}
