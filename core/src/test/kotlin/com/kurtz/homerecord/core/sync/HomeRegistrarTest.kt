package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Answers like the Worker: a home id must be registered (PUT /homes/:id) before its sync routes stop answering 404. */
private class HomesServer : SyncApi, HomeRegistrationApi {
    var online = true
    val registrations = mutableListOf<Pair<String, String>>()
    private val homes = mutableSetOf<String>()
    var pushes = 0

    override suspend fun register(homeId: String, name: String): ApiResult<Unit> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        registrations += homeId to name
        homes += homeId
        return ApiResult.Ok(Unit)
    }

    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse> {
        if (tenant.id !in homes) return ApiResult.Err(ApiFailure.NotFound)
        pushes++
        return ApiResult.Ok(PushResponse(ops.mapIndexed { i, op -> Accepted(op.outboxId, i + 1L) }))
    }

    override suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse> =
        if (tenant.id !in homes) ApiResult.Err(ApiFailure.NotFound) else ApiResult.Ok(PullResponse(serverSeq = since))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// AC-H15: signing in uploads the home, so the first sync after sign-in succeeds (judge:hm-signin-sync-failed)
class HomeRegistrarTest {
    private val home = TenantRef(TenantKind.Home, "6f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f")
    private val server = HomesServer()
    private lateinit var db: HomeRecordDatabase
    private lateinit var registrar: HomeRegistrar
    private lateinit var engine: SyncEngine

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        registrar = HomeRegistrar(server, { db.homeDao().get(it)?.name })
        engine = SyncEngine(
            server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), "phone-a",
            prepare = registrar::ensure,
        )
    }

    @After fun tearDown() = db.close()

    @Test fun anOfflineMadeHomeIsRegisteredThenSyncsClean() = runTest {
        db.homeDao().upsert(HomeEntity(id = home.id, name = "Maple St", createdAt = 1, updatedAt = 1))
        engine.recordChange(home, "home", home.id)

        assertThat(engine.sync(home)).isInstanceOf(ApiResult.Ok::class.java)

        assertThat(engine.phase.value).isEqualTo(SyncPhase.Idle)
        assertThat(server.registrations).containsExactly(home.id to "Maple St")
        assertThat(server.pushes).isEqualTo(1)
    }

    @Test fun laterPassesDoNotRegisterAgainUntilReset() = runTest {
        db.homeDao().upsert(HomeEntity(id = home.id, name = "Maple St", createdAt = 1, updatedAt = 1))
        engine.sync(home)
        engine.sync(home)
        assertThat(server.registrations).hasSize(1)

        registrar.reset()
        engine.sync(home)
        assertThat(server.registrations).hasSize(2)
    }

    @Test fun offlineRegistrationShowsOfflineAndRetriesNextPass() = runTest {
        db.homeDao().upsert(HomeEntity(id = home.id, name = "Maple St", createdAt = 1, updatedAt = 1))
        server.online = false
        assertThat(engine.sync(home)).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(engine.phase.value).isEqualTo(SyncPhase.Offline)

        server.online = true
        assertThat(engine.sync(home)).isInstanceOf(ApiResult.Ok::class.java)
        assertThat(server.registrations).hasSize(1)
    }

    @Test fun aHomeWithoutANameIsRegisteredUnderTheFallback() = runTest {
        assertThat(registrar.ensure(home)).isEqualTo(ApiResult.Ok(Unit))
        assertThat(server.registrations).containsExactly(home.id to "Home")
    }

    @Test fun companiesPassStraightThrough() = runTest {
        assertThat(registrar.ensure(TenantRef(TenantKind.Company, "c1"))).isEqualTo(ApiResult.Ok(Unit))
        assertThat(server.registrations).isEmpty()
    }
}
