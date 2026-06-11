# SplitPay — Frontend Design Report

**Platform:** Android  
**Language:** Kotlin  
**UI Framework:** Jetpack Compose (Material3)  
**Architecture:** MVVM (Model-View-ViewModel)  
**Minimum SDK:** 26 (Android 8.0)  
**Target SDK:** 36  

---

## 1. Project Structure

```
frontend/app/src/main/java/com/splitpay/
├── MainActivity.kt              # Entry point, theme observer
├── SplitPayApp.kt               # Application class, global init
├── SplitPayFcmService.kt        # Firebase push notification handler
│
├── data/
│   ├── local/
│   │   ├── AppCache.kt          # In-memory + disk cache (SharedPreferences)
│   │   ├── AuthEvents.kt        # Global auth event bus (SharedFlow)
│   │   ├── ThemeManager.kt      # Dark/light mode singleton
│   │   └── TokenManager.kt      # JWT token persistence (SharedPreferences)
│   ├── model/
│   │   ├── Group.kt             # Local group data model
│   │   └── Member.kt            # Local member data model
│   └── network/
│       ├── ApiModels.kt         # Request/response data classes
│       ├── ApiService.kt        # Retrofit interface (all endpoints)
│       └── RetrofitClient.kt    # OkHttp client + interceptors
│
├── navigation/
│   ├── NavGraph.kt              # Navigation host + global event listeners
│   └── Screen.kt                # Sealed class of all routes
│
├── ui/
│   ├── admin/AdminScreen.kt
│   ├── auth/
│   │   ├── LoginScreen.kt
│   │   └── RegisterScreen.kt
│   ├── group/GroupDetailScreen.kt
│   ├── groups/
│   │   ├── CreateGroupScreen.kt
│   │   └── GroupsScreen.kt
│   ├── home/HomeScreen.kt
│   ├── kyc/KycScreen.kt
│   ├── notifications/NotificationsScreen.kt
│   ├── profile/ProfileScreen.kt
│   ├── space/
│   │   ├── CreateSpaceScreen.kt
│   │   ├── SpaceDetailScreen.kt
│   │   └── SpaceListScreen.kt
│   └── theme/
│       ├── AppColors.kt         # Color system (light + dark)
│       ├── Color.kt             # Base color constants
│       ├── FormatAmount.kt      # Compact number formatter (1K / 1M / 1B)
│       ├── Theme.kt             # SplitPayTheme composable
│       └── Type.kt              # Typography
│
└── viewmodel/
    ├── AdminViewModel.kt
    ├── AuthViewModel.kt
    ├── CreateGroupViewModel.kt
    ├── GroupDetailViewModel.kt
    ├── HomeViewModel.kt
    ├── KycViewModel.kt
    ├── NotificationsViewModel.kt
    ├── ProfileViewModel.kt
    └── SpaceViewModel.kt
```

---

## 2. Architecture — MVVM

The app follows a strict MVVM pattern using Jetpack Compose:

```
UI (Composable)
    ↓  observes StateFlow
ViewModel (AndroidViewModel)
    ↓  calls suspend functions
Repository / ApiService (Retrofit)
    ↓  HTTP
Backend API
```

- **State** is exposed via `StateFlow<T>` and collected with `collectAsStateWithLifecycle()`
- **Side effects** use `LaunchedEffect` and `SharedFlow`
- **No LiveData** — all reactive state uses Kotlin coroutines + Flow
- ViewModels are created with `viewModel()` and survive configuration changes

---

## 3. Navigation

Navigation uses **Jetpack Navigation Compose** with a single `NavHost`.

### Route Map

| Route | Screen | Auth Required |
|---|---|---|
| `login` | LoginScreen | No |
| `register` | RegisterScreen | No |
| `home` | HomeScreen | Yes |
| `groups` | GroupsScreen | Yes |
| `groups/create` | CreateGroupScreen | Yes |
| `group/{groupId}` | GroupDetailScreen | Yes |
| `group/{groupId}/spaces` | SpaceListScreen | Yes |
| `group/{groupId}/spaces/create` | CreateSpaceScreen | Yes |
| `group/{groupId}/spaces/{spaceId}` | SpaceDetailScreen | Yes |
| `notifications` | NotificationsScreen | Yes |
| `profile` | ProfileScreen | Yes |
| `admin` | AdminScreen | Yes (admin only) |
| `kyc` | KycScreen | Yes |

### Global Listeners in NavGraph

`NavGraph.kt` listens to two global events at the root level:

- **`AuthEvents.sessionExpired`** — redirects to Login and clears the back stack when JWT refresh fails
- **`AuthEvents.accountSuspended`** — shows an `AlertDialog` explaining suspension then redirects to Login

---

## 4. Network Layer

### RetrofitClient

All HTTP communication goes through a single `OkHttpClient` shared by the Retrofit instance.

**Base URL:** `https://splitpay.fnlsrv.website/`

#### Interceptors & Authenticator

| Component | Role |
|---|---|
| `authInterceptor` | Adds `Authorization: Bearer <accessToken>` to every request |
| `suspensionInterceptor` | Detects 403 "suspended" responses, clears token, emits `AuthEvents.notifySuspended()` |
| `tokenAuthenticator` | OkHttp `Authenticator` — on 401, attempts silent token refresh, retries original request with new token |
| `HttpLoggingInterceptor` | Logs HTTP requests/responses in debug mode |

#### Silent Token Refresh (tokenAuthenticator)

1. On any 401 response (excluding `/auth/*` routes), the authenticator intercepts
2. A `ReentrantLock` prevents concurrent refresh races
3. If another thread already refreshed while waiting → reuse new token, skip re-fetch
4. Makes a synchronous refresh call to `POST /auth/refresh`
5. On success → saves new tokens, retries original request
6. On failure → clears tokens, emits `AuthEvents.notifyExpired()` → redirect to Login

---

## 5. Local Data Layer

### TokenManager

Persists authentication data in `SharedPreferences` (`splitpay_prefs`):

| Key | Value |
|---|---|
| `access_token` | JWT access token (15 min expiry) |
| `refresh_token` | JWT refresh token (30 days) |
| `user_id` | Logged-in user UUID |
| `user_name` | Display name |
| `user_email` | Email address |
| `fcm_token` | Firebase push notification token |

### AppCache

In-memory cache with optional disk persistence for groups:

- `groups` — list of groups (persisted to `SharedPreferences` as JSON)
- `groupMembers` — map of `groupId → List<Member>` (in-memory only)
- `archivedGroups` — archived groups (in-memory only)
- `clearAll()` — called on logout (disk cache preserved for instant display on next login)
- `invalidateGroup(id)` — removes a specific group from all caches

### ThemeManager

Singleton that manages dark/light mode across the app:

- Persists preference to `SharedPreferences` (`splitpay_theme`)
- Exposes `isDark: StateFlow<Boolean>`
- `toggle()` flips the mode and persists it immediately

### AuthEvents

Global `SharedFlow`-based event bus for cross-layer communication:

- `sessionExpired` — emitted when refresh token is invalid or expired
- `accountSuspended(message)` — emitted when a 403 "suspended" response is detected

---

## 6. Screens & ViewModels

### 6.1 Authentication

#### LoginScreen + AuthViewModel

- Accepts **email or phone number** as login identifier
- Keyboard type adapts dynamically: phone keyboard when input starts with `+` or a digit
- Google Sign-In via **Credential Manager** (`GetGoogleIdOption`)
- After login: saves tokens, clears cache, registers FCM token
- UI states: `Idle`, `Loading`, `Success`, `Error(message)`

#### RegisterScreen + AuthViewModel

- Required fields: name, email, password, phone
- Password validation: minimum 8 characters, 1 uppercase, 1 digit
- Auto-login after successful registration

---

### 6.2 Home

#### HomeScreen + HomeViewModel

**Balance section:**
- Large account balance display (user's wallet)
- Two tappable cards: "YOU OWE" and "OWED TO YOU"
- Tapping opens a `ModalBottomSheet` listing groups by balance

**Send Money button:**
- Full-width gradient button
- Opens `SendMoneySheet` bottom sheet
- Step 1: search by phone number → lookup user via `POST /users/lookup`
- Step 2: enter amount + optional note → confirm transfer
- Live balance check with insufficient-balance warning

**Group list:**
- Shows last 5 active groups with balance indicator
- "View all" navigates to GroupsScreen

**Refresh strategy:**
- `ON_RESUME` lifecycle observer → `fetchGroups()` with 30-second cooldown
- Auto-polling every 60 seconds
- Pull-to-refresh (force refresh, bypasses cooldown)

**Bottom navigation:**
- 4 tabs: Home, Groups, Notifications, Profile (icons only, no labels)
- Red dot badge on Notifications tab when `pendingCount > 0`

---

### 6.3 Groups

#### GroupsScreen

- Lists all active groups with balance per group
- Archive/unarchive toggle
- Navigates to GroupDetailScreen on tap

#### CreateGroupScreen + CreateGroupViewModel

- Group name + emoji picker
- Contact integration: reads device contacts via `ContentResolver`
- Filters contacts already on SplitPay via `POST /users/lookup`
- Two lists: "SplitPay Contacts" (addable directly) and "All Contacts" (invite by SMS)
- Long names truncated with ellipsis

#### GroupDetailScreen + GroupDetailViewModel

- Member list with roles (admin / member), ellipsis on long names
- Expense list with pending badge for unaccepted invitations
- Add member with consent check (direct add vs invitation)
- Archive/delete group (admin only)
- Spaces tab for viewing group spaces
- Pull-to-refresh with 30-second cooldown

---

### 6.4 Spaces

#### SpaceListScreen + SpaceViewModel

- Lists all spaces for a group
- Status badge: `pending_acceptance`, `active`, `settling`, `settled`, `cancelled`, `suspended`
- Pull-to-refresh

#### SpaceDetailScreen

- Full space detail: participants, shares, acceptance status
- Accept/Decline buttons for pending invitations
- Early settle request (PLAN mode)
- Launcher transfer
- Quorum confirmation
- Audit log

#### CreateSpaceScreen

- Name, category, total amount
- Split mode: equally / exact / percentage
- Settlement mode: PAY (immediate) / PLAN (with due date)
- Participant picker from group members

---

### 6.5 Notifications

#### NotificationsScreen + NotificationsViewModel

- Unified list: group invitations + expense invitations + space invitations
- Accept/Decline for each item
- Auto-polling every **30 seconds**
- Pull-to-refresh

---

### 6.6 Profile

#### ProfileScreen + ProfileViewModel

- Avatar with initials (first 2 letters of name)
- Edit profile dialog (name + phone)
- Stats: total group balance + group count
- Settings: currency, dark mode toggle, require consent toggle
- KYC status with navigation to KYC screen
- Account balance display
- Payment history (last 10)
- Pending invitations list
- Admin dashboard link (if `isAdmin = true`)
- Logout button
- Delete account button with GDPR confirmation dialog

---

### 6.7 KYC

#### KycScreen + KycViewModel

- Upload 4 document types: ID front, ID back, passport, selfie
- Camera capture or gallery pick (base64 encoded)
- Status per document: `uploaded`, `pending_review`, `approved`, `rejected`
- Auto-submit when all rejected documents are re-uploaded
- Real-time status from `GET /kyc/status`

---

### 6.8 Admin

#### AdminScreen + AdminViewModel

6 tabs:

| Tab | Content |
|---|---|
| Users | All users with AML status, balance, lift-suspension button |
| Balances | All users with balance + Manage button (set / add / subtract) |
| Groups | All groups with member/expense counts |
| KYC | Pending documents grouped by user, approve/reject per doc |
| Alerts | AML alerts with status filter, clear/suspend actions |
| Config | GDPR thresholds and AML parameters |

---

## 7. Theme System

### Color System — AppColors

Two predefined color schemes: `LightAppColors` and `DarkAppColors`.

| Token | Light | Dark |
|---|---|---|
| `primary` | `#2B348D` (navy blue) | `#8A93E0` (soft blue) |
| `primaryContainer` | `#444DA6` | `#5D66BB` |
| `secondary` | `#1B6D24` (green) | `#4CAF6A` |
| `tertiary` | `#84000C` (red) | `#CF6679` |
| `surface` | `#F9F9FC` | `#111318` |
| `onSurface` | `#1A1C1E` | `#E2E2E9` |

Colors are provided via `CompositionLocal`:
```kotlin
val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val c = LocalAppColors.current
```

Each screen reads `val c = LocalAppColors.current` — no hardcoded colors anywhere.

### Dark Mode

- Controlled by `ThemeManager.isDark: StateFlow<Boolean>`
- `MainActivity` observes and passes `darkTheme` to `SplitPayTheme`
- Toggle persisted across app restarts via `SharedPreferences`

### Amount Formatting — FormatAmount.kt

```kotlin
fun formatAmount(amount: Double): String
```

| Value | Display |
|---|---|
| 999.99 | $999.99 |
| 1,500 | $1.5K |
| 25,000 | $25.0K |
| 1,200,000 | $1.2M |
| 3,500,000,000 | $3.5B |

Applied on all monetary displays across all screens.

---

## 8. Push Notifications — FCM

`SplitPayFcmService` extends `FirebaseMessagingService`:

- On new token → stores in `TokenManager` and registers via `POST /users/fcm-token`
- On message received → displays a system notification with title + body
- Notification types sent by the backend:
  - `space_invitation` — invited to a new space
  - `expense_invitation` — consent required for an expense
  - `payment_received` — direct payment received
  - `early_settle_request` — early settlement vote requested
  - `quorum_confirmation` — quorum confirmation required

---

## 9. Data Refresh Strategy

| Screen | ON_RESUME | Auto-poll | Pull-to-refresh |
|---|---|---|---|
| Home | ✅ (30s cooldown) | Every 60s | ✅ Force |
| GroupDetail | ✅ (30s cooldown) | — | ✅ Force |
| Groups | ✅ | — | ✅ |
| Notifications | — | Every 30s | ✅ |
| Profile | — | — | ✅ |
| SpaceList | — | — | ✅ |
| SpaceDetail | — | — | ✅ |

---

## 10. Key Dependencies

| Library | Version | Purpose |
|---|---|---|
| Jetpack Compose BOM | Latest | UI framework |
| Material3 | Latest | Components + theming |
| Navigation Compose | 2.7.7 | Screen navigation |
| Retrofit | 2.9.0 | HTTP client |
| OkHttp | 4.12.0 | HTTP + interceptors |
| Gson | — | JSON serialization |
| Coroutines | 1.7.3 | Async operations |
| Lifecycle ViewModel | 2.7.0 | MVVM support |
| Firebase BOM | 34.12.0 | FCM push notifications |
| Coil | 2.6.0 | Image loading (KYC docs) |
| DataStore | 1.0.0 | Token persistence |
| Credentials | 1.3.0 | Google Sign-In |
| Splashscreen | 1.0.1 | Android splash screen |

---

## 11. Security Considerations

- **No secrets in code** — all API keys/tokens stored in `SharedPreferences` at runtime
- **HTTPS only** — `usesCleartextTraffic = false` in `AndroidManifest.xml`
- **Token rotation** — refresh tokens are rotated on each use (one-time tokens)
- **Mutex on refresh** — prevents concurrent refresh race conditions
- **Auto-logout** — on session expiry or account suspension
- **No credentials in logs** — `HttpLoggingInterceptor` set to `BASIC` level only
