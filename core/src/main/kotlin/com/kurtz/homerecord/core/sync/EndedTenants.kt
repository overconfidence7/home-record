package com.kurtz.homerecord.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits each tenant this flow moves away from: a company left, switched or signed out of, a home that is gone.
 * Each app passes these to [SyncEngine.forgetTenant] so stale sync bookkeeping never outlives the membership.
 */
fun Flow<TenantRef?>.endedTenants(): Flow<TenantRef> = flow {
    var previous: TenantRef? = null
    collect { current ->
        val left = previous
        if (left != null && left != current) emit(left)
        previous = current
    }
}
