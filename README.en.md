[中文](README.md) | [English](README.en.md)

<div align="center">
<img src="frontend/public/brand/logo.jpg" alt="ZhiHua Technology logo" width="120" />

# PlotTrial · Field Variety Trials and Plot Observations

**Public source for learning / non-commercial use** · **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**

[Official website](https://www.zhuatech.cn/) · [User manual](docs/操作手册.md) · [API reference](docs/接口说明.md) · [Architecture and data](docs/架构与数据.md) · [Security](SECURITY.md)
</div>

## From a trial plan to traceable field records

Field variety comparisons need to connect experimental design, actual plots, observations and revisions. Rearranging treatments, filling missing values with zero or overwriting measurements undermines later summaries. PlotTrial is for small trial teams, agricultural research groups and teaching trials: it records variety treatments, randomized complete blocks, assigned observations and independent data review.

Java 21 / Spring Boot, Vue 3, MySQL and Flyway provide the record workflow, data authorization and version history. The detailed manuals linked above are currently in Chinese.

This release provides records, authorization and descriptive statistics. It does not choose the best variety, recommend pesticides/fertilizers/cultivation, perform significance tests or ANOVA, prepare statutory variety applications or generate research conclusions. Qualified people remain responsible for trial design and actual observations. The [SARE introduction to randomized complete block designs](https://www.sare.org/publications/how-to-conduct-research-on-your-farm-or-ranch/basics-of-experimental-design/common-research-designs-for-farmers/) is a learning reference; the application does not represent certification by that organization.

## Implemented workflow

1. Designers create field sites and draft trials with crop, objective, plot area and **4–16 blocks**.
2. Define **2–12 variety treatments with exactly one control** and up to 20 numeric measurements, with at least one required. Each measurement declares units, allowed range and required status.
3. Assign an independent reviewer who has not edited the plan. Approval freezes plan/treatments/measurements; rejected or withdrawn drafts remain revisable.
4. The server generates a within-block randomized layout exactly once. Every block contains all enabled treatments. It stores a 256-bit seed, `SHA256-FY32-v1` version and layout digest, with at most 192 plots.
5. Designers assign each plot to an observer, who personally acknowledges. Once all are ready, record the actual start date. Observers become fixed after starting and register actual planting dates per plot.
6. Assigned observers enter numeric values or explicit missing reasons, dates and notes. Independent review accepts or returns them; withdrawal/cancellation retain evidence. Input values have at most four decimal places and are not automatically rounded.
7. Corrections append a new version pointing to the currently accepted version. The old accepted fact remains current until the correction is accepted; accepted records are never overwritten in place.
8. Plot exclusion requires a request and independent acceptance, preserving all observations. Normal ending requires planting and accepted required measurements on every nonexcluded plot, with no pending observations/exclusion requests.
9. The independent reviewer can return a trial for further observations or close it with a frozen data digest. `ABORTED` and `FINISHED` remain distinct. Closed business facts cannot be edited.
10. Authorized summaries show valid N, missing, excluded, unrecorded, mean and extrema. Export complete JSON evidence and CSV including every observation revision.

| Module | Available operations |
|---|---|
| Field trials | Draft, review, random allocation, assignments, start, finish, abort and close |
| Field sites | Create, versioned editing and enable/disable while retaining associated identity |
| Trial definitions | Varieties, control, measurements, units, ranges and required rules |
| Plot observations | Acknowledgment, planting dates, values/missing responses, corrections, independent acceptance and exclusion |
| Descriptive statistics | Current accepted, nonexcluded data only; no significance judgment |
| Administration | Accounts, roles, departments, menus, permission descriptions, crop dictionaries, settings and audit |

**No models or external business services are required.** Local business workflows use an actual database. There is no AI, fabricated demo-data mode, device interface, notifications, attachment upload, inventory or membership-course functionality. Business tables are empty at first startup.

## Business and administrative interfaces

The observer workspace lists only assigned trials/plots. An observer-only role cannot read someone else's plots/observations even with ALL scope. Designers maintain plans/assignments within department or SELF scope. An assigned reviewer receives responsibility for that trial; ALL scope does not replace explicit assignment.

Administrators maintain accounts, roles, departments, enabled menus, crop types and settings. Registered permission/menu codes can have descriptions/configuration maintained; administrators cannot invent permissions for nonexistent interfaces. The last enabled ALL-scope administrator is protected. Password hashes do not appear in interfaces/exports. Role and enabled-status changes take effect on subsequent requests.

### Actual running pages

The `TEST` records shown below are inputs to an independent acceptance environment, not automatically generated business examples at first startup.

**Login and observer workspace**

![Login](docs/screenshots/login.jpg)

Login: session authentication into the workspace.

![Observer workspace](docs/screenshots/observer-home.jpg)

Observer workspace: personally assigned trials and plots.

**Plans, plots and observations**

![Trial plan](docs/screenshots/plan.jpg)

Trial plan: variety treatments, the control and numeric measurements.

![Randomized layout](docs/screenshots/plots.jpg)

Randomized layout: frozen complete blocks and observer assignments.

![Observations and revisions](docs/screenshots/observations.jpg)

Observations: actual values or missing responses, appended corrections and retained review history.

**Administration, permissions, statistics and settings**

![Accounts](docs/screenshots/users.jpg)

Accounts: users, departments, roles and enabled status.

![Roles and permissions](docs/screenshots/roles.jpg)

Roles: interface permissions and data scopes.

![Trial statistics](docs/screenshots/dashboard.jpg)

Statistics: authorized trial progress and descriptive summaries.

![System settings](docs/screenshots/settings.jpg)

Settings: supported workspace name/capacity settings.

**English and mobile pages**

![English interface](docs/screenshots/english.jpg)

English interface: English operation pages.

![Mobile interface](docs/screenshots/mobile.jpg)

Mobile interface: narrow-screen trial record operations.

## Technology and structure

| Layer | Version/implementation |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Spring Security and JPA |
| Frontend | Vue 3.5.40, Vite 8.1.5, Node 24.19.0+, npm 11 and Lucide icons |
| Data | MySQL 8.4, Flyway V1/V2 and BCrypt password hashes |
| Deployment | Docker Engine 27+, Docker Compose v2 and same-origin Nginx proxy |
| Checks | JUnit/MockMvc/H2, Node tests, Spotless, ESLint and Prettier |

```text
backend/                 Identity, transactions, random allocation, observations, migrations
frontend/                Chinese/English business and administration interfaces
scripts/init-env.py      Independent random local password generation
scripts/smoke.py         Isolated actual HTTP/MySQL acceptance and persistence comparisons
scripts/release-check.py Brand, original QR images, screenshots, license and sensitive scans
docs/                    Operations, architecture, API, actual screenshots and third-party notices
compose.yaml             Three-service startup and health checks
```

Browser → Nginx → Spring Boot → MySQL. No database host port is exposed; the default web binding is loopback only. Writes use sessions and CSRF. Business writes serialize within database transactions and check record versions, scopes, roles and states.

## First run with Docker Compose

Requirements: Docker Engine, Compose v2 and Python 3.10+. Initial builds need Maven Central, npm and official image registries. At least 4 GB available memory is recommended for this application's local setup.

```sh
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait
```

Open **[http://127.0.0.1:8130/](http://127.0.0.1:8130/)** with username `admin`; read `ADMIN_PASSWORD` in the local, ignored `.env`. No fixed public password is provided. The initialization script refuses to overwrite an existing file and writes independent random passwords with mode 0600. Keep them private.

The [health endpoint](http://127.0.0.1:8130/actuator/health) normally includes `"status":"UP"`.

If occupied, override `WEB_PORT`, for example 18130, in local configuration and restart this project. Do not stop another project. `docker compose down` retains volumes. **`down -v` deletes this Compose project's database volume and is only for explicitly disposable acceptance resources.**

### Configuration

| Name | Meaning |
|---|---|
| `DATABASE_PASSWORD` | Required independent database application password |
| `MYSQL_ROOT_PASSWORD` | Required initial database administration password |
| `ADMIN_PASSWORD` | Required only when initializing an administrator in an empty database |
| `WEB_PORT` | Host web port, default 8130 |
| `BIND_ADDRESS` | Default 127.0.0.1; external deployment requires trusted HTTPS and network policies |
| `COOKIE_SECURE` | `false` for local HTTP; `true` for external HTTPS |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG` | Direct backend-process connection/account/catalog overrides; Compose uses its internal MySQL |

[.env.example](.env.example) lists configuration names. Never commit actual configuration. Changing `ADMIN_PASSWORD` does not reset an existing database account.

## Source development

Provide the project MySQL service or an independent MySQL 8.4 database. For the host backend, configure a safe local connection using `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DATABASE_CATALOG` and `ADMIN_PASSWORD`:

```sh
mvn -f backend/pom.xml spring-boot:run
```

In another terminal at the repository root, use Node 24.19.0+ / npm 11:

```sh
cd frontend
npm ci
npm run dev
```

The development interface at `http://127.0.0.1:5173` proxies `/api` to backend port 8080. The default Compose database is not exposed to the host; host development can use a separately configured local test database. Do not put passwords in source.

## Database initialization and upgrades

There are 19 tables including Flyway history. V1 manages accounts, roles, role permissions, departments, permissions, menus, dictionaries, settings and audit. V2 manages sites, trials, past editors, treatments, measurement definitions, plots, observation revisions, request keys and evidence events.

An empty database creates one administrator, headquarters, four roles, 12 permissions, 11 menus, four crop categories and settings. It creates no sites, trials or fabricated measurements. MySQL volumes persist all business facts. Manual dates use SQL DATE; software facts use UTC microseconds and the interface uses Shanghai time.

Before upgrades, stop business writes and preserve database backups and image versions; verify independent restoration. New migrations are checked/applied by Flyway. **Do not modify applied migrations or enable automatic schema creation that overwrites data.** There is no legacy-system import, automatic downgrade or business-deletion feature. Roll back with matching application versions and verified backups. Keep restricted backups outside the source repository.

## Validation

```sh
mvn -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Backend integration tests initialize isolated H2 with a dynamically generated random password. They cover complete blocks, freezing, scopes, review, missing observations, revisions, exclusions, concurrency, retries and CSV. H2 does not replace MySQL acceptance. Backend image builds execute tests without skipping them.

After starting an **independent, fresh, disposable acceptance project and volume**:

```sh
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
git diff --check
```

Only explicit test-write mode creates TEST business records/random test accounts. Private comparison state is in ignored `output/qa-state.json` with mode 0600; do not publish passwords or business responses. `--verify` compares saved responses, including independent restores selected through `TEST_URL`. Refresh `--capture` after screenshot/page operations, then compare restart and backup-restoration behavior. CI runs formatting, tests, frontend builds and public-asset checks. Full Docker startup/workflow/restoration requires separate isolated acceptance.

## Troubleshooting

- **Login rejected:** check initial credentials, enabled account and correct database. Eight consecutive failures trigger a five-minute limit. Never post real passwords in issues.
- **Startup failure:** check required `.env` fields, Docker networking, memory, migrations and this project's logs. Rebuild only this project's services when changing its port.
- **Plan submission rejected:** require at least two enabled treatments, exactly one control and at least one enabled required measurement.
- **Approval rejected:** use the assigned independent reviewer, who must not have edited the protocol. Administrator status does not replace the assignment.
- **Starting rejected:** every plot must have an assigned observer who personally acknowledged and remains enabled/authorized.
- **Ending rejected:** resolve pending observations/exclusions; plant every nonexcluded plot and accept every required measurement. Explicit missing reasons can be reviewed, but cannot be replaced by artificial zeros.
- **Correction/version conflict:** refresh. Append a correction referring to the current accepted record rather than creating an unrelated new original measurement.
- **Mean is empty:** there is no current valid numeric value for that treatment/measurement. Missing/excluded observations are not zero and do not increase N.

## Security, contributions and license

HttpOnly/SameSite Strict cookies, CSRF for writes, BCrypt, immediate account/role checks, last-administrator protection, bounded pagination, field whitelists, business versions and UUID retries protect data. CSV escapes formula prefixes while retaining separate numbers/missing responses. There is no multi-tenancy, HA, device identity, electronic signing, compliance certification or medical functionality. External deployment requires HTTPS, trusted proxies, database backups and access controls; see [SECURITY](SECURITY.md).

Reproducible issues/PRs are welcome; see [CONTRIBUTING](CONTRIBUTING.md). Provide redacted steps, versions and error codes, not actual field records, passwords, backups or personal data. Do not post detailed security exploits publicly; contact ZhiHua privately through the channels below.

The project's own code uses the [ZhuaTech Non-Commercial Source License 1.0](LICENSE): personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** It does not automatically grant brand/trademark rights. Third-party licenses remain separate; see [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md). Commercial deployment, integration and customization need separate written authorization. This is publicly readable source, not an OSI-approved open-source license.

The software is provided as is. Records/descriptive statistics do not prove sound experimental design, truthful observations or research conclusions and do not constitute agricultural-production advice. Users remain responsible for their data, backups and suitability assessment. No unverified production-readiness claim is made.

## Contact ZhiHua Technology

For commercial licensing, private deployment, source customization, system integration or in-depth custom development, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
