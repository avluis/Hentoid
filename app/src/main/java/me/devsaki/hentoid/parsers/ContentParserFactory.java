package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ASMHentaiContent;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.DoujinsContent;
import me.devsaki.hentoid.parsers.content.DummyContent;
import me.devsaki.hentoid.parsers.content.EhentaiContent;
import me.devsaki.hentoid.parsers.content.ExhentaiContent;
import me.devsaki.hentoid.parsers.content.HbrowseContent;
import me.devsaki.hentoid.parsers.content.Hentai2ReadContent;
import me.devsaki.hentoid.parsers.content.HentaiCafeContent;
import me.devsaki.hentoid.parsers.content.HentaifoxContent;
import me.devsaki.hentoid.parsers.content.HitomiContent;
import me.devsaki.hentoid.parsers.content.ImhentaiContent;
import me.devsaki.hentoid.parsers.content.LusciousContent;
import me.devsaki.hentoid.parsers.content.ManhwaContent;
import me.devsaki.hentoid.parsers.content.MrmContent;
import me.devsaki.hentoid.parsers.content.MusesContent;
import me.devsaki.hentoid.parsers.content.NhentaiContent;
import me.devsaki.hentoid.parsers.content.PorncomixContent;
import me.devsaki.hentoid.parsers.content.PururinContent;
import me.devsaki.hentoid.parsers.content.ToonilyContent;
import me.devsaki.hentoid.parsers.content.TsuminoContent;
import me.devsaki.hentoid.parsers.images.ASMHentaiParser;
import me.devsaki.hentoid.parsers.images.DoujinsParser;
import me.devsaki.hentoid.parsers.images.DummyParser;
import me.devsaki.hentoid.parsers.images.EHentaiParser;
import me.devsaki.hentoid.parsers.images.ExHentaiParser;
import me.devsaki.hentoid.parsers.images.HbrowseParser;
import me.devsaki.hentoid.parsers.images.Hentai2ReadParser;
import me.devsaki.hentoid.parsers.images.HentaiCafeParser;
import me.devsaki.hentoid.parsers.images.HentaifoxParser;
import me.devsaki.hentoid.parsers.images.HitomiParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.parsers.images.ImhentaiParser;
import me.devsaki.hentoid.parsers.images.LusciousParser;
import me.devsaki.hentoid.parsers.images.ManhwaParser;
import me.devsaki.hentoid.parsers.images.MrmParser;
import me.devsaki.hentoid.parsers.images.MusesParser;
//import me.devsaki.hentoid.parsers.images.NexusParser2;
import me.devsaki.hentoid.parsers.images.NhentaiParser;
import me.devsaki.hentoid.parsers.images.PorncomixParser;
import me.devsaki.hentoid.parsers.images.PururinParser;
import me.devsaki.hentoid.parsers.images.ToonilyParser;
import me.devsaki.hentoid.parsers.images.TsuminoParser;

public class ContentParserFactory {

    private static final ContentParserFactory mInstance = new ContentParserFactory();

    private ContentParserFactory() {
    }

    public static ContentParserFactory getInstance() {
        return mInstance;
    }


    public Class<? extends ContentParser> getContentParserClass(Site site) {
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
            //case NEXUS:
            //    return NexusContent.class;
            case MUSES:
                return MusesContent.class;
            case DOUJINS:
                return DoujinsContent.class;
            case PORNCOMIX:
                return PorncomixContent.class;
            case HBROWSE:
                return HbrowseContent.class;
            case HENTAI2READ:
                return Hentai2ReadContent.class;
            case HENTAIFOX:
                return HentaifoxContent.class;
            case MRM:
                return MrmContent.class;
            case MANHWA:
                return ManhwaContent.class;
            case IMHENTAI:
                return ImhentaiContent.class;
            case EHENTAI:
                return EhentaiContent.class;
            case EXHENTAI:
                return ExhentaiContent.class;
            case LUSCIOUS:
                return LusciousContent.class;
            case TOONILY:
                return ToonilyContent.class;
            default:
                return DummyContent.class;
        }
    }

    public ImageListParser getImageListParser(Content content) {
        return (null == content) ? new DummyParser() : getImageListParser(content.getSite());
    }

    public ImageListParser getImageListParser(Site site) {
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
            case EXHENTAI:
                return new ExHentaiParser();
            //case NEXUS:
            //    return new NexusParser2();
            case LUSCIOUS:
                return new LusciousParser();
            case PORNCOMIX:
                return new PorncomixParser();
            case MUSES:
                return new MusesParser();
            case NHENTAI:
                return new NhentaiParser();
            case DOUJINS:
                return new DoujinsParser();
            case HBROWSE:
                return new HbrowseParser();
            case HENTAI2READ:
                return new Hentai2ReadParser();
            case HENTAIFOX:
                return new HentaifoxParser();
            case MRM:
                return new MrmParser();
            case MANHWA:
                return new ManhwaParser();
            case IMHENTAI:
                return new ImhentaiParser();
            case TOONILY:
                return new ToonilyParser();
            default:
                return new DummyParser();
        }
    }
}
