# couch-recap: "Catch me up" Lambda (Amazon Nova on Bedrock)
- Prereqs: AWS CLI v2 on PATH, a live session (`aws login`), Amazon Nova available in Bedrock in us-east-2 (uses the `us.*` inference profiles).
- Deploy: `powershell -File recap\deploy.ps1`. Creates/updates the function and its Function URL, generates a `RECAP_TOKEN` once, and writes `RECAP_URL` + `RECAP_TOKEN` to the git-ignored repo-root `.env` (format in `.env.example`). Requests without a matching `x-recap-token` header get 401. `-NoToken` turns the check off; `-ModelId "<id1>,<id2>"` sets the model fallback order (default Nova 2 Lite, then Nova Micro).
- Test: `curl -s -X POST <url> -H "content-type: application/json" -H "x-recap-token: <token from .env>" -d '{"title":"Lift","position":"12:34","duration":"90:00","transcript":"[12:30] Push the button, Bo.\n[12:33] It is stuck."}'`
- Response: `{"recap": "..."}`, or `{"error": "..."}` with 400/401/502. Transcript limit: 200,000 chars.
- Length: share = position/duration (capped at 1; a 120-min film is assumed if `duration` is missing). The target is round(40 + 110 × share) words. The hard limit is min(150, target + 25), and the reply is clipped to the last full sentence within it.
- TV app: just rebuild after deploying (`.\gradlew.bat :tv:assembleDebug`); the build reads `.env` and the app sends the token.
- Unit tests (no AWS needed): `cd recap; python -m unittest`
