package com.heysia.app.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAppsTest {
    @Test fun recognisesOneTimeCodes() {
        assertTrue(PrivateApps.looksLikeCode("Your OTP is 482913"))
        assertTrue(PrivateApps.looksLikeCode("482913 is your verification code"))
        assertTrue(PrivateApps.looksLikeCode("Use code 5521 to log in"))
        assertFalse(PrivateApps.looksLikeCode("Rafi: are you coming at 7?"))
        assertFalse(PrivateApps.looksLikeCode("New video from your subscription"))
    }
}
