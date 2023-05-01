## Fix for https://github.com/square/okhttp/issues/3959 when profiling
-keep class okhttp3.Headers { *; }
## Fix for https://github.com/square/okhttp/issues/6299
-dontwarn org.conscrypt.ConscryptHostnameVerifier
## Fix for https://github.com/square/okhttp/issues/6258
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE