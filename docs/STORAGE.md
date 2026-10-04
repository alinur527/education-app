# Authorized materials

`StorageService` separates metadata/business access from byte storage. PostgreSQL stores material IDs, parent IDs, bilingual title, original filename, MIME, byte count, opaque storage key, creator/time and publication flag. It does not store file binaries.

## Local development and Docker

The default provider is `local`. Direct Java execution uses `./data/materials` relative to its working directory. Override `APP_STORAGE_LOCAL_ROOT` if needed. Docker sets `/app/data/materials` and mounts the persistent `material_data` volume, writable by the non-root backend account. Temporary writes are moved to a new opaque key; existing objects are never overwritten.

Back up PostgreSQL **and** the material volume together. `docker compose down -v` destroys both. Switching the provider does not migrate existing objects: copy every key to the destination bucket before changing configuration, retain a verified backup, then exercise an authorized download. Metadata remains unchanged.

## S3-compatible provider

Use an existing private bucket and server-side credentials with only the required bucket/object permissions. Add to local `.env` or the deployment secret store:

```dotenv
APP_STORAGE_PROVIDER=s3
APP_STORAGE_S3_ENDPOINT=https://your-s3-endpoint.example
APP_STORAGE_S3_BUCKET=education-materials
APP_STORAGE_S3_REGION=us-east-1
APP_STORAGE_S3_ACCESS_KEY=your-access-key
APP_STORAGE_S3_SECRET_KEY=your-secret-key
```

The JDK HTTP adapter uses AWS Signature V4 and path-style bucket URLs for PUT/GET/DELETE, with timeouts and no redirects. The endpoint must be the API origin without a path. Use the provider's configured region, including `auto` when required by that provider. Credentials never reach frontend code. No SDK or S3 server is added to the application deployment.

The integration test exercises real PUT/GET/DELETE and rejects incorrect credentials against an isolated, digest-pinned [RustFS container](https://docs.rustfs.com/en/installation/container). This verifies S3 interoperability, not every cloud provider's policy configuration. AWS S3, R2, MinIO and other providers still need endpoint/region/bucket configuration and a deployment-specific smoke test. Providers exposing S3 under a URL path need an origin proxy or an adapter extension.

## Upload and access policy

Staff use drag-and-drop or the ordinary file picker. Upload maximum is 20 MiB; HTTP multipart maximum is 21 MiB. Supported types: PDF, DOCX, PPTX, PNG, JPEG, WebP, UTF-8 TXT and Markdown. Extension and supplied MIME must agree; file signatures are checked. Office archives require the appropriate document parts, reject traversal/macros, and have entry/expanded-size limits. Executables, SVG, unsafe filenames, control characters, path separators and `..` are rejected. These structural checks do not establish that arbitrary documents are harmless.

`MalwareScanner` is injected before storage. The ClamAV INSTREAM adapter is implemented and tested with clean bytes and EICAR. Set `APP_SCAN_ENGINE=clamav`, `APP_SCAN_REQUIRED=true`, and start the Compose `malware` profile for fail-closed staff uploads. The default `none` returns UNSCANNED; student submissions require CLEAN regardless of this flag. Legacy staff attachments retain the explicit UNSCANNED_LEGACY migration status. See [STUDENT_FILES.md](STUDENT_FILES.md) for quarantine, revisions, quotas and deployment limits.

Uploaded files are draft attachments until their parent is published. On a previously published item, save a draft, then review/publish to release newly attached files. Files inherit topic/theory/course/lesson/assignment access. Publication of a file cannot bypass an archived ancestor or missing enrollment/group assignment. Images are fetched through the same authenticated download and rendered from a browser object URL.

There is no `/public/uploads` route. Downloads use `GET /api/materials/{id}/download` with JWT, parent access checks, attachment Content-Disposition, `nosniff` and private/no-store caching. Opaque storage keys are never accepted as download paths. The production bucket must stay private. The app currently proxies downloads rather than creating public/signed bucket links.

Database rollback deletes a successfully uploaded object. A provider outage during cleanup can leave an orphan; operations should reconcile storage keys against material metadata before deleting any unreferenced object. No automatic destructive garbage collection is implemented.
