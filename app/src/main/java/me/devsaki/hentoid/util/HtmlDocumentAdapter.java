package me.devsaki.hentoid.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public final class HtmlDocumentAdapter implements Converter<ResponseBody, Document> {
    static final Converter.Factory FACTORY = new Converter.Factory() {
        @Override
        public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
            if (type == Document.class) return new HtmlDocumentAdapter();
            return null;
        }
    };

    @Override
    public Document convert(ResponseBody responseBody) throws IOException {
        return Jsoup.parse(responseBody.string());
    }
}
