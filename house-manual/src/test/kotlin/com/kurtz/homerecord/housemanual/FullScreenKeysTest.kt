package com.kurtz.homerecord.housemanual

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule 3: the item form and the Pro screen hide the tab bar; viewers keep it. */
class FullScreenKeysTest {
    @Test fun itemFormAndPaywallAreFullScreen() {
        assertTrue(isFullScreen(ItemEditKey()))
        assertTrue(isFullScreen(PaywallKey()))
    }

    @Test fun viewersKeepTheTabBar() {
        assertFalse(isFullScreen(ItemDetailKey("i")))
        assertFalse(isFullScreen(ContactsKey))
    }
}
