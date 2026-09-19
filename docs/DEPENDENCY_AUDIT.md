# Backend dependency audit — 2026-09-19

`npm audit --json`: 12 vulnerable package entries, 5 high and 7 moderate; zero critical. This is a registry advisory result, not an exploitability assessment. No forced upgrades were applied because proposed fixes include major dependency migrations.

| Package | Severity | Direct | Suggested fix |
|---|---|---|---|
| @nestjs/core | high | True | @nestjs/core 12.0.3 (major change) |
| @nestjs/platform-express | high | True | @nestjs/platform-express 12.0.3 (major change) |
| @nestjs/swagger | high | True | @nestjs/swagger 12.0.1 (major change) |
| @vitest/mocker | moderate | False | vitest 5.0.1 (major change) |
| decode-uri-component | moderate | False | minio 7.1.3 (major change) |
| minio | moderate | True | minio 7.1.3 (major change) |
| multer | high | False | @nestjs/platform-express 12.0.3 (major change) |
| qs | moderate | False | True |
| query-string | moderate | False | minio 7.1.3 (major change) |
| sharp | high | True | True |
| stream-json | moderate | False | minio 7.1.3 (major change) |
| vitest | moderate | True | vitest 5.0.1 (major change) |

Advisory references:
- [Vitest: Path Traversal / Arbitrary File Read via @vitest/mocker Redirect Mock](https://github.com/advisories/GHSA-82fw-gwwq-j7x9)
- [decode-uri-component: Denial of service via exponential decoding of malformed percent-encoded input](https://github.com/advisories/GHSA-vcc3-ghjq-m6fr)
- [multer vulnerable to Denial of Service via crafted multipart field names](https://github.com/advisories/GHSA-wc9g-mqfw-jrwm)
- [multer vulnerable to Denial of Service via file descriptor leak on aborted uploads](https://github.com/advisories/GHSA-qfvm-cv95-jqjf)
- [multer vulnerable to file size limit bypass via async fileFilter race condition](https://github.com/advisories/GHSA-qvfw-j98x-7q72)
- [multer vulnerable to Denial of Service via oversized array index in field names](https://github.com/advisories/GHSA-535w-7cp7-47q4)
- [qs array-limit bypass via bracket-key comma parsing](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx)
- [qs: Denial of Service via Attacker Controlled isBuffer](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g)
- [sharp: Vulnerabilities in libheif: GHSA-g89c-p67h-r497 and GHSA-2jg2-4ch7-h545](https://github.com/advisories/GHSA-rgj7-g3m4-5g8c)
- [stream-json: pick/ignore/filter/replace filters are O(depth²) on nested input — small crafted JSON blocks the event loop for seconds→minutes (DoS)](https://github.com/advisories/GHSA-528h-pc64-c93x)
