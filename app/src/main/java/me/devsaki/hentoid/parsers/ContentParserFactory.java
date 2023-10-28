package me.devsaki.hentoid.parsers;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ASMHentaiContent;
import me.devsaki.hentoid.parsers.content.AllPornComicContent;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.DoujinsContent;
import me.devsaki.hentoid.parsers.content.DummyContent;
import me.devsaki.hentoid.parsers.content.EdoujinContent;
import me.devsaki.hentoid.parsers.content.EhentaiContent;
import me.devsaki.hentoid.parsers.content.ExhentaiContent;
import me.devsaki.hentoid.parsers.content.HbrowseContent;
import me.devsaki.hentoid.parsers.content.HdPornComicsContent;
import me.devsaki.hentoid.parsers.content.Hentai2ReadContent;
import me.devsaki.hentoid.parsers.content.HentaifoxContent;
import me.devsaki.hentoid.parsers.content.HitomiContent;
import me.devsaki.hentoid.parsers.content.ImhentaiContent;
import me.devsaki.hentoid.parsers.content.LusciousContent;
import me.devsaki.hentoid.parsers.content.Manhwa18Content;
import me.devsaki.hentoid.parsers.content.ManhwaContent;
import me.devsaki.hentoid.parsers.content.MrmContent;
import me.devsaki.hentoid.parsers.content.MultpornContent;
import me.devsaki.hentoid.parsers.content.MusesContent;
import me.devsaki.hentoid.parsers.content.NhentaiContent;
import me.devsaki.hentoid.parsers.content.PixivContent;
import me.devsaki.hentoid.parsers.content.PorncomixContent;
import me.devsaki.hentoid.parsers.content.PururinContent;
import me.devsaki.hentoid.parsers.content.SimplyContent;
import me.devsaki.hentoid.parsers.content.ToonilyContent;
import me.devsaki.hentoid.parsers.content.TsuminoContent;
import me.devsaki.hentoid.parsers.images.ASMHentaiParser;
import me.devsaki.hentoid.parsers.images.AllPornComicParser;
import me.devsaki.hentoid.parsers.images.DoujinsParser;
import me.devsaki.hentoid.parsers.images.DummyParser;
import me.devsaki.hentoid.parsers.images.EHentaiParser;
import me.devsaki.hentoid.parsers.images.EdoujinParser;
import me.devsaki.hentoid.parsers.images.ExHentaiParser;
import me.devsaki.hentoid.parsers.images.HbrowseParser;
import me.devsaki.hentoid.parsers.images.HdPornComicsParser;
import me.devsaki.hentoid.parsers.images.Hentai2ReadParser;
import me.devsaki.hentoid.parsers.images.HentaifoxParser;
import me.devsaki.hentoid.parsers.images.HitomiParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.parsers.images.ImhentaiParser;
import me.devsaki.hentoid.parsers.images.LusciousParser;
import me.devsaki.hentoid.parsers.images.Manhwa18Parser;
import me.devsaki.hentoid.parsers.images.ManhwaParser;
import me.devsaki.hentoid.parsers.images.MrmParser;
import me.devsaki.hentoid.parsers.images.MultpornParser;
import me.devsaki.hentoid.parsers.images.MusesParser;
import me.devsaki.hentoid.parsers.images.NhentaiParser;
import me.devsaki.hentoid.parsers.images.PixivParser;
import me.devsaki.hentoid.parsers.images.PorncomixParser;
import me.devsaki.hentoid.parsers.images.PururinParser;
import me.devsaki.hentoid.parsers.images.SimplyParser;
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
        return switch (site) {
            case NHENTAI -> NhentaiContent.class;
            case ASMHENTAI, ASMHENTAI_COMICS -> ASMHentaiContent.class;
            case HITOMI -> HitomiContent.class;
            case TSUMINO -> TsuminoContent.class;
            case PURURIN -> PururinContent.class;
            case MUSES -> MusesContent.class;
            case DOUJINS -> DoujinsContent.class;
            case PORNCOMIX -> PorncomixContent.class;
            case HBROWSE -> HbrowseContent.class;
            case HENTAI2READ -> Hentai2ReadContent.class;
            case HENTAIFOX -> HentaifoxContent.class;
            case MRM -> MrmContent.class;
            case MANHWA -> ManhwaContent.class;
            case IMHENTAI -> ImhentaiContent.class;
            case EHENTAI -> EhentaiContent.class;
            case EXHENTAI -> ExhentaiContent.class;
            case LUSCIOUS -> LusciousContent.class;
            case TOONILY -> ToonilyContent.class;
            case ALLPORNCOMIC -> AllPornComicContent.class;
            case PIXIV -> PixivContent.class;
            case MANHWA18 -> Manhwa18Content.class;
            case MULTPORN -> MultpornContent.class;
            case SIMPLY -> SimplyContent.class;
            case HDPORNCOMICS -> HdPornComicsContent.class;
            case EDOUJIN -> EdoujinContent.class;
            default -> DummyContent.class;
        };
    }

    public ImageListParser getImageListParser(Content content) {
        return (null == content) ? new DummyParser() : getImageListParser(content.getSite());
    }

    public ImageListParser getImageListParser(Site site) {
        return switch (site) {
            case ASMHENTAI, ASMHENTAI_COMICS -> new ASMHentaiParser();
            case HITOMI -> new HitomiParser();
            case TSUMINO -> new TsuminoParser();
            case PURURIN -> new PururinParser();
            case EHENTAI -> new EHentaiParser();
            case EXHENTAI -> new ExHentaiParser();
            case LUSCIOUS -> new LusciousParser();
            case PORNCOMIX -> new PorncomixParser();
            case MUSES -> new MusesParser();
            case NHENTAI -> new NhentaiParser();
            case DOUJINS -> new DoujinsParser();
            case HBROWSE -> new HbrowseParser();
            case HENTAI2READ -> new Hentai2ReadParser();
            case HENTAIFOX -> new HentaifoxParser();
            case MRM -> new MrmParser();
            case MANHWA -> new ManhwaParser();
            case IMHENTAI -> new ImhentaiParser();
            case TOONILY -> new ToonilyParser();
            case ALLPORNCOMIC -> new AllPornComicParser();
            case PIXIV -> new PixivParser();
            case MANHWA18 -> new Manhwa18Parser();
            case MULTPORN -> new MultpornParser();
            case SIMPLY -> new SimplyParser();
            case HDPORNCOMICS -> new HdPornComicsParser();
            case EDOUJIN -> new EdoujinParser();
            default -> new DummyParser();
        };
    }
}
