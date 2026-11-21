# Bird Theatre · Account system and Render deployment

## Why Windows showed Server returned 404

The desktop program was built before its server was deployed. The default
`https://bird-theatre.onrender.com` is a **planned address**, not a promise
that Render has an active web service there.

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

## Email verification and Mailjet/SMTP

**Registration and password recovery require server-side SMTP.** Without
mail delivery, the backend will correctly report these features as
unavailable; this avoids creating accounts that cannot be verified.

Configure private environment variables on the Bird Theatre Render service:

| Environment variable | Purpose |
|---|---|
| `BIRD_SMTP_HOST` | SMTP host (Mailjet example: `in-v3.mailjet.com`) |
| `BIRD_SMTP_PORT` | SMTP port (commonly `587`) |
| `BIRD_SMTP_USERNAME` | SMTP login/API key |
| `BIRD_SMTP_PASSWORD` | SMTP secret; never commit or publish |
| `BIRD_MAIL_FROM` | Verified sender email address |

Use valid sending-domain verification, SPF and DKIM according to your
mail provider. Never put real SMTP credentials in GitHub code, Android,
Windows, or iOS app assets. The `GET /api/v1/auth/options` endpoint indicates
whether the server has an email sender configured.

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
4. Configure SMTP variables listed above in the web-service environment.
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
