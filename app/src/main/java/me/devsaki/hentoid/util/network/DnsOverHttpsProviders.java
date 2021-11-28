package me.devsaki.hentoid.util.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class DnsOverHttpsProviders {
    private static final String[] CLOUDFARE_HOSTS = {"162.159.36.1", "162.159.46.1", "1.1.1.1", "1.0.0.1", "162.159.132.53", "2606:4700:4700::1111", "2606:4700:4700::1001", "2606:4700:4700::0064", "2606:4700:4700::6400"};

    public static List<InetAddress> getCloudflareHosts() {
        return Stream.of(CLOUDFARE_HOSTS).map(DnsOverHttpsProviders::silentGetByName).withoutNulls().toList();
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
