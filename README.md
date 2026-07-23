# NetSuite Custom JDBC Connector

A JDBC driver wrapper for NetSuite SuiteAnalytics Connect that solves two problems with JetBrains IDE integration:

1. **Automatic nonce generation** — NetSuite's token-based auth requires a fresh HMAC-SHA256 signed nonce for every connection. This driver generates it transparently so you don't get re-authentication prompts.

2. **JetBrains introspection compatibility** — The stock OpenAccess driver triggers DBMS mismatch errors in JetBrains' schema introspector. This wrapper normalizes metadata responses so schema discovery works out of the box.

## Prerequisites

- JDK 8+ (build targets Java 8 bytecode)
- The base NetSuite JDBC driver (`NQjc.jar`) placed at `ref-binaries/netsuite-jbdc.jar`

## Build

```bash
./build.sh
```

Output: `out/netsuite-jetbrains-driver.jar` — a single fat JAR containing both the wrapper and the base driver.

## JetBrains Setup

1. In Database tool window, add a new Driver:
   - **Driver files:** Add `out/netsuite-jetbrains-driver.jar`
   - **Class:** `com.netsuite.jetbrains.NetsuiteJetbrainsDriver`

2. Create a new Data Source using this driver:
   - **URL:** `jdbc:ns://<host>:<port>;ServerDataSource=NetSuite2.com;Encrypted=1;NegotiateSSLClose=false;CustomProperties=(AccountID=<acct_id>;RoleID=<role_id>;GenerateNonce=true)`
   - **Password:** A JSON object with your nonce credentials:
     ```json
     {"accountId":"...","consumerKey":"...","consumerSecret":"...","tokenId":"...","tokenSecret":"..."}
     ```
   - **Dialect:** NetSuite (for SuiteQL syntax) or GenericSQL

3. Set the data source DBMS to **Auto-detect** or **GenericSQL** so the generic introspector is used.

## How It Works

When `GenerateNonce=true` is present in the JDBC URL's `CustomProperties`:

1. The password field is parsed as JSON containing the five credential fields
2. A fresh nonce and timestamp are generated
3. The base string (`accountId & consumerKey & tokenId & nonce & timestamp`) is signed with HMAC-SHA256 using `consumerSecret & tokenSecret` as the key
4. The computed password is passed to the underlying OpenAccess driver

When `GenerateNonce` is absent or false, the driver passes through to the stock NetSuite driver with zero overhead.

### Introspection Fixes

- `getDatabaseProductName()` returns `"GenericSQL"` to prevent JetBrains from mapping `"OpenAccess"` to its NETSUITE DBMS constant (which triggers the Oracle introspector and a `DbmsMismatchException`)
- Catalog names are normalized (sandbox suffixes like `_SB1` are stripped)
- Oracle-specific queries (`SYS_CONTEXT`, `DUAL`, `DBA_USERS`) are stubbed with sensible values

## Debug Logging

To diagnose introspection issues, enable debug logging by adding this JVM argument to your JetBrains configuration:

```
-Dnetsuite.jdbc.debug=true
```

Logs are written to `/tmp/netsuite-jdbc.log` and include all JDBC method calls, arguments, and return values flowing through the wrapper.

## Project Structure

```
├── build.sh                          Build script (produces fat JAR)
├── ref-binaries/
│   └── netsuite-jbdc.jar            Base NetSuite JDBC driver (vendor binary)
├── src/
│   ├── META-INF/services/
│   │   └── java.sql.Driver          Service registration
│   └── com/netsuite/jetbrains/
│       ├── NetsuiteJetbrainsDriver.java   Main wrapper driver
│       ├── NonceCredentials.java          JSON credential parser
│       ├── NonceGenerator.java            HMAC-SHA256 nonce signer
│       ├── ConnectionWrapper.java         Connection proxy (metadata interception)
│       ├── MetaDataWrapper.java           DatabaseMetaData normalization
│       ├── StatementInterceptor.java      Oracle query stubbing
│       ├── CatalogStripper.java           Sandbox suffix removal
│       ├── CatalogRewriteResultSet.java   ResultSet catalog rewriting
│       ├── DatabaseMetaDataDelegate.java  Metadata proxy base class
│       ├── ResultSetDelegate.java         ResultSet proxy base class
│       ├── SimpleResultSet.java           In-memory ResultSet for stubs
│       └── JdbcLogger.java               Debug logger (off by default)
└── out/
    └── netsuite-jetbrains-driver.jar     Build output (gitignored)
```
