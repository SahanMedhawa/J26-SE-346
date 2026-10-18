# Repository browsing for implementation mappings

The existing Architecture Project `repositoryUrl` field is reused. Set or edit it through the
existing project registration/update APIs. Project registration still supports its existing
HTTP/HTTPS repository URLs; only the browsing endpoint requires a GitHub repository URL.
Supported browsing URLs are `https://github.com/owner/repository`, optionally ending in `.git`
or `/`. SSH URLs, ports, credentials, query strings, fragments, and nested GitHub page URLs
are rejected. Projects with other repository hosts remain retrievable and editable.

No database change is needed for browsing. Existing migrations V1–V5 and all mapping code
are preserved. V5 stores each selected path as a mapping UUID, rule UUID, SOURCE/TARGET side,
and path; its unique constraint prevents duplicate paths per side of a rule. Project ownership
comes through the rule's project UUID. Browsing never writes mapping or repository data.

## Configuration

Set these environment variables on the backend application process before starting it:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AKEGP_GITHUB_TOKEN` | empty | Optional GitHub token, used only in outbound Authorization headers |
| `AKEGP_GITHUB_CONNECT_TIMEOUT_MS` | `5000` | Connection timeout for each request |
| `AKEGP_GITHUB_READ_TIMEOUT_MS` | `15000` | Socket read timeout for each request |
| `AKEGP_GITHUB_MAX_RESPONSE_BYTES` | `8388608` | Maximum bytes per GitHub response, enforced while reading |
| `AKEGP_GITHUB_MAX_FILES` | `20000` | Maximum selectable files |

Public repositories work without a token. For private repositories, supply a token authorized
for that repository with read access to repository metadata and contents. Do not put the token
in project URLs, requests from the frontend, source files, or PostgreSQL. Configure the variables
in the application's environment, including the IDE run configuration when starting from IDEA.
Timeouts must be positive. The byte limit is bounded at 64 MiB and the file limit at 100,000.

Each browse makes at most two GET requests: repository metadata to discover `default_branch`,
then a recursive Git tree request for that branch. Only blob paths are returned; directories and
submodule commits are excluded. Files are sorted, and file contents are never downloaded.
All requests target `https://api.github.com`; redirects and automatic retries are disabled.

## Postman demonstration

Set `baseUrl` (for example `http://localhost:8080`), `projectId`, and `ruleUuid` in Postman.
Use a project whose existing repository URL points to the desired GitHub repository and a
rule belonging to that project. The browser requires no request body or frontend GitHub token.

```http
GET {{baseUrl}}/api/rules/projects/{{projectId}}/repository/files
```

Example HTTP 200 response (the branch is determined from GitHub):

```json
{
  "projectId": "<project UUID>",
  "repositoryUrl": "https://github.com/owner/repository",
  "branch": "develop",
  "files": ["db/V1.sql", "src/Order.java", "src/OrderController.java"]
}
```

Save selections using the unchanged mapping API:

```http
PUT {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/implementation-mappings
Content-Type: application/json
```

```json
{
  "sourceFiles": ["src/Order.java", "src/OrderController.java"],
  "targetFiles": ["db/V1.sql"]
}
```

HTTP 200 response:

```json
{
  "ruleUuid": "<rule UUID>",
  "sourceFiles": ["src/Order.java", "src/OrderController.java"],
  "targetFiles": ["db/V1.sql"]
}
```

```http
GET {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/implementation-mappings
```

GET has no request body and returns the saved mapping response above. PUT replaces both lists;
send `{"sourceFiles":[],"targetFiles":[]}` to clear them. Mapping validation remains local:
paths are repository-relative strings, not proof of an architectural implementation. Saving
does not contact GitHub and still works if GitHub is unavailable.

## Browsing errors

Errors use the existing `ApiError` response with timestamp, status, error, message, and fieldErrors.

| HTTP status | Meaning |
| --- | --- |
| 400 | Invalid project UUID, missing repository URL, or unsupported/unsafe repository URL |
| 404 | Project missing, or GitHub repository/default branch missing or inaccessible |
| 403 | GitHub rejected the configured token or its permissions |
| 409 | GitHub has no available tree, for example an empty repository |
| 429 | GitHub rate limit; numeric upstream `Retry-After` is forwarded when supplied |
| 502 | Upstream failure, redirect, malformed/incomplete/truncated response, or configured limit exceeded |
| 503 | GitHub connection failure or timeout |

An incomplete file list is never returned as a successful response. A renamed repository that
redirects must have its project URL updated. There is no fallback recursion or unbounded retry.

## Verification

From the repository root with JDK 25 and Maven:

```text
mvn -pl akegp-rules -am test
mvn -pl akegp-rules -am package
```

GitHub tests use mocked HTTP responses and do not require network access. Existing PostgreSQL
tests run only when `AKEGP_TEST_POSTGRES_URL` is configured, with optional
`AKEGP_TEST_POSTGRES_USER` and `AKEGP_TEST_POSTGRES_PASSWORD`. They use isolated random schemas.
Do not substitute the production Neon URL just to run the test suite.
