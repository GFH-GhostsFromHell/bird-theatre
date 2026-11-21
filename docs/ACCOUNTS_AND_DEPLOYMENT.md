# Bird Theatre · Account system and Render deployment

## Why Windows showed Server returned 404

The first desktop build pointed to an unprovisioned URL. The Bird Theatre prototype is now deployed at
`https://bird-theatre-h7xq.onrender.com` and its initial Render deployment completed successfully. Rebuilds from this commit use the live address.

The desktop application now has **Server settings**, so you can paste the
actual HTTPS URL assigned by Render once the portal is deployed. This setting
is saved for subsequent launches; it doesn't require a new installer.

## Account features

Bird Theatre has independent member accounts but uses the same *type* of
cross-platform account lifecycle as Electrical Workbench. It does **not**
silently import credentials, users, subscriptions, or databases from
Electrical Workbench. A Bird Theatre profile is the same on Windows, Android,
iPhone/iPad, and the browser because those clients use one Bird Theatre API.

- Sign-in: `POST /api/v1/auth/login` with username and password.
- Registration: `POST /api/v1/auth/register/start`, followed by
  `POST /api/v1/auth/register/verify` using a 6-digit email code.
  Codes expire after 15 minutes and have a maximum of 8 verification attempts.
  New registration is not activated until email verification succeeds.
- Account recovery: `/api/v1/auth/recovery/start`,
  `/api/v1/auth/recovery/complete`, and
  `/api/v1/auth/recovery/username`.
  Unknown accounts get the same initial recovery response.
- Private account details: `GET /api/v1/me/profile`, editable using
  `PUT /api/v1/me/profile`. Set a display name, broad region and biography,
  and choose whether to publish your profile.
- Password change: `POST /api/v1/me/password`, revokes previous sessions
  and returns a replacement session to the current signed-in client.
- Public profile: `GET /api/v1/members/{handle}` only for accounts that
  explicitly opt in; email addresses are never in public profile responses.
- Passwords are BCrypt-hashed. Session and camera tokens are stored as
  SHA-256 hashes. Sessions expire after 14 days.

The older, no-email `/api/v1/auth/signup` route is **disabled** in regular
builds to prevent bypassing email verification. It can only be enabled
explicitly for local test fixtures and should never be enabled on Render.

## Email verification using Electrical Workbench's existing Mailjet account

Electrical Workbench sends verification, password-reset and username-reminder emails
through Mailjet SMTP. Bird Theatre uses the **same Mailjet provider and account**,
but sends through its **HTTPS Send API v3.1** (port 443). The different transport
is necessary because **Render Free blocks outgoing SMTP 25, 465 and 587**.

**No new Mailjet subscription is needed.** Both projects can use the same
Mailjet API key/secret and verified sender, subject to the account's shared
sending limits. The two apps do not share member accounts, sessions or codes.

### Copy existing Mailjet settings securely in Render

1. Open the existing Electrical Workbench web service at
   https://dashboard.render.com/web/srv-dbj3eoc9v7es73916h1g,
   then **Environment**. Locate its Mailjet SMTP credentials. Common Spring
   names are `SPRING_MAIL_USERNAME` (Mailjet API/public key),
   `SPRING_MAIL_PASSWORD` (Mailjet secret key) and `MAIL_FROM`
   (verified sender). **Check the actual names**; do not guess values.
2. Open the new Bird Theatre web service at
   https://dashboard.render.com/web/srv-dc0rf7cs728c73b5nm40,
   then **Environment**. Add these keys with the existing Mailjet values:

   | Bird Theatre environment key | Copy from Electrical Workbench / Mailjet |
   |---|---|
   | `BIRD_MAILJET_API_KEY` | Mailjet API/public key (SMTP username) |
   | `BIRD_MAILJET_SECRET_KEY` | Mailjet secret key (SMTP password) |
   | `BIRD_MAIL_FROM` | The existing **verified sender email** |

3. Save in Render, not in GitHub, chats, or source files. Render will redeploy.
   Do not change Electrical Workbench's existing environment variables.
4. Visit https://bird-theatre-h7xq.onrender.com/api/v1/auth/options and confirm
   `emailConfigured` is `true`. This indicates the keys are present;
   it does **not** independently validate them against Mailjet.
5. Create a disposable Bird Theatre test account with an email address you
   control. Confirm that Mailjet accepts and delivers the six-digit verification
   email, then confirm sign-in and password recovery.

**Do not enter secret keys into ChatGPT messages or the public repository.**
The available Render integration cannot read or copy masked environment secrets
from Electrical Workbench, so the cross-service credential copying must be
completed securely within Render's Environment settings. A dedicated Mailjet
sub-account/API key is preferable when Bird Theatre becomes public.

When credentials are absent, Bird Theatre deliberately reports registration
and password recovery as unavailable; no unverified account is created. The
legacy SMTP transport remains optional for local development or paid hosting
only (`BIRD_SMTP_ENABLED=true` plus `BIRD_SMTP_*`), and it is
**disabled by default on Render Free**.

Both systems must respect Mailjet sender/domain verification, SPF/DKIM,
suppression lists and sending quotas. Add per-IP/account email rate limiting,
verification cleanup and abuse monitoring before inviting public signups.
Mailjet integration docs:
https://dev.mailjet.com/docs/email-api/send-api-v31/send-basic-email


## Host the backend on Render

`render.yaml` now defines a **free-tier prototype web service only**. It references
the **existing** Electrical Workbench PostgreSQL instance (`remote-javafx-portal-db`)
in Render's Ohio region, rather than creating another database instance.
The Bird Theatre application automatically creates its own `bird_theatre` schema,
sets each JDBC connection's `search_path` to that schema, and refuses to start if
it cannot verify schema isolation. This prevents accidental use of Electrical
Workbench's existing `public` tables. There are no new PostgreSQL resources in
the Blueprint, although free web hosting has usage limits and the existing
database continues to incur its current bill.

**Important:** This is logical isolation, not security or resource isolation:
both apps share the Postgres login, 1 GB storage allocation, CPU, connections,
backups, and failure domain. The existing database restricts external access,
so its current disk usage could not be checked from this session. Monitor disk
and database connections after deployment. Do not use shared database credentials
for untrusted workloads; use a separate database user and PostgreSQL database
or instance when moving into production.

The Render Blueprint is a proposed configuration, not proof that Bird Theatre is live.

1. Open the prepared Bird Theatre Blueprint in **Bryn's workspace** and verify
   its plan shows **one free-tier web service** and **zero new databases**.
2. Approve only after reviewing the plan and its expected resource usage.
3. Allow its first build and the `/actuator/health` health check to pass.
4. Securely configure the three Mailjet HTTPS API variables listed above in the Bird Theatre service Environment settings.
5. Copy the *actual Render HTTPS service URL* from the Render dashboard.
6. Open Bird Theatre Desktop → **Server settings** and paste that URL.
   Do the same in the mobile client configuration before creating production
   signed builds for Android/iOS.
7. Verify the web homepage and
   `GET /api/v1/auth/options`; test registration and email delivery using
   a test account before inviting the community.

Do not use H2 for the public community; it is an ephemeral development database.
Render's `DATABASE_URL` references the existing paid PostgreSQL instance; the
`BIRD_DB_SCHEMA=bird_theatre` environment variable is a safety boundary.
Set up backups, rate limiting, sender abuse controls, account auditing,
monitoring and privacy review before opening signups to the public.


### Prototype resource details

- Existing Render database: `remote-javafx-portal-db` (PostgreSQL 18, Ohio,
  1 GB storage, 256 MB RAM). This database continues serving Electrical Workbench.
- Bird Theatre database namespace: PostgreSQL schema `bird_theatre` inside the
  same database. **No tables are created in `public`.**
- New Bird Theatre web service: Render `free` compute (cold starts, suspension
  and monthly limits may apply). This is appropriate for development, not a
  guaranteed 24/7 live streaming community.
- Connection pool: at most two Bird Theatre database connections, with zero
  minimum idle connections.
- First launch creates only the missing `bird_theatre` schema. Hibernate's
  existing `ddl-auto=update` creates prototype tables inside that schema;
  production should migrate to Flyway and separate credentials.
- The Render Blueprint references the existing database via its name. Do not
  rename or delete `remote-javafx-portal-db` while Bird Theatre uses it.
