# Aureon CRM: production checklist

Work through this before real users sign in. The apps enforce only what is marked so; the rest is
on the deployer. Variables in detail: the API's [configuration](../configuration.md) and the web's
[configuration](https://github.com/neszaDev/aureon-crm-web/blob/feat/crm/docs/configuration.md).
Azure Container Apps specifics (resources, secrets, rollback): [deployment.md](../deployment.md).
Why each item matters: [security.md](security.md). None of this has run in a real deployment yet
(the project runs locally only), and the nginx lines below are a sketch, not tested here.

## Before you start

- [ ] **Build the images yourself.** This project publishes none: the CD workflows that would
      (`cd-dev.yml`, `cd-main.yml`) are disabled in both repositories, and CI hasn't run on
      `feat/crm` ([CI](../ci.md#the-crm-branch-featcrm)). API:
      `docker build -f docker/Dockerfile --target runtime .`; web:
      `docker build -f docker/Dockerfile .`. Build from a commit whose suites passed
      ([status](status.md#verification)).
- [ ] **Run `make smoke` on the API image** (the prod profile against a real PostgreSQL: health,
      JSON logs, Prometheus, writable uploads). Phase 4 didn't run it.

## API

- [ ] `SPRING_PROFILES_ACTIVE=prod` (the runtime image's default), never next to `local` or `dev`.
- [ ] `JWT_SECRET`: random, at least 32 bytes, from a secret manager (`openssl rand -hex 32`).
      _Enforced:_ the app refuses a shorter one and this repository's placeholders under `prod`.
- [ ] `DB_URL`: TLS with a verified certificate, `jdbc:postgresql://<host>:5432/<db>?sslmode=verify-full`.
      **Never add `logServerErrorDetail`** to it: the URL would override `application.yml`'s
      `false`, and PostgreSQL's Detail lines would put stored values (emails, rows with password
      hashes) in the logs.
- [ ] `DB_USERNAME`, `DB_PASSWORD`: a user of its own with a generated password (not the local
      `boilerplate` / `change-me`). Flyway migrates at startup, so it needs DDL rights on the
      schema.
- [ ] `CORS_ALLOWED_ORIGINS`: exactly the web app's public origin (`https://crm.example.com`),
      never `*`. Browsers never call the API (the web server does), so nothing else belongs
      there. _Enforced:_ required and non-empty under `prod`.
- [ ] `REGISTRATION_ENABLED`: decide. The default `true` lets anyone create an account (and then
      a workspace), limited per IP; `false` makes the API invite-only, and people join through
      invitation links.
- [ ] `ADMIN_EMAIL`, `ADMIN_PASSWORD` (optional): the first system admin, a 12-72 character
      password from a secret manager. Created only when missing; an existing account is never
      promoted. A system admin manages accounts, not workspaces.
- [ ] `DEMO_DATA` and `DEMO_PASSWORD` unset. _Enforced:_ the seeder exists only in the `local` and
      `dev` profiles and never runs under `prod`. No `*@demo.test` accounts in a production
      database.
- [ ] `SPRINGDOC_ENABLED` unset: no public API docs or Swagger UI.
- [ ] `FILES_DIR` on a persistent, backed-up volume; `FILES_MAX_SIZE` at most `10MB` (the web
      server takes 11 MB per upload).
- [ ] `APP_VERSION` set (shown at `/actuator/info`).
- [ ] Rate limits left on (`RATE_LIMIT_ENABLED` unset); `RATE_LIMIT_*` at their defaults or lower.
      Never the e2e values (10,000 per IP).
- [ ] `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`: exactly the web server's addresses when it
      reaches the API from a public address, or when untrusted machines share the private
      network. By default any private or loopback address may set `X-Forwarded-For`.
- [ ] Logging at INFO; `org.springframework.security` and `org.springframework.web` never at
      DEBUG: they log request URIs, and an invitation's URI carries its token.
- [ ] Actuator: under `prod` only health, info and prometheus are exposed. Keep `/actuator/**`,
      `/actuator/prometheus` above all, off the internet; scrape it over the private network.
- [ ] **The API is reachable only from the web server** (a private network or an internal
      ingress, as in [deployment.md](../deployment.md)). It has no JSON body size cap, and it
      trusts `X-Forwarded-For` from private addresses.

## Web

- [ ] `BACKEND_URL`: the API's internal URL (`http://<api-host>:8080`), never a public one.
      _Enforced:_ the server won't start without a valid URL.
- [ ] `APP_URL`: the public `https://` URL. Only `https://` makes the session cookies `Secure`;
      the app accepts any scheme (finding I-7).
- [ ] `NEXT_PUBLIC_LOGGING_LEVEL`: `info` or above, at build (it is inlined). `env/.env.example`'s
      `debug` is for local use.
- [ ] `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`: empty, or a key restricted by HTTP referrer (the demo
      map page only; inlined at build).
- [ ] `SKIP_ENV_VALIDATION` only inside `docker build` (the Dockerfile's build stage sets it),
      never at runtime.
- [ ] Link previews: the public pages' `og:image` host is baked from `APP_URL` at build, and the
      Dockerfile takes no build argument for it, so a stock image points them at
      `http://localhost:3000` (cosmetic).
- [ ] **Never exposed directly**: only the reverse proxy reaches the server's port (3000).

## Edge (reverse proxy and TLS)

- [ ] **TLS in front of the web server, with HSTS.** The TLS terminator sends
      `Strict-Transport-Security` (`max-age=31536000`; add `includeSubDomains` only when every
      subdomain serves HTTPS) and redirects HTTP to HTTPS. The app sends its other security
      headers itself, but not HSTS: one image runs under any `APP_URL`.
- [ ] **The client's address is the last `X-Forwarded-For` entry**: overwrite the header
      (`proxy_set_header X-Forwarded-For $remote_addr;`) or append to it
      (`$proxy_add_x_forwarded_for`). Otherwise a client picks its own address and the per-IP
      sign-in and registration limits don't hold (finding W-3). Behind more than one proxy,
      adjust `clientIpFrom` in the web app.
- [ ] **The browser's `Host` is passed on**: `proxy_set_header Host $host;`, or `$http_host` when
      the public URL carries a port (`https://crm.example.com:8443`), since `$host` drops it.
      Otherwise every server action fails Next's `Origin` check ("Invalid Server Actions
      request").
- [ ] **A request body cap: 11 MB on the files page, 1 MB everywhere else.** Required: Next.js
      reads up to 11 MB of any POST before its own 413, and the files page (`/dashboard/files`,
      also under `/en`, `/fr`, `/th`) parses up to 11 MB from anyone, signed in or not. It is the
      boilerplate's upload demo; the CRM itself uploads nothing, so a 1 MB cap there too costs only
      the demo's large uploads.
- [ ] **Access logs leave out invitation paths** (`/invite/<token>`, under any locale): the token
      is a secret until the invitation is used.

In nginx, the server block in front of the web app could read:

```nginx
add_header Strict-Transport-Security "max-age=31536000" always;
client_max_body_size 1m;
proxy_set_header Host $host;                      # $http_host when the public URL has a port
proxy_set_header X-Forwarded-For $remote_addr;    # the client is the last (only) entry

location ~ ^/(?:(?:en|fr|th)/)?dashboard/files$ { # the files page's uploads
  client_max_body_size 11m;
  proxy_pass http://web:3000;
}
location / {
  proxy_pass http://web:3000;
}
```

## Database and data

- [ ] PostgreSQL 17 on a private network, reached over TLS (`sslmode=verify-full`; the Azure
      template uses `require` behind a firewall rule).
- [ ] Automated backups with point-in-time restore, and one restore tried; `FILES_DIR` backed up
      with them.
- [ ] Plan for the audit trail: it is append-only (a trigger refuses `UPDATE` and `DELETE`) and has
      no retention period, so actors' emails and every export's filters stay. Erasing a person's
      data needs a procedure of your own.

## Operations

- [ ] Ship both services' logs from stdout: the API logs JSON under `prod`, the web server JSON
      lines. Every API request carries an `X-Request-Id`, also in its log lines.
- [ ] Liveness and readiness probes on the API's `/actuator/health/liveness` and
      `/actuator/health/readiness`.
- [ ] Expect, after a web redeploy, a form opened before it to fail once ("Failed to find Server
      Action"); reloading fixes it.

## Scaling

- [ ] **One API instance.** Rate limits live in memory per instance and reset on restart, and
      uploads live on its disk. More instances need shared limits and shared storage first.
- [ ] **One web instance is what was tested.** Several serve pages fine, but requests that refresh
      a session together share one refresh only within a process: on another instance, one of
      them loses the rotation and gets the API's 401 (the session stays; the browser keeps the
      winner's cookies). Multi-instance behaviour was not tested.

## After the first deploy

Check what Phase 4 could not ([security.md](security.md#not-verified)):

- [ ] `Strict-Transport-Security` on a page; after signing in, both session cookies `Secure`,
      `HttpOnly`, `SameSite=Lax` in the browser.
- [ ] A 2 MB POST to `/sign-in` is refused by the proxy (413); a server action (any form) works.
- [ ] The API, `/actuator/prometheus` included, doesn't answer from the internet.
- [ ] The logs are JSON, and a sign-in, an invitation and a failed request leave no password,
      token or invitation link in them.
