package com.kurtz.homerecord.housemanual.tile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileStateChooserTest {
    @Test fun noShutoffsIsInactive() = assertFalse(TileStateChooser.isActive(0))

    @Test fun oneShutoffIsActive() = assertTrue(TileStateChooser.isActive(1))

    @Test fun manyShutoffsIsActive() = assertTrue(TileStateChooser.isActive(5))
}
