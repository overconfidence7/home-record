package com.kurtz.homerecord.housemanual.nav

import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    private val home = Home("h1", "Maple", null, null, null, 1L, 1L, Source.HOUSE_MANUAL)

    @Test fun `fresh install opens onboarding`() = assertEquals(Routes.ONBOARDING, StartDestination.resolve(null))

    @Test fun `existing home opens hub`() = assertEquals(Routes.HOME, StartDestination.resolve(home))
}
