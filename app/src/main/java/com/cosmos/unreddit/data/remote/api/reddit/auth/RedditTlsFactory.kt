package com.cosmos.unreddit.data.remote.api.reddit.auth

import com.github.zhkl0228.impersonator.ImpersonatorFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object RedditTlsFactory {
    private val impersonator = ImpersonatorFactory.android()

    private data class TlsConfig(
        val socketFactory: SSLSocketFactory,
        val trustManager: X509TrustManager,
    )

    private val tlsConfig: TlsConfig by lazy {
        val tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmFactory.init(null as java.security.KeyStore?)
        val trustManagers = tmFactory.trustManagers
        val trustManager =
            trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: error("No X509TrustManager found from ${TrustManagerFactory.getDefaultAlgorithm()}")

        val sslContext = impersonator.newSSLContext(null, trustManagers)
        TlsConfig(
            socketFactory = sslContext.socketFactory,
            trustManager = trustManager,
        )
    }

    fun socketFactory(): SSLSocketFactory = tlsConfig.socketFactory

    fun trustManager(): X509TrustManager = tlsConfig.trustManager
}
