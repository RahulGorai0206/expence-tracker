# Updating SMS Filters & Extraction Rules

The rules that decide **whether an SMS is a transaction**, **how much it was for**, and **which
category it belongs to** live in a single file:

```
rules/extraction-rules.json
```

That file is fetched by the app at runtime from this public repo, so **filter changes ship by
pushing to `main` — no APK release required**. Users pick them up from
**Settings → Detection Rules → refresh**.

---

## Contents

- [How it reaches users](#how-it-reaches-users)
- [File format](#file-format)
- [Common tasks](#common-tasks)
- [Word-boundary matching (read this first)](#word-boundary-matching-read-this-first)
- [Validation — what gets rejected](#validation--what-gets-rejected)
- [Testing your change](#testing-your-change)
- [Releasing](#releasing)
- [Troubleshooting](#troubleshooting)

---

## How it reaches users

```
rules/extraction-rules.json  (this repo, main branch)
            │
            │  raw.githubusercontent.com
            ▼
   Settings → Detection Rules → refresh
            │
            │  parsed + validated
            ▼
   filesDir/remote_resources/extraction-rules.json
            │
            ▼
   TransactionExtractor  (next SMS uses it — no restart needed)
```

A copy is also bundled in the APK as `assets/extraction-rules.json`. The app resolves in this
order:

1. Downloaded copy, if one exists and still parses
2. Bundled copy that shipped with the build

So a bad download can never leave a user worse off than the version they installed. If a previously
downloaded file later fails to parse, it is **deleted automatically** and the bundled baseline is
restored on next launch.

> The bundled copy is generated at build time from `rules/extraction-rules.json` by the
> `bundleRemoteResources` Gradle task. There is exactly one copy in version control — never edit
> anything under `assets/` directly.

---

## File format

```jsonc
{
  "schemaVersion": 1,      // format version — bump only when the app learns new fields
  "version": 2,            // rules version — MUST increase on every change
  "releasedAt": "2026-08-16",
  "notes": "What changed and why.",

  "spendKeywords":  ["debited", "spent", "dr."],
  "receiveKeywords": ["credited", "refunded", "cr."],

  "otpPhrases":              ["otp", "verification code"],
  "nonTransactionalPhrases": ["pre-approved", "require consent"],
  "txnDisqualifiers":        ["limit", "alert", "password", "pin"],
  "creditCardBillPhrases":   ["total amount due", "minimum amount due"],

  "amountPatterns": [
    "(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"
  ],
  "balanceLookback": 15,

  "categories": {
    "Dining": ["Swiggy", "Zomato", "Cafe"]
  }
}
```

### Field reference

| Field | Purpose |
|---|---|
| `version` | **Must increase.** The app refuses anything lower than what's installed, and treats equal as "already current". |
| `releasedAt` | Shown in Settings so users can see how fresh their rules are. |
| `spendKeywords` | Any match ⇒ money went **out**. Matched on word boundaries. |
| `receiveKeywords` | Any match ⇒ money came **in**. |
| `otpPhrases` | Message is discarded outright. |
| `nonTransactionalPhrases` | Message is discarded outright — promos, limit changes, marketing. |
| `txnDisqualifiers` | Words that, appearing just after `txn`, mean it isn't a transaction ("txn limit"). |
| `creditCardBillPhrases` | Marks a message as a card statement, skipped when *Ignore CC Bills* is on. |
| `amountPatterns` | Regexes for the amount. **Group 1 must be the number.** Tried in order; first non-balance match wins. |
| `balanceLookback` | Characters scanned before a matched amount for `bal`/`balance`, so account balances aren't logged as spending. Range 0–200. |
| `categories` | Category name → merchant keywords. First matching category wins. |

Matching is case-insensitive throughout — write keywords in whatever case reads best.

---

## Common tasks

### Stop a message being tracked

Find a phrase that is distinctive to the unwanted message and **absent from real transactions**,
then add it to `nonTransactionalPhrases`.

```jsonc
"nonTransactionalPhrases": [
  "...",
  "require consent"      // YES Bank "funds available, require consent" promos
]
```

Prefer a two- or three-word phrase. Single common words will silently swallow real transactions.

### Start tracking a message that's being missed

Work out which part is missing. Usually it's the debit/credit marker.

```
Dear Customer, Acct XXX421 Dr. INR 16,498.87 on 07/07/26 to CRED Club; Bal INR 2,769.96
```

This has no `debited` / `spent` / `paid` — its marker is `Dr.`. Adding `"dr."` to `spendKeywords`
(and `"cr."` to `receiveKeywords`) makes it work.

### Add a category or merchant

```jsonc
"categories": {
  "Dining": ["Swiggy", "Zomato", "Cafe", "Blue Tokai"]
}
```

First match wins, so put narrower categories above broader ones if a merchant could match two.

### Support a new amount format

Add a regex with the number in **capture group 1**:

```jsonc
"amountPatterns": [
  "(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)",
  "amount\\s+of\\s+(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"
]
```

Remember JSON needs backslashes doubled: `\\d`, not `\d`.

---

## Word-boundary matching (read this first)

Keywords match **whole words**, not substrings. This is the single most important thing to
understand before editing keyword lists.

It exists because plain substring matching caused real bugs:

| Message | Substring match | Word-boundary match |
|---|---|---|
| `...require **consent** to continue...` | `sent` matched ⇒ logged a ₹58,000 spend | no match ✓ |
| `...**addr**ess on file...` | `dr` would match | no match ✓ |

Consequences when you edit:

- **Short markers are safe.** `dr.` only matches `Dr.` as its own token, never inside `address`.
- **Prefixed forms no longer match.** `paid` does *not* match `prepaid`; `payment` does not match
  `repayment`. If you need those, add them as their own keywords.
- **Trailing punctuation is respected.** `dr.` anchors on its left edge only, so it matches `Dr.`
  and `dr.` but not `drive`.

`nonTransactionalPhrases`, `otpPhrases` and `creditCardBillPhrases` are matched as plain
substrings, since they're distinctive multi-word phrases.

---

## Validation — what gets rejected

The file is parsed and validated **before** it is saved, so a bad publish is discarded rather than
adopted. A rejected download leaves the user on their previous rules with an error in Settings.

Rejected if:

- it isn't valid JSON
- `schemaVersion` is newer than the app understands
- `version` is missing, or lower than the installed version
- `spendKeywords` or `receiveKeywords` is empty *(this would disable detection entirely)*
- `amountPatterns` is empty, or any pattern fails to compile, has no capture group, or exceeds
  300 characters
- more than 32 amount patterns
- `balanceLookback` is outside 0–200

Blank and `null` entries inside lists are dropped rather than failing the file. Empty category
lists are dropped too.

---

## Testing your change

The unit tests run against **the real `rules/extraction-rules.json`**, so a bad edit fails the
build rather than reaching users:

```bash
./gradlew :app:testDebugUnitTest
```

Add a regression test for anything you fix, using the actual message text — see
`TransactionExtractorTest` for the pattern:

```kotlin
@Test
fun `yes bank consent promo is not a transaction`() {
    assertFalse(extractor.isSpendMessage(yesBankConsentPromo.lowercase()))
    assertTrue(extractor.isNonTransactional(yesBankConsentPromo))
}
```

`ExtractionRulesParserTest` additionally asserts the shipped file is valid and still contains the
behaviour the extractor depends on.

---

## Releasing

1. Edit `rules/extraction-rules.json`
2. **Increase `version`** and update `releasedAt` and `notes`
3. Run the tests
4. Commit and push to `main`

Users then get it from **Settings → Detection Rules → refresh**. There's a **Revert to Bundled
Filters** action there too, which drops any downloaded update and returns to the rules that shipped
with their build.

> Anyone who can push to `main` changes how every installed app parses SMS. Validation limits the
> blast radius to *bad* rules rather than arbitrary code — regexes are data, never executable — but
> treat this file as a live control channel and review changes accordingly.

---

## Troubleshooting

**"Filter update failed: …" in Settings**
The file was fetched but rejected. The message names the reason — check it against the validation
list above.

**Nothing happens / 404**
The app reads from `main`. Confirm your change is pushed, and that
`rules/extraction-rules.json` still exists at that path.

**"Already on the latest filters"**
`version` wasn't increased. The app compares versions, not content.

**A change needs an app update**
Anything beyond the data in this file — how matching works, new fields, the parser itself — is app
code. The file can only adjust keywords, phrases, patterns, categories and the lookback window.
