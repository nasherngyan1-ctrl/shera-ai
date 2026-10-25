# Security Specification — SHÊRA AI Firestore Rules

## 1. Data Invariants
1. **Multi-User Isolation**: Every document under `/users/{userId}/watchlist/{instId}`, `/users/{userId}/alertRules/{ruleId}`, and `/users/{userId}/signalHistory/{signalId}` must strictly belong to `request.auth.uid == userId` and store `data.userId == request.auth.uid`.
2. **Strict Schema & Bounds**: All strings are length-bounded, enums are validated against allowed lists, numeric fields are range-checked, and unexpected ghost keys are rejected via `.keys().hasOnly(...)`.
3. **Temporal Integrity**: `createdAt` and `updatedAt` must be Firestore server `timestamp` values (`<= request.time`), and `createdAt` plus `userId` are immutable on updates.

## 2. The Dirty Dozen Payloads
1. Unauthenticated read/list on `/users/alice/watchlist`.
2. Cross-user read (`bob` reading `/users/alice/watchlist/BTC-USDT`).
3. Identity spoofing (`alice` creating a watchlist item with `userId: "bob"`).
4. Ghost field injection (`isAdmin: true` added to `WatchlistItem`).
5. Oversized `note` string (> 200 chars) on `WatchlistItem`.
6. Invalid `ruleType` enum (`"GUARANTEED_PROFIT"`) on `AlertRule`.
7. Out-of-bounds `cooldownMinutes` (`0` or `99999`) on `AlertRule`.
8. Mutating immutable `createdAt` or `userId` on `AlertRule` update.
9. Invalid `signalCategory` (`"SURE_WIN"`) on `SignalRecord`.
10. Out-of-range `confluenceScore` (`150` or `-10`) on `SignalRecord`.
11. Non-timestamp `createdAt` (epoch integer `1700000000000`) on `SignalRecord`.
12. Malformed document ID containing illegal characters or excessive length.
