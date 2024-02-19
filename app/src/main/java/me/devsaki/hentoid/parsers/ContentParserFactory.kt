package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ASMHentaiContent
import me.devsaki.hentoid.parsers.content.AllPornComicContent
import me.devsaki.hentoid.parsers.content.AnchiraContent
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.DeviantArtContent
import me.devsaki.hentoid.parsers.content.DoujinsContent
import me.devsaki.hentoid.parsers.content.DummyContent
import me.devsaki.hentoid.parsers.content.EdoujinContent
import me.devsaki.hentoid.parsers.content.EhentaiContent
import me.devsaki.hentoid.parsers.content.ExhentaiContent
import me.devsaki.hentoid.parsers.content.HdPornComicsContent
import me.devsaki.hentoid.parsers.content.Hentai2ReadContent
import me.devsaki.hentoid.parsers.content.HentaifoxContent
import me.devsaki.hentoid.parsers.content.HitomiContent
import me.devsaki.hentoid.parsers.content.ImhentaiContent
import me.devsaki.hentoid.parsers.content.LusciousContent
import me.devsaki.hentoid.parsers.content.Manhwa18Content
import me.devsaki.hentoid.parsers.content.ManhwaContent
import me.devsaki.hentoid.parsers.content.MrmContent
import me.devsaki.hentoid.parsers.content.MultpornContent
import me.devsaki.hentoid.parsers.content.MusesContent
import me.devsaki.hentoid.parsers.content.NhentaiContent
import me.devsaki.hentoid.parsers.content.PixivContent
import me.devsaki.hentoid.parsers.content.PorncomixContent
import me.devsaki.hentoid.parsers.content.PururinContent
import me.devsaki.hentoid.parsers.content.SimplyContent
import me.devsaki.hentoid.parsers.content.ToonilyContent
import me.devsaki.hentoid.parsers.content.TsuminoContent
import me.devsaki.hentoid.parsers.images.ASMHentaiParser
import me.devsaki.hentoid.parsers.images.AllPornComicParser
import me.devsaki.hentoid.parsers.images.AnchiraParser
import me.devsaki.hentoid.parsers.images.DeviantArtParser
import me.devsaki.hentoid.parsers.images.DoujinsParser
import me.devsaki.hentoid.parsers.images.DummyParser
import me.devsaki.hentoid.parsers.images.EHentaiParser
import me.devsaki.hentoid.parsers.images.EdoujinParser
import me.devsaki.hentoid.parsers.images.ExHentaiParser
import me.devsaki.hentoid.parsers.images.HdPornComicsParser
import me.devsaki.hentoid.parsers.images.Hentai2ReadParser
import me.devsaki.hentoid.parsers.images.HentaifoxParser
import me.devsaki.hentoid.parsers.images.HitomiParser
import me.devsaki.hentoid.parsers.images.ImageListParser
import me.devsaki.hentoid.parsers.images.ImhentaiParser
import me.devsaki.hentoid.parsers.images.LusciousParser
import me.devsaki.hentoid.parsers.images.Manhwa18Parser
import me.devsaki.hentoid.parsers.images.ManhwaParser
import me.devsaki.hentoid.parsers.images.MrmParser
import me.devsaki.hentoid.parsers.images.MultpornParser
import me.devsaki.hentoid.parsers.images.MusesParser
import me.devsaki.hentoid.parsers.images.NhentaiParser
import me.devsaki.hentoid.parsers.images.PixivParser
import me.devsaki.hentoid.parsers.images.PorncomixParser
import me.devsaki.hentoid.parsers.images.PururinParser
import me.devsaki.hentoid.parsers.images.SimplyParser
import me.devsaki.hentoid.parsers.images.ToonilyParser
import me.devsaki.hentoid.parsers.images.TsuminoParser

object ContentParserFactory {

    fun getContentParserClass(site: Site): Class<out ContentParser> {
        return when (site) {
            Site.NHENTAI -> NhentaiContent::class.java
            Site.ASMHENTAI, Site.ASMHENTAI_COMICS -> ASMHentaiContent::class.java
            Site.HITOMI -> HitomiContent::class.java
            Site.TSUMINO -> TsuminoContent::class.java
            Site.PURURIN -> PururinContent::class.java
            Site.MUSES -> MusesContent::class.java
            Site.DOUJINS -> DoujinsContent::class.java
            Site.PORNCOMIX -> PorncomixContent::class.java
            Site.HENTAI2READ -> Hentai2ReadContent::class.java
            Site.HENTAIFOX -> HentaifoxContent::class.java
            Site.MRM -> MrmContent::class.java
            Site.MANHWA -> ManhwaContent::class.java
            Site.IMHENTAI -> ImhentaiContent::class.java
            Site.EHENTAI -> EhentaiContent::class.java
            Site.EXHENTAI -> ExhentaiContent::class.java
            Site.LUSCIOUS -> LusciousContent::class.java
            Site.TOONILY -> ToonilyContent::class.java
            Site.ALLPORNCOMIC -> AllPornComicContent::class.java
            Site.PIXIV -> PixivContent::class.java
            Site.MANHWA18 -> Manhwa18Content::class.java
            Site.MULTPORN -> MultpornContent::class.java
            Site.SIMPLY -> SimplyContent::class.java
            Site.HDPORNCOMICS -> HdPornComicsContent::class.java
            Site.EDOUJIN -> EdoujinContent::class.java
            Site.ANCHIRA -> AnchiraContent::class.java
            Site.DEVIANTART -> DeviantArtContent::class.java
            else -> DummyContent::class.java
        }
    }

    fun getImageListParser(content: Content?): ImageListParser {
        return if (null == content) DummyParser() else getImageListParser(content.site)
    }

    fun getImageListParser(site: Site): ImageListParser {
        return when (site) {
            Site.ASMHENTAI, Site.ASMHENTAI_COMICS -> ASMHentaiParser()
            Site.HITOMI -> HitomiParser()
            Site.TSUMINO -> TsuminoParser()
            Site.PURURIN -> PururinParser()
            Site.EHENTAI -> EHentaiParser()
            Site.EXHENTAI -> ExHentaiParser()
            Site.LUSCIOUS -> LusciousParser()
            Site.PORNCOMIX -> PorncomixParser()
            Site.MUSES -> MusesParser()
            Site.NHENTAI -> NhentaiParser()
            Site.DOUJINS -> DoujinsParser()
            Site.HENTAI2READ -> Hentai2ReadParser()
            Site.HENTAIFOX -> HentaifoxParser()
            Site.MRM -> MrmParser()
            Site.MANHWA -> ManhwaParser()
            Site.IMHENTAI -> ImhentaiParser()
            Site.TOONILY -> ToonilyParser()
            Site.ALLPORNCOMIC -> AllPornComicParser()
            Site.PIXIV -> PixivParser()
            Site.MANHWA18 -> Manhwa18Parser()
            Site.MULTPORN -> MultpornParser()
            Site.SIMPLY -> SimplyParser()
            Site.HDPORNCOMICS -> HdPornComicsParser()
            Site.EDOUJIN -> EdoujinParser()
            Site.ANCHIRA -> AnchiraParser()
            Site.DEVIANTART -> DeviantArtParser()
            else -> DummyParser()
        }
    }
}