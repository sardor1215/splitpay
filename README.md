# SplitPay

A full-stack group expense splitting application with AML/KYC compliance, consent-based participant management, and real-time push notifications.

**Stack:** Kotlin/Ktor backend · Android Jetpack Compose frontend · PostgreSQL · Firebase FCM

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Backend](#backend)
   - [Setup & Run](#backend-setup--run)
   - [Database Schema](#database-schema)
   - [API Reference](#api-reference)
   - [Services](#services)
3. [Frontend](#frontend)
   - [Setup & Run](#frontend-setup--run)
   - [Navigation](#navigation)
   - [Screens](#screens)
   - [Architecture Patterns](#architecture-patterns)
4. [Security & Compliance](#security--compliance)
5. [Key Flows](#key-flows)

---

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│              Android App (Frontend)          │
│  Jetpack Compose · Retrofit · FCM · Coil    │
└─────────────────┬───────────────────────────┘
                  │ HTTP / REST
┌─────────────────▼───────────────────────────┐
│            Ktor Server (Backend)             │
│  JWT Auth · Activity Tracker · AML Plugin   │
└─────────────────┬───────────────────────────┘
                  │ Exposed ORM
┌─────────────────▼───────────────────────────┐
│              PostgreSQL Database             │
│           HikariCP Connection Pool           │
└─────────────────────────────────────────────┘
```

The backend is a stateless REST API. The frontend uses MVVM with StateFlow for reactive UI updates, and an in-memory cache layer (`AppCache`) for instant rendering before API responses arrive.

---

## Backend

### Backend Setup & Run

**Requirements:** JDK 17+, PostgreSQL, Gradle

**Environment:**
```bash
# No .env file — credentials are configured in Database.kt
# Default DB: jdbc:postgresql://localhost:5432/expense_app
# Default user: app_user
```

**Run:**
```bash
cd backend
./gradlew run
```

The server starts on `http://localhost:8080`.

---

### Database Schema

#### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `name` | VARCHAR(100) | |
| `email` | VARCHAR(255) | Unique |
| `phone` | VARCHAR(20) | Unique, nullable |
| `password_hash` | VARCHAR(255) | BCrypt, nullable (Google auth) |
| `is_verified` | BOOLEAN | Default false |
| `refresh_token` | VARCHAR(512) | Nullable, rotated on each refresh |
| `google_id` | VARCHAR(255) | Nullable |
| `preferred_currency` | CHAR(3) | Default "USD" |
| `is_admin` | BOOLEAN | Default false |
| `is_deleted` | BOOLEAN | Soft delete flag |
| `aml_status` | VARCHAR(20) | `clear` \| `flagged` \| `suspended` |
| `kyc_status` | VARCHAR(20) | `none` \| `pending` \| `approved` \| `rejected` |
| `account_balance` | DECIMAL(12,2) | Personal wallet balance |
| `require_consent` | BOOLEAN | Default false — if true, requires approval before being added to groups/expenses |
| `last_activity_at` | TIMESTAMPTZ | Updated on every authenticated request |

#### `expense_groups`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `name` | VARCHAR(100) | |
| `emoji` | VARCHAR(10) | Default 💰 |
| `invite_token` | VARCHAR(100) | Unique, for invite links |
| `is_archived` | BOOLEAN | Default false |
| `created_at` | TIMESTAMPTZ | |

#### `group_members`
| Column | Type | Notes |
|---|---|---|
| `group_id` | UUID | FK → expense_groups |
| `user_id` | UUID | FK → users |
| `role` | VARCHAR(20) | `admin` \| `member` |

#### `expenses`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `group_id` | UUID | FK → expense_groups |
| `title` | VARCHAR(255) | |
| `amount` | DECIMAL(12,2) | Total amount |
| `paid_by` | UUID | FK → users |
| `split_mode` | VARCHAR(20) | `equally` \| `exact` \| `percentage` |
| `category` | VARCHAR(50) | food, transport, accommodation, etc. |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | Nullable |

#### `expense_participants`
| Column | Type | Notes |
|---|---|---|
| `expense_id` | UUID | FK → expenses |
| `user_id` | UUID | FK → users |
| `share` | DECIMAL(12,2) | This participant's share amount |

#### `expense_activities`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `expense_id` | UUID | FK → expenses (CASCADE delete) |
| `user_id` | UUID | FK → users |
| `action` | VARCHAR(30) | `created` \| `updated` |
| `details` | TEXT | Human-readable description of changes |
| `created_at` | TIMESTAMPTZ | |

#### `group_invitations`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `group_id` | UUID | FK → expense_groups (CASCADE) |
| `invited_user_id` | UUID | FK → users |
| `invited_by_id` | UUID | FK → users |
| `status` | VARCHAR(20) | `pending` \| `accepted` \| `declined` |
| `created_at` | TIMESTAMPTZ | |

#### `expense_invitations`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `expense_id` | UUID | FK → expenses (CASCADE) |
| `group_id` | UUID | FK → expense_groups (CASCADE) |
| `invited_user_id` | UUID | FK → users |
| `invited_by_id` | UUID | FK → users |
| `status` | VARCHAR(20) | `pending` \| `accepted` \| `declined` |
| `created_at` | TIMESTAMPTZ | |

#### `kyc_documents`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | FK → users |
| `doc_type` | VARCHAR(30) | `id_front` \| `id_back` \| `passport` \| `selfie` |
| `file_path` | TEXT | Server file path |
| `status` | VARCHAR(20) | `uploaded` \| `pending` \| `approved` \| `rejected` |
| `rejection_reason` | TEXT | Nullable |
| `reviewed_by` | UUID | FK → users, nullable |
| `reviewed_at` | TIMESTAMPTZ | Nullable |
| `created_at` | TIMESTAMPTZ | |

#### `aml_alerts`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | FK → users |
| `alert_type` | VARCHAR(50) | `large_transaction` \| `high_frequency` \| `new_account` \| `unusual_pattern` |
| `description` | TEXT | Human-readable details |
| `amount` | DECIMAL(12,2) | Nullable |
| `expense_id` | UUID | Nullable |
| `status` | VARCHAR(20) | `pending` \| `reviewed` \| `dismissed` |
| `reviewed_by` | UUID | Nullable |
| `reviewed_at` | TIMESTAMPTZ | Nullable |
| `created_at` | TIMESTAMPTZ | |

#### `fcm_tokens`
Stores Firebase Cloud Messaging device tokens per user for push notifications.

#### `gdpr_config`
Key-value store for configurable compliance thresholds (retention periods, AML thresholds, auto-suspend count).

---

### API Reference

All endpoints except `/auth/*` require `Authorization: Bearer <accessToken>`.

#### Authentication

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Register with name, email, password, phone |
| `POST` | `/auth/login` | Login — returns access + refresh tokens |
| `POST` | `/auth/refresh` | Rotate tokens using refresh token |
| `POST` | `/auth/logout` | Revoke refresh token |
| `GET` | `/auth/verify?token=` | Verify email address |
| `POST` | `/auth/forgot-password` | Send password reset email |
| `POST` | `/auth/reset-password` | Reset password using token |
| `POST` | `/auth/google` | Login/register via Google ID token |

**Login response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "userId": "uuid",
  "name": "John",
  "email": "john@example.com"
}
```

> **Suspended accounts** receive `403 Forbidden` on login and token refresh.

#### User Profile

| Method | Path | Description |
|---|---|---|
| `GET` | `/me` | Get current user profile (includes `accountBalance`, `requireConsent`, `kycStatus`) |
| `PATCH` | `/me` | Update name, phone, avatar, currency |
| `POST` | `/me/require-consent` | Toggle consent requirement `{ "value": true }` |
| `DELETE` | `/me` | Soft-delete account (GDPR) |
| `GET` | `/me/export/json` | Download personal data as JSON |
| `GET` | `/me/export/pdf` | Download personal data as text |
| `POST` | `/users/lookup` | Lookup users by phone numbers `{ "phones": [...] }` |
| `POST` | `/users/fcm-token` | Register FCM push token |

#### Groups

| Method | Path | Description |
|---|---|---|
| `GET` | `/groups` | List user's groups (includes balance per group) |
| `POST` | `/groups` | Create group |
| `GET` | `/groups/:id` | Get group details |
| `PATCH` | `/groups/:id` | Update group name/emoji (admin only) |
| `DELETE` | `/groups/:id` | Archive group (admin only) |
| `GET` | `/groups/:id/members` | List group members |
| `POST` | `/groups/:id/members` | Add member — returns `200` (direct) or `202` (invitation sent if user requires consent) |
| `DELETE` | `/groups/:id/members/:userId` | Remove member (admin only) |
| `GET` | `/groups/:id/invite-link` | Generate invite link |
| `POST` | `/groups/join/:token` | Join group via invite link |

#### Expenses

| Method | Path | Description |
|---|---|---|
| `GET` | `/groups/:id/expenses` | List expenses — includes `pendingInvitationId` if current user hasn't consented |
| `POST` | `/groups/:id/expenses` | Create expense — AML pre-check runs before creation; sends FCM consent requests to all non-payer participants |
| `GET` | `/groups/:id/expenses/:expenseId` | Expense detail with activity timeline and `pendingInvitationId` |
| `PATCH` | `/groups/:id/expenses/:expenseId` | Update expense |
| `DELETE` | `/groups/:id/expenses/:expenseId` | Delete expense |
| `GET` | `/groups/:id/balances` | Net balances per member |
| `GET` | `/groups/:id/settlements` | Minimum transactions to settle all debts |

**Split modes:** `equally` · `exact` · `percentage`

> `equally` splits the total evenly. `exact` uses the `share` field per participant. `percentage` uses `share` as a percentage (0–100).

#### Invitations

| Method | Path | Description |
|---|---|---|
| `GET` | `/invitations/pending` | All pending invitations (group + expense) for current user |
| `POST` | `/invitations/:id/accept` | Accept group invitation → join group |
| `POST` | `/invitations/:id/decline` | Decline group invitation |
| `POST` | `/invitations/expense/:id/accept` | Accept expense invitation |
| `POST` | `/invitations/expense/:id/decline` | Decline expense invitation → removes participant, redistributes share to payer |

#### KYC (Identity Verification)

| Method | Path | Description |
|---|---|---|
| `POST` | `/kyc/documents` | Upload document (multipart: `docType` + file) |
| `POST` | `/kyc/submit` | Submit all uploaded docs for admin review |
| `GET` | `/kyc/status` | Current KYC status + document list |
| `GET` | `/admin/kyc/pending` | Admin: list pending documents |
| `PATCH` | `/admin/kyc/documents/:id` | Admin: approve or reject document |
| `POST` | `/admin/kyc/users/:userId/approve-all` | Admin: approve all pending docs for a user |
| `GET` | `/admin/kyc/documents/:id/file` | Admin: serve document file |

#### Admin

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/stats` | Platform statistics |
| `GET` | `/admin/users` | All users with AML/KYC status |
| `GET` | `/admin/groups` | All groups |
| `GET` | `/admin/aml/alerts` | AML alert queue |
| `PATCH` | `/admin/aml/alerts/:id` | Review alert (`reviewed` / `dismissed`) |
| `POST` | `/admin/users/:userId/lift-suspension` | Lift AML suspension |
| `GET` | `/admin/gdpr/config` | Current compliance config |
| `POST` | `/admin/gdpr/config` | Update thresholds |
| `POST` | `/admin/gdpr/cleanup` | Trigger data retention cleanup |

---

### Services

#### `AmlService`
Anti-Money Laundering monitoring. Runs on every expense creation.

**Pre-check (blocks expense creation):**
- `large_transaction` — amount ≥ `largeTransactionThreshold` (default $1,000)
- `high_frequency` — more than `highFrequencyCount` expenses in `highFrequencyWindowHours` hours (default: 10 in 24h)
- `new_account` — account < `newAccountDays` days old (default: 30) with amount ≥ threshold / 2

**Auto-escalation:** After `autoSuspendAfterAlerts` unreviewed alerts (default: 3), the user is automatically suspended and all admins receive an FCM notification.

#### `FcmService`
Firebase Cloud Messaging push notifications.
- `notifyUser(userId, title, body, data)` — single user
- `notifyGroupMembers(groupId, excludeUserId, memberIds, title, body)` — group broadcast
- `notifyAdmins(title, body, data)` — all admin users

#### `GdprService`
Data retention cleanup. Removes or anonymizes user data based on configurable periods. Triggered via `POST /admin/gdpr/cleanup`.

#### `ExportService`
Generates JSON or plain-text exports of all personal data for a user (GDPR Article 20 — data portability).

#### `ActivityTrackerPlugin`
Ktor application plugin that runs after JWT authentication on every request:
- Updates `last_activity_at` for authenticated users
- Blocks write operations (`POST`, `PATCH`, `PUT`, `DELETE`) for suspended users with `403 Forbidden`

---

## Frontend

### Frontend Setup & Run

**Requirements:** Android Studio, JDK 11+, Android SDK 26+

**Configuration:**
```kotlin
// frontend/app/src/main/java/com/splitpay/data/network/RetrofitClient.kt
private const val BASE_URL = "http://<your-backend-ip>:8080/"
```

Find your machine's IP:
```bash
ipconfig getifaddr en0   # macOS Wi-Fi
```

**Build & install:**
```bash
cd frontend
./gradlew installDebug
```

---

### Navigation

All screens are registered in `Screen.kt` (sealed class) and wired in `NavGraph.kt`.

```
Login / Register
    └── Home
        ├── Groups
        │   ├── CreateGroup
        │   └── GroupDetail
        │       ├── AddExpense
        │       ├── EditExpense
        │       ├── ExpenseDetail    ← Accept/Decline banner if consent pending
        │       └── Settlement
        ├── Notifications            ← Pending group + expense invitations
        └── Profile
            ├── KYC Verification
            └── Admin Dashboard
```

**Session management:** `AuthEvents` is a SharedFlow singleton that triggers navigation from anywhere in the app:
- `AuthEvents.notifyExpired()` → redirect to Login (token refresh failed)
- `AuthEvents.notifySuspended(message)` → show blocking dialog then redirect to Login (AML suspension)

---

### Screens

#### `HomeScreen`
Main landing screen showing:
- **Account Balance** — personal wallet balance from `/me`
- **You Owe / Owed To You** — aggregated across all groups
- Group list (last 5) with per-group balance
- Red dot on Notifications tab when pending invitations exist

#### `GroupDetailScreen`
Group expense dashboard:
- Total spending, user's net balance in the group
- Expense timeline — expenses with **PENDING** badge (amber) for ones awaiting user consent
- Settlement breakdown
- Add/edit/delete expenses (admin only)
- Manage members — add by phone contact, remove, invite

#### `ExpenseDetailScreen`
Full expense view:
- Hero card: category emoji, title, total amount, split mode, date
- **Consent banner** (blue) with Accept/Decline if current user hasn't responded to the expense invitation
- "Your share" banner (green = owed to you, red = you owe)
- Split breakdown with per-participant progress bars
- Activity timeline (created/updated events with change details)

#### `AddExpenseScreen` / `EditExpenseScreen`
Full-screen expense form:
- Large amount input
- Description field
- Paid by picker
- Category scrollable chips
- Split mode segmented control (`Equally` · `Exact` · `Percent`)
  - `Equally` is disabled when participant count is odd (to avoid rounding issues)
- Participant list with include/exclude toggles
- Per-participant share input for Exact/Percent modes

#### `NotificationsScreen`
Unified notification feed showing all pending invitations:
- **Group invitations** — with group emoji, group name, inviter name
- **Expense invitations** — with expense title, share amount, creator name
- Accept / Decline buttons per notification

#### `KycScreen`
Identity verification:
- Upload 4 documents: ID front, ID back, passport, selfie
- Progress bar showing upload completion
- Per-document status (Uploaded / Pending / Approved / Rejected)
- Auto-submit when all rejected docs are re-uploaded
- Submit button for first-time submission

#### `AdminScreen` (admin only)
Three-tab dashboard:
- **Users** — list with AML/KYC status badges, Lift Suspension button
- **KYC** — pending documents with approve/reject per document
- **Config** — GDPR/AML thresholds (retention periods, transaction limits, auto-suspend count)

---

### Architecture Patterns

#### MVVM + StateFlow
Each screen has a `ViewModel` exposing `StateFlow<T>`. Screens collect these flows with `collectAsStateWithLifecycle()`.

```
Screen (Composable)
   └── ViewModel (AndroidViewModel)
        ├── StateFlow → UI state
        └── API calls via RetrofitClient.api
```

#### Cache-first loading (`AppCache`)
`AppCache` is an in-memory singleton (with SharedPreferences persistence for groups) that enables instant UI rendering:

```kotlin
// Pattern used in every ViewModel load function:
AppCache.groups?.let { _groups.value = it }  // show immediately
// ...then fetch from API and update both state + cache
```

Cached data:
- `groups: List<Group>` — persisted to SharedPreferences
- `groupMembers[groupId]: List<Member>` — in-memory
- `expensesByGroup[groupId]: List<Expense>` — in-memory
- `expenseDetails[expenseId]: ExpenseResponse` — in-memory
- `expenseActivities[expenseId]: List<ExpenseActivityResponse>` — in-memory

#### Token management (`TokenManager`)
Tokens are stored in DataStore (encrypted). The `tokenAuthenticator` in `RetrofitClient` automatically refreshes the access token on `401` responses using the stored refresh token.

#### Suspension interceptor
`suspensionInterceptor` in `RetrofitClient` detects `403` responses containing "suspended". It clears tokens and emits `AuthEvents.notifySuspended(message)`, which triggers a blocking `AlertDialog` in `NavGraph` followed by logout.

---

## Security & Compliance

### Authentication
- JWT access tokens (short-lived) + refresh tokens (long-lived, rotated on each use)
- BCrypt password hashing (cost factor 12)
- Refresh token revocation on logout
- Suspended accounts blocked at login AND token refresh

### AML (Anti-Money Laundering)
- Pre-check before expense creation blocks suspicious transactions
- Three detection rules: large transaction, high frequency, new account
- Configurable thresholds via admin dashboard
- Auto-suspension after N unreviewed alerts
- Manual review + lift-suspension by admins

### KYC (Know Your Customer)
- 4 required documents: ID front, ID back, passport, selfie
- Admin review per document with rejection reasons
- Partial re-submission: only rejected documents need re-uploading
- Account features gated on `kycStatus = "approved"`

### GDPR
- Soft-delete with data anonymization
- Personal data export (JSON + text)
- Configurable data retention periods
- Right to be forgotten via `DELETE /me`

### Consent System
- Users can enable `requireConsent` in profile settings
- **Group invitations** — admin adding a user with `requireConsent = true` sends a notification instead of adding directly
- **Expense invitations** — every non-payer participant receives a consent request; decline removes them from the expense and redistributes their share to the payer

---

## Key Flows

### Creating an Expense
```
1. AML preCheck runs (blocks if violation detected)
2. Expense + participants saved to DB
3. For each participant (except payer):
   - ExpenseInvitation record created (status: pending)
   - FCM notification sent: "You've been added to X — consent required"
4. Participant sees PENDING badge on expense in group list
5. Participant opens expense → sees Accept/Decline banner
6. Accept → invitation marked accepted
   Decline → participant removed, share redistributed to payer
```

### Adding a Group Member
```
1. Admin taps Add Member
2. Backend checks target user's requireConsent flag:
   - false → added directly (200 OK), FCM to all group members
   - true  → GroupInvitation created, FCM notification sent (202 Accepted)
3. Target user sees invitation in Notifications screen
4. Accept → joins group, all members notified
   Decline → invitation discarded
```

### Token Refresh
```
1. API call returns 401
2. OkHttp tokenAuthenticator intercepts
3. Synchronous call to POST /auth/refresh
4. Success → new tokens saved, original request retried with new token
5. Failure (suspended/invalid) → tokens cleared, AuthEvents.notifyExpired() emitted
6. NavGraph collects event → popUpTo(0), navigate to Login
```

### AML Auto-Suspension
```
1. Expense created → AmlService.checkExpense() runs (fire-and-forget)
2. Alert created → checkEscalation() counts pending alerts
3. If count >= autoSuspendAfterAlerts:
   - users.aml_status = 'suspended'
   - All admins receive FCM: "User X auto-suspended"
4. On next login attempt → 403 Forbidden
5. On next API call from active session → 403 → suspensionInterceptor → dialog + logout
```
