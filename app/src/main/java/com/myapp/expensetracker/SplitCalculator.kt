package com.myapp.expensetracker

import kotlin.math.abs
import kotlin.math.roundToLong

data class SplitShareDraft(
    val memberId: Long,
    val memberName: String,
    val owedAmount: Double,
    val percentage: Double,
    val edited: Boolean = false
)

object SplitCalculator {
    private const val CENTS_PER_UNIT = 100L
    private const val PERCENT_BASIS = 10_000L

    fun equalShares(total: Double, members: List<SplitMember>): List<SplitShareDraft> {
        val cents = total.toCents()
        val amounts = distribute(cents, members.size)
        return members.mapIndexed { index, member ->
            val amount = amounts.getOrElse(index) { 0L }
            SplitShareDraft(
                memberId = member.id,
                memberName = member.displayName,
                owedAmount = amount.fromCents(),
                percentage = if (cents == 0L) 0.0 else amount * 100.0 / cents
            )
        }
    }

    fun rebalanceAmounts(total: Double, drafts: List<SplitShareDraft>): List<SplitShareDraft> {
        if (drafts.isEmpty()) return emptyList()
        val totalCents = total.toCents()
        val editedTotal = drafts.filter { it.edited }.sumOf { it.owedAmount.toCents() }
        val unedited = drafts.filterNot { it.edited }
        if (unedited.isEmpty()) {
            return drafts.map { it.withRoundedAmount(totalCents) }
        }
        val remaining = (totalCents - editedTotal).coerceAtLeast(0L)
        val distributed = distribute(remaining, unedited.size)
        var uneditedIndex = 0
        return drafts.map { draft ->
            if (draft.edited) {
                draft.withRoundedAmount(totalCents)
            } else {
                val amount = distributed[uneditedIndex++]
                draft.copy(
                    owedAmount = amount.fromCents(),
                    percentage = if (totalCents == 0L) 0.0 else amount * 100.0 / totalCents
                )
            }
        }
    }

    fun rebalancePercentages(total: Double, drafts: List<SplitShareDraft>): List<SplitShareDraft> {
        if (drafts.isEmpty()) return emptyList()
        val totalCents = total.toCents()
        val editedBasis = drafts.filter { it.edited }.sumOf { (it.percentage * 100).roundToLong() }
        val unedited = drafts.filterNot { it.edited }
        if (unedited.isEmpty()) {
            return drafts.map { it.withRoundedPercent(totalCents) }
        }
        val remainingBasis = (PERCENT_BASIS - editedBasis).coerceAtLeast(0L)
        val distributedBasis = distribute(remainingBasis, unedited.size)
        var uneditedIndex = 0
        val basisByMember = drafts.map { draft ->
            if (draft.edited) {
                (draft.percentage * 100).roundToLong().coerceAtLeast(0L)
            } else {
                distributedBasis[uneditedIndex++]
            }
        }
        val rawAmounts =
            basisByMember.map { basis -> totalCents * basis / PERCENT_BASIS }.toMutableList()
        var remainder = totalCents - rawAmounts.sum()
        var index = 0
        while (remainder > 0 && rawAmounts.isNotEmpty()) {
            rawAmounts[index] = rawAmounts[index] + 1
            remainder--
            index = (index + 1) % rawAmounts.size
        }
        return drafts.mapIndexed { draftIndex, draft ->
            draft.copy(
                percentage = basisByMember[draftIndex] / 100.0,
                owedAmount = rawAmounts[draftIndex].fromCents()
            )
        }
    }

    fun isBalanced(total: Double, shares: List<SplitShareDraft>, mode: SplitMode): Boolean {
        val totalCents = total.toCents()
        val amountCents = shares.sumOf { it.owedAmount.toCents() }
        val percentBasis = shares.sumOf { (it.percentage * 100).roundToLong() }
        val amountOk = abs(totalCents - amountCents) <= 1L
        val percentOk = mode != SplitMode.PERCENTAGE || abs(PERCENT_BASIS - percentBasis) <= 1L
        return shares.isNotEmpty() && amountOk && percentOk
    }

    fun computeBalances(
        members: List<SplitMember>,
        expenses: List<SplitExpense>,
        shares: List<SplitShare>,
        payments: List<SplitPayment> = emptyList()
    ): List<MemberBalance> {
        val paid = expenses.groupBy { it.paidByMemberId }
            .mapValues { entry -> entry.value.sumOf { it.amount.toCents() } }
        val owed = shares.groupBy { it.memberId }
            .mapValues { entry -> entry.value.sumOf { it.owedAmount.toCents() } }
        val paymentCredits = payments.groupBy { it.fromMemberId }
            .mapValues { entry -> entry.value.sumOf { it.amount.toCents() } }
        val paymentDebits = payments.groupBy { it.toMemberId }
            .mapValues { entry -> entry.value.sumOf { it.amount.toCents() } }
        return members.map { member ->
            val netCents = (paid[member.id] ?: 0L) -
                    (owed[member.id] ?: 0L) +
                    (paymentCredits[member.id] ?: 0L) -
                    (paymentDebits[member.id] ?: 0L)
            MemberBalance(member.id, member.displayName, netCents.fromCents())
        }
    }

    fun simplifySettlements(balances: List<MemberBalance>): List<Settlement> {
        val debtors = balances.map { it to it.netAmount.toCents() }
            .filter { it.second < 0 }
            .map { it.first to -it.second }
            .toMutableList()
        val creditors = balances.map { it to it.netAmount.toCents() }
            .filter { it.second > 0 }
            .toMutableList()
        val settlements = mutableListOf<Settlement>()
        var debtorIndex = 0
        var creditorIndex = 0

        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val debtor = debtors[debtorIndex]
            val creditor = creditors[creditorIndex]
            val amount = minOf(debtor.second, creditor.second)
            if (amount > 0L) {
                settlements += Settlement(
                    fromMemberId = debtor.first.memberId,
                    fromMemberName = debtor.first.memberName,
                    toMemberId = creditor.first.memberId,
                    toMemberName = creditor.first.memberName,
                    amount = amount.fromCents()
                )
            }
            debtors[debtorIndex] = debtor.first to (debtor.second - amount)
            creditors[creditorIndex] = creditor.first to (creditor.second - amount)
            if (debtors[debtorIndex].second == 0L) debtorIndex++
            if (creditors[creditorIndex].second == 0L) creditorIndex++
        }
        return settlements
    }

    fun buildShareSummary(
        eventName: String,
        memberId: Long,
        balances: List<MemberBalance>,
        settlements: List<Settlement>
    ): SplitShareSummary? {
        val member = balances.firstOrNull { it.memberId == memberId } ?: return null
        return SplitShareSummary(
            eventName = eventName,
            memberId = member.memberId,
            memberName = member.memberName,
            netAmount = member.netAmount,
            involvedSettlements = settlements.filter {
                it.fromMemberId == memberId || it.toMemberId == memberId
            },
            allBalances = balances
        )
    }

    private fun SplitShareDraft.withRoundedAmount(totalCents: Long): SplitShareDraft {
        val amount = owedAmount.toCents().coerceAtLeast(0L)
        return copy(
            owedAmount = amount.fromCents(),
            percentage = if (totalCents == 0L) 0.0 else amount * 100.0 / totalCents
        )
    }

    private fun SplitShareDraft.withRoundedPercent(totalCents: Long): SplitShareDraft {
        val basis = (percentage * 100).roundToLong().coerceAtLeast(0L)
        val amount = totalCents * basis / PERCENT_BASIS
        return copy(
            percentage = basis / 100.0,
            owedAmount = amount.fromCents()
        )
    }

    private fun distribute(total: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val base = total / count
        val remainder = total % count
        return List(count) { index -> base + if (index < remainder) 1 else 0 }
    }

    private fun Double.toCents(): Long = (this * CENTS_PER_UNIT).roundToLong()

    private fun Long.fromCents(): Double = this / CENTS_PER_UNIT.toDouble()
}
