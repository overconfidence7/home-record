package com.kurtz.homerecord.fieldlog.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// T047b2: the notification permission is asked once, on Android 13+, only when this build can receive pushes.
class NotificationAskTest {
    @Test fun asksOnceOnAndroid13WithPush() {
        assertThat(NotificationAsk.shouldAsk(sdkInt = 33, granted = false, askedBefore = false, pushEnabled = true)).isTrue()
        assertThat(NotificationAsk.shouldAsk(sdkInt = 37, granted = false, askedBefore = true, pushEnabled = true)).isFalse()
    }

    @Test fun neverAsksWhenItCannotMatter() {
        assertThat(NotificationAsk.shouldAsk(sdkInt = 32, granted = false, askedBefore = false, pushEnabled = true)).isFalse()
        assertThat(NotificationAsk.shouldAsk(sdkInt = 35, granted = true, askedBefore = false, pushEnabled = true)).isFalse()
        assertThat(NotificationAsk.shouldAsk(sdkInt = 35, granted = false, askedBefore = false, pushEnabled = false)).isFalse()
    }
}
