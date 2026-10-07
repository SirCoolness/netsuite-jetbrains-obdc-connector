# NetSuite Custom JDBC Connector

A JDBC driver wrapper for NetSuite SuiteAnalytics Connect that makes the stock OpenAccess driver work in JetBrains IDEs (PhpStorm, DataGrip, IntelliJ IDEA; tested with PhpStorm 2026.2):

1. **Automatic nonce generation.** NetSuite's token-based auth (TBA) needs a fresh HMAC-SHA256 signed nonce for every login. The driver signs one for each connection, so you don't get re-authentication prompts, and it reconnects with a new nonce when NetSuite expires an idle session.

2. **Schema introspection that completes.** The stock driver makes JetBrains pick its Oracle introspector, which fails with `DbmsMismatchException`. The wrapper steers the IDE to the generic introspector and normalizes the metadata NetSuite returns, so tables, columns, primary keys and foreign keys show up.

## Prerequisites

- JDK 8+ to build (the jar targets Java 8 bytecode)
- The base NetSuite JDBC driver (`NQjc.jar`) at `ref-binaries/netsuite-jbdc.jar`
- A NetSuite integration record and access token with the SuiteAnalytics Connect permission, and the id of the role the token belongs to

## Build

```bash
./build.sh
```

Output: `out/netsuite-jetbrains-driver.jar`, a single jar holding the wrapper and the base driver.

## JetBrains setup

### 1. Install the jar at a stable path

```bash
NETSUITE_ACCOUNT_ID=1234567_SB1 NETSUITE_ROLE_ID=57 helper-scripts/install-jetbrains-driver.sh
```

This builds the jar if needed, copies it to `~/.local/share/netsuite-jdbc/netsuite-jetbrains-driver.jar` (set `NETSUITE_JDBC_HOME` to change that), and renders the templates from `config-templates/` into `~/.local/share/netsuite-jdbc/jetbrains/`:

| File | What it is |
|---|---|
| `jdbc-url.txt` | The data source URL for your account and role |
| `databaseDrivers.xml` | The driver definition (for reference, or to merge into the IDE's `options/databaseDrivers.xml` while the IDE is closed) |
| `dataSources.xml`, `dataSources.local.xml` | A project data source (for `<project>/.idea/`), with user `TBA` and a one-schema introspection scope |

The account and role ids can also come from a file: `--env-file path/to/netsuite.env`. The script never prints secrets and never edits your IDE or project configuration. Run it again after every rebuild; the driver jar path stays the same, so the IDE picks up the new jar.

Don't point the IDE at `out/` or `~/Downloads`: a rebuild or cleanup would silently swap or remove the driver.

### 2. Create the driver

**Database** tool window > **+** > **Driver**:

- **Name:** `NetSuite (TBA wrapper)`
- **Driver Files:** **+** > **Custom JARs** > `~/.local/share/netsuite-jdbc/netsuite-jetbrains-driver.jar`
- **Class:** `com.netsuite.jetbrains.NetsuiteJetbrainsDriver`
- **Dialect:** leave it on **Generic SQL** (or unset). Do not pick NetSuite or Oracle: those dialects use JetBrains' Oracle introspector, which rejects this connection with `DbmsMismatchException`.
- Options tab: leave **Auto commit** on (the default).

Don't base it on the IDE's built-in "NetSuite" driver template; create a plain user driver.

### 3. Create the data source

**+** > **Data Source** > `NetSuite (TBA wrapper)`:

- **Connection type / URL only:** paste the URL from `jdbc-url.txt`:

  ```
  jdbc:ns://<account-host>.connect.api.netsuite.com:1708;ServerDataSource=NetSuite2.com;Encrypted=1;NegotiateSSLClose=false;CustomProperties=(AccountID=<account-id>;RoleID=<role-id>;GenerateNonce=true)
  ```

  `<account-host>` is the account id in lower case with `_` replaced by `-` (`1234567_SB1` becomes `1234567-sb1`). `GenerateNonce=true` (or `1`) may appear anywhere inside `CustomProperties`; the driver removes it before calling NetSuite.
- **User:** `TBA`
- **Password:** the credential JSON, on one line:

  ```json
  {"accountId":"...","consumerKey":"...","consumerSecret":"...","tokenId":"...","tokenSecret":"..."}
  ```

  `helper-scripts/generate-password.sh` builds it from `NETSUITE_*` environment variables (or prompts, without echo) and copies it to the clipboard without printing it.
- **Save:** **Forever**. The IDE opens extra connections for introspection and consoles; each one needs the password. Without it the driver fails with "GenerateNonce=true but no password was supplied".

Click **Test Connection**.

### 4. Introspect one schema

On the data source's **Schemas** tab, select only the current schema, not "All schemas". NetSuite exposes one schema per role (e.g. `Data Warehouse Integrator`), and the driver reports it as the current schema, so "current schema" and that schema name are the same thing. The rendered `dataSources.local.xml` already selects it.

The first introspection is slow and that is expected. NetSuite builds its metadata on the server the first time a session asks for it: listing ~2,700 tables cold takes about a minute, and the first primary key lookup in a session can take another minute. Later refreshes are much faster. Avoid "introspect all columns of all schemas" style operations; a bulk `getColumns` over the whole account can run for many minutes on the server.

### 5. Run SuiteQL

Open a console on the data source and run queries, for example `SELECT id, companyname FROM customer WHERE ROWNUM <= 10`. SuiteAnalytics Connect is read-only.

## Debug logging

JetBrains runs JDBC drivers in a separate process, so the flag goes into the data source, not the IDE's own VM options. Data source properties > **Advanced** > **VM options**:

```
-Dnetsuite.jdbc.debug=true
```

Then disconnect and reconnect the data source.

Calls through the wrapper are logged to `<java.io.tmpdir>/netsuite-jdbc.log` (`/tmp/netsuite-jdbc.log` on Linux). Use `-Dnetsuite.jdbc.log=/path/to/file` to log elsewhere. The file is created readable by you only. Account ids, user names, passwords, credential JSON and generated nonces are redacted from every line, but the log still contains SQL text and table names, so delete it when you're done.

## Troubleshooting

| Symptom (IDE message or `idea.log`) | Cause | Fix |
|---|---|---|
| `GenerateNonce=true but no password was supplied` | The IDE opened a connection without the password (password not saved, or saved for this session only). | Set the password JSON and choose **Save: Forever**. |
| `Null connection returned for: jdbc:ns://...GenerateNonce=true` | Older wrapper jar, which returned no connection when the password was missing. | Install the current jar (step 1) and save the password. |
| `Unable to parse provided authentication string` | The JSON password reached NetSuite unsigned: `GenerateNonce=true` is missing from `CustomProperties`, or the driver class is the stock `OpenAccessDriver`. | Check the URL and the driver class `com.netsuite.jetbrains.NetsuiteJetbrainsDriver`. |
| `GenerateNonce JSON parse error` / `missing required field` | The password is not the five-field JSON object. | Regenerate it with `helper-scripts/generate-password.sh`. |
| `DbmsMismatchException: Introspector expects a connection to ORACLE but got to NETSUITE` | The IDE uses its Oracle introspector: the driver or data source dialect is NetSuite/Oracle, or an old jar reports the product as OpenAccess. | Set the driver dialect to Generic SQL, install the current jar, then **Forget Cached Schemas** and refresh. |
| `Connection expired. Please reconnect.` | NetSuite closed an idle session. | Handled automatically: reads reconnect with a fresh nonce and retry once. If it still appears, the statement was not a plain `SELECT`/`WITH`; run it again. |
| Hundreds of primary keys on one table, or no foreign keys | Older wrapper jar (NetSuite returns one key per referencing foreign key, and a different catalog name on key results). | Install the current jar, then **Forget Cached Schemas** and refresh. |
| Introspection takes minutes | Cold server-side metadata, or the scope includes every schema. | Introspect only the current schema (step 4) and let the first run finish. |
| Login fails after it used to work | Token revoked, wrong role id, or the machine clock is off (the nonce is timestamped). | Check the token and role in NetSuite and sync the clock. |

## How it works

When `GenerateNonce=true` is present in the JDBC URL's `CustomProperties`:

1. The password field is parsed as JSON containing the five credential fields
2. A fresh nonce and timestamp are generated
3. The base string (`accountId & consumerKey & tokenId & nonce & timestamp`) is signed with HMAC-SHA256 using `consumerSecret & tokenSecret` as the key
4. The computed password is passed to the underlying OpenAccess driver

Steps 2 to 4 repeat for every connection and every automatic reconnect. Without `GenerateNonce` (or with `GenerateNonce=false`) the password goes to the stock driver unchanged.

### Introspection fixes

- `getDatabaseProductName()` returns `"GenericSQL"`. JetBrains maps a product name containing "OpenAccess" to its NETSUITE DBMS, whose dialect extends Oracle; the Oracle introspector then insists on an ORACLE connection and throws `DbmsMismatchException`. An unknown product name selects the generic JDBC introspector.
- Statements, result sets, metadata and `unwrap()` always hand back the wrapper's objects, never the raw OpenAccess ones, so no code path sees "OpenAccess" again.
- Catalog columns of every metadata result (`TABLE_CAT`, `PKTABLE_CAT`, `FKTABLE_CAT`, ...) have the sandbox suffix (`_SB1`) stripped, so keys and tables agree. Table names are never changed.
- `getPrimaryKeys` returns one key per table (named `pk_<table>`), not one per referencing foreign key. When the server's candidates disagree, the key with the most columns wins (e.g. `transactionLine` gets `(transaction, id)`), then one containing `id`.
- `getImportedKeys` for all tables, which NetSuite refuses, is answered from `getExportedKeys` for all tables, so bulk foreign key loading works.
- `getSchema()` returns the role's schema instead of the user name (`TBA`) NetSuite reports.
- Catalogs are reported as not usable in SQL, because NetSuite rejects `"catalog"."schema".table`; the IDE qualifies names by schema only.
- `SYSTEM TABLE` is hidden from table types, which skips a slow scan of the server's system schema.
- Metadata calls NetSuite does not support (`getFunctions`, key and index lookups without a table, ...) return empty results instead of errors, so introspection finishes.

## Project structure

```
├── build.sh                          Build script (produces the fat JAR)
├── config-templates/                 JetBrains driver/data source templates (placeholders only)
├── helper-scripts/
│   ├── install-jetbrains-driver.sh   Installs the jar at a stable path, renders the templates
│   └── generate-password.sh          Copies the credential JSON to the clipboard
├── ref-binaries/
│   └── netsuite-jbdc.jar             Base NetSuite JDBC driver (vendor binary)
├── src/
│   ├── META-INF/services/
│   │   └── java.sql.Driver           Service registration
│   └── com/netsuite/jetbrains/
│       ├── NetsuiteJetbrainsDriver.java   Driver: URL handling, nonce passwords
│       ├── NonceCredentials.java          JSON credential parser
│       ├── NonceGenerator.java            HMAC-SHA256 nonce signer
│       ├── ConnectionWrapper.java         Connection proxy, reconnect on expiry
│       ├── StatementWrapper.java          Statement proxy, retries expired SELECTs
│       ├── MetaDataWrapper.java           DatabaseMetaData normalization
│       ├── CatalogStripper.java           Sandbox suffix removal
│       ├── CatalogRewriteResultSet.java   Catalog rewriting for metadata results
│       ├── ResultSetDelegate.java         ResultSet proxy base class
│       ├── SimpleResultSet.java           In-memory ResultSet for rewritten metadata
│       ├── Proxies.java                   Shared proxy helpers
│       └── JdbcLogger.java                Redacting debug logger (off by default)
└── out/
    └── netsuite-jetbrains-driver.jar     Build output (gitignored)
```
