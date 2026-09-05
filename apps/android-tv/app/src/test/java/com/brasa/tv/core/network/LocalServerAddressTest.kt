package com.brasa.tv.core.network

import org.junit.Assert.*
import org.junit.Test

class LocalServerAddressTest {
    @Test fun normalizesPrivateAddressAndDefaultPort(){assertEquals("http://192.168.1.20:4173",LocalServerAddress.normalize("192.168.1.20"))}
    @Test fun acceptsMdnsName(){assertEquals("http://brasa-sala.local:5000",LocalServerAddress.normalize("brasa-sala.local:5000"))}
    @Test fun rejectsPublicAndCredentialBearingAddresses(){assertThrows(IllegalArgumentException::class.java){LocalServerAddress.normalize("https://8.8.8.8")};assertThrows(IllegalArgumentException::class.java){LocalServerAddress.normalize("http://user:password@192.168.1.2")}}
    @Test fun keepsRequestsOnPairedOrigin(){assertEquals("http://10.0.0.8:4173/api/v1/bootstrap",LocalServerAddress.resolve("10.0.0.8","/api/v1/bootstrap"));assertTrue(LocalServerAddress.sameOrigin("http://10.0.0.8:4173","http://10.0.0.8:4173/movie"));assertFalse(LocalServerAddress.sameOrigin("http://10.0.0.8:4173","http://10.0.0.9:4173/movie"))}
}
