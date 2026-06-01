package com.myapp.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {
    private val members = listOf(
        SplitMember(id = 1, eventId = 1, displayName = "A"),
        SplitMember(id = 2, eventId = 1, displayName = "B"),
        SplitMember(id = 3, eventId = 1, displayName = "C")
    )

    @Test
    fun evenSplitDistributesRounding() {
        val shares = SplitCalculator.equalShares(100.0, members)

        assertEquals(100.0, shares.sumOf { it.owedAmount }, 0.001)
        assertEquals(33.34, shares[0].owedAmount, 0.001)
        assertEquals(33.33, shares[1].owedAmount, 0.001)
        assertTrue(SplitCalculator.isBalanced(100.0, shares, SplitMode.EVEN))
    }

    @Test
    fun amountSplitAutoAdjustsUneditedRows() {
        val initial = SplitCalculator.equalShares(300.0, members)
        val edited = initial.map {
            if (it.memberId == 1L) it.copy(owedAmount = 120.0, edited = true) else it
        }

        val shares = SplitCalculator.rebalanceAmounts(300.0, edited)

        assertEquals(120.0, shares[0].owedAmount, 0.001)
        assertEquals(90.0, shares[1].owedAmount, 0.001)
        assertEquals(90.0, shares[2].owedAmount, 0.001)
        assertTrue(SplitCalculator.isBalanced(300.0, shares, SplitMode.AMOUNT))
    }

    @Test
    fun percentageSplitAutoAdjustsUneditedRows() {
        val initial = SplitCalculator.equalShares(200.0, members)
        val edited = initial.map {
            if (it.memberId == 1L) it.copy(percentage = 50.0, edited = true) else it
        }

        val shares = SplitCalculator.rebalancePercentages(200.0, edited)

        assertEquals(50.0, shares[0].percentage, 0.001)
        assertEquals(25.0, shares[1].percentage, 0.001)
        assertEquals(25.0, shares[2].percentage, 0.001)
        assertEquals(200.0, shares.sumOf { it.owedAmount }, 0.001)
        assertTrue(SplitCalculator.isBalanced(200.0, shares, SplitMode.PERCENTAGE))
    }

    @Test
    fun balancesIncludePayerAndParticipants() {
        val expenses = listOf(
            SplitExpense(
                id = 10,
                eventId = 1,
                amount = 300.0,
                description = "Dinner",
                paidByMemberId = 1,
                splitMode = "even"
            )
        )
        val shares = listOf(
            SplitShare(splitExpenseId = 10, memberId = 1, owedAmount = 100.0, percentage = 33.33),
            SplitShare(splitExpenseId = 10, memberId = 2, owedAmount = 100.0, percentage = 33.33),
            SplitShare(splitExpenseId = 10, memberId = 3, owedAmount = 100.0, percentage = 33.34)
        )

        val balances = SplitCalculator.computeBalances(members, expenses, shares)
        val settlements = SplitCalculator.simplifySettlements(balances)

        assertEquals(200.0, balances.first { it.memberId == 1L }.netAmount, 0.001)
        assertEquals(-100.0, balances.first { it.memberId == 2L }.netAmount, 0.001)
        assertEquals(2, settlements.size)
        assertTrue(settlements.all { it.toMemberId == 1L })
    }

    @Test
    fun shareSummaryShowsMemberPerspective() {
        val balances = listOf(
            MemberBalance(1, "A", 200.0),
            MemberBalance(2, "B", -100.0),
            MemberBalance(3, "C", -100.0)
        )
        val settlements = SplitCalculator.simplifySettlements(balances)

        val summary = SplitCalculator.buildShareSummary("Trip", 2, balances, settlements)!!

        assertEquals("B", summary.memberName)
        assertEquals(-100.0, summary.netAmount, 0.001)
        assertEquals(1, summary.involvedSettlements.size)
        assertEquals(3, summary.allBalances.size)
    }

    @Test
    fun partialAndFullPaymentsReduceRemainingSettlements() {
        val expenses = listOf(
            SplitExpense(
                id = 10,
                eventId = 1,
                amount = 300.0,
                description = "Dinner",
                paidByMemberId = 1,
                splitMode = "even"
            )
        )
        val shares = listOf(
            SplitShare(splitExpenseId = 10, memberId = 1, owedAmount = 100.0, percentage = 33.33),
            SplitShare(splitExpenseId = 10, memberId = 2, owedAmount = 100.0, percentage = 33.33),
            SplitShare(splitExpenseId = 10, memberId = 3, owedAmount = 100.0, percentage = 33.34)
        )

        val partialBalances = SplitCalculator.computeBalances(
            members,
            expenses,
            shares,
            payments = listOf(
                SplitPayment(
                    eventId = 1,
                    fromMemberId = 2,
                    toMemberId = 1,
                    amount = 40.0
                )
            )
        )
        val partialSettlements = SplitCalculator.simplifySettlements(partialBalances)
        assertEquals(60.0, partialSettlements.first { it.fromMemberId == 2L }.amount, 0.001)

        val fullBalances = SplitCalculator.computeBalances(
            members,
            expenses,
            shares,
            payments = listOf(
                SplitPayment(eventId = 1, fromMemberId = 2, toMemberId = 1, amount = 100.0),
                SplitPayment(eventId = 1, fromMemberId = 3, toMemberId = 1, amount = 100.0)
            )
        )
        assertTrue(SplitCalculator.simplifySettlements(fullBalances).isEmpty())
    }
}
