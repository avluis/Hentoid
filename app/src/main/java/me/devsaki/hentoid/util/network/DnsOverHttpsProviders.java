package me.devsaki.hentoid.util.network;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

public class DnsOverHttpsProviders {

    // To sync with pref_browser_dns_* in array_preferences.xml
    @IntDef({Source.NONE, Source.CLOUDFLARE, Source.QUAD9, Source.ADGUARD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Source {
        int NONE = -1;
        int CLOUDFLARE = 0;
        int QUAD9 = 1;
        int ADGUARD = 2;
    }

    private static final String[] CLOUDFARE_HOSTS = {"162.159.36.1", "162.159.46.1", "1.1.1.1", "1.0.0.1", "162.159.132.53", "2606:4700:4700::1111", "2606:4700:4700::1001", "2606:4700:4700::0064", "2606:4700:4700::6400"};
    private static final String[] QUAD9_HOSTS = {"9.9.9.9", "149.112.112.112"};
    private static final String[] ADGUARD_HOSTS = {"94.140.14.14", "94.140.15.15"};

    public static HttpUrl getPrimaryUrl(@Source int source) {
        switch (source) {
            case Source.CLOUDFLARE:
                return HttpUrl.get("https://cloudflare-dns.com/dns-query");
            case Source.QUAD9:
                return HttpUrl.get("https://dns.quad9.net/dns-query");
            case Source.ADGUARD:
                return HttpUrl.get("https://dns.adguard.com/dns-query");
            case Source.NONE:
            default:
                return HttpUrl.get("");
        }
    }

    public static List<InetAddress> getHosts(@Source int source) {
        switch (source) {
            case Source.CLOUDFLARE:
                return Stream.of(CLOUDFARE_HOSTS).map(DnsOverHttpsProviders::silentGetByName).withoutNulls().toList();
            case Source.QUAD9:
                return Stream.of(QUAD9_HOSTS).map(DnsOverHttpsProviders::silentGetByName).withoutNulls().toList();
            case Source.ADGUARD:
                return Stream.of(ADGUARD_HOSTS).map(DnsOverHttpsProviders::silentGetByName).withoutNulls().toList();
            case Source.NONE:
            default:
                return Collections.emptyList();
        }
    }

    @Nullable
    public static InetAddress silentGetByName(@NonNull String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
