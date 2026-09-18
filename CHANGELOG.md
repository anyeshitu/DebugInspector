# Changelog

## 0.1.6

- Published `inspector-http` as a self-contained HTTP AAR without transitive inspector-api/runtime/core/ui modules.

- Hide the protocol tab bar when only one inspector feature is installed, including HTTP-only integrations.

- Added persistent multi-keyword HTTP URL exclusion filters in the inspector list.

- Show one body section when a transformer returns unchanged content, avoiding duplicate raw/plaintext response panels for unencrypted responses.

- Fixed Retrofit typed-tag lookup so annotation-driven request/response transformers receive `Invocation` on OkHttp 4.x hosts.

- Initial legacy-compatible Phase 1 SDK.
- HTTP raw/transformed capture, Header redaction and text/cURL/JSON/HAR sharing.
- Manual WebSocket and serial event reporting.
- Read-only SQLite discovery, browsing and CSV/JSON export.
- Database table rows rendered as a horizontally scrollable column grid with row details.
- CSV/JSON database exports use a folder picker and explicit confirmation before writing.
- Chucker-style HTTP details with expanded headers, formatted JSON bodies and contextual copy/share format selection.
- Optional HTTP, WebSocket, serial and database feature artifacts with dependency-driven menu visibility.
- Simplified Chinese platform View/XML UI with English fallback.
- Full debug and release no-op integration artifacts.

### HTTP inspector compatibility updates

- Added Chucker-compatible `alwaysReadResponseBody`, `skipPaths`, `skipPathPatterns`,
  `skipDomains` and `skipDomainPatterns` configuration hooks.
- Added long-press body copy selection for formatted or raw payloads while retaining
  the request URL context.
- Fixed HAR `startedDateTime` to use ISO-8601 UTC text and restricted the HTTP
  `4xx/5xx` filter to status codes 400 through 599.
## 1.0.0

- Consolidate the HTTP inspector UI and runtime into the first stable release.
- Add notification entry retry and Android-compatible HTTP inspection integration.
