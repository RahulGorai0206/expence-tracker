package com.myapp.expensetracker

/**
 * Resolves which local row (if any) an incoming cloud record corresponds to.
 *
 * Matching is by remoteId first, then by content for rows that were recorded
 * on this device before they were ever uploaded. Each local row can be claimed
 * only once: if two cloud records resolve to the same row, the second is
 * treated as new rather than overwriting the first — two remote records must
 * never collapse into one local row.
 */
internal class LocalTransactionMatcher(local: List<Transaction>) {

    private val byRemoteId: Map<String, Transaction> =
        local.filter { !it.remoteId.isNullOrBlank() }.associateBy { it.remoteId!! }

    private val byContent: Map<String, Transaction> = local.associateBy { it.dedupKey() }

    private val claimed = mutableSetOf<Int>()

    fun match(remoteId: String?, incoming: Transaction): Transaction? {
        val byId = remoteId?.let { byRemoteId[it] }
        if (byId != null && claimed.add(byId.id)) return byId

        val byBody = byContent[incoming.dedupKey()]
        if (byBody != null && claimed.add(byBody.id)) return byBody

        return null
    }
}
