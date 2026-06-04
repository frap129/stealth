package com.cosmos.unreddit.data.remote.api.reddit.auth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RedditTlsFactoryProguardRulesTest {
    @Test
    fun `proguard rules keep BC reflective bridge classes`() {
        val proguardRules =
            sequenceOf(
                File("app/proguard-rules.pro"),
                File("proguard-rules.pro"),
            ).firstOrNull { it.exists() }
                ?.readText()
                ?: error("Could not locate proguard-rules.pro")

        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ImportX509TrustManager_7"))
        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ExportX509TrustManager_7"))
        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ImportSSLSession_7"))
        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ImportSSLSession_8"))
        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ExportSSLSession_7"))
        assertTrue(proguardRules.contains("org.bouncycastle.jsse.provider.ExportSSLSession_8"))
        assertTrue(proguardRules.contains("-dontwarn org.conscrypt.Conscrypt\$Version"))
        assertTrue(proguardRules.contains("-dontwarn org.conscrypt.Conscrypt"))
        assertTrue(proguardRules.contains("-dontwarn org.conscrypt.ConscryptHostnameVerifier"))
        assertTrue(proguardRules.contains("-dontwarn org.openjsse.javax.net.ssl.SSLParameters"))
        assertTrue(proguardRules.contains("-dontwarn org.openjsse.javax.net.ssl.SSLSocket"))
        assertTrue(proguardRules.contains("-dontwarn org.openjsse.net.ssl.OpenJSSE"))
        assertTrue(proguardRules.contains("-dontwarn org.slf4j.impl.StaticLoggerBinder"))
    }
}
