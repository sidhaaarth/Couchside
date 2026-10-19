# Deploys the couch-recap Lambda + public Function URL. Run after `aws login`.
# The URL requires the shared secret RECAP_TOKEN in the x-recap-token header. The token and URL are kept in
# the repo-root .env (git-ignored): an existing token is reused, otherwise one is generated. The TV build reads .env.
# -NoToken turns the check off (open URL).
param(
    [switch]$NoToken,
    [string]$Region = "us-east-2",
    [string]$ModelId = "us.amazon.nova-2-lite-v1:0,us.amazon.nova-micro-v1:0"  # tried in order
)
$ErrorActionPreference = "Continue"
$aws = (Get-Command aws -ErrorAction Stop).Source
$fn = "couch-recap"
$role = "couch-recap-role"
$tmp = Join-Path $env:TEMP "couch-recap"
New-Item -ItemType Directory -Force $tmp | Out-Null
function Fail($m) { Write-Error $m; exit 1 }
function J($name, $obj) { $p = Join-Path $tmp $name; $obj | ConvertTo-Json -Depth 10 | Out-File -Encoding ascii $p; "file://$p" }

# .env (KEY=VALUE lines) in the repo root
$dotenv = Join-Path (Split-Path $PSScriptRoot) ".env"
$secrets = [ordered]@{}
if (Test-Path $dotenv) {
    foreach ($line in Get-Content $dotenv) { if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { $secrets[$Matches[1]] = $Matches[2].Trim() } }
}

# 1. Zip
$zip = Join-Path $tmp "function.zip"
Compress-Archive -Force -Path (Join-Path $PSScriptRoot "handler.py") -DestinationPath $zip

# 2. IAM role (idempotent)
$roleArn = & $aws iam get-role --role-name $role --query Role.Arn --output text 2>$null
if ($LASTEXITCODE -ne 0) {
    $trust = J "trust.json" @{ Version = "2012-10-17"; Statement = @(@{ Effect = "Allow"; Principal = @{ Service = "lambda.amazonaws.com" }; Action = "sts:AssumeRole" }) }
    $roleArn = & $aws iam create-role --role-name $role --assume-role-policy-document $trust --query Role.Arn --output text
    if ($LASTEXITCODE -ne 0) { Fail "create-role failed" }
    $newRole = $true
}
& $aws iam attach-role-policy --role-name $role --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
# Resource "*": us.* inference profiles route across regions, so pinning model ARNs is fiddly. Tighten later if needed.
$bedrock = J "bedrock.json" @{ Version = "2012-10-17"; Statement = @(@{ Effect = "Allow"; Action = @("bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"); Resource = "*" }) }
& $aws iam put-role-policy --role-name $role --policy-name bedrock-invoke --policy-document $bedrock
if ($newRole) { Write-Host "Waiting for new IAM role to propagate..."; Start-Sleep 15 }

# 3. Function (create or update)
$token = if ($NoToken) { $null } elseif ($secrets["RECAP_TOKEN"]) { $secrets["RECAP_TOKEN"] } else { [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N") }
$vars = @{ MODEL_ID = $ModelId }
if ($token) { $vars.RECAP_TOKEN = $token }
$envFile = J "env.json" @{ Variables = $vars }

& $aws lambda get-function --function-name $fn --region $Region 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    & $aws lambda update-function-code --function-name $fn --zip-file "fileb://$zip" --region $Region | Out-Null
    & $aws lambda wait function-updated --function-name $fn --region $Region
    & $aws lambda update-function-configuration --function-name $fn --runtime python3.13 --handler handler.handler `
        --timeout 30 --memory-size 256 --environment $envFile --region $Region | Out-Null
} else {
    & $aws lambda create-function --function-name $fn --runtime python3.13 --handler handler.handler `
        --role $roleArn --zip-file "fileb://$zip" --timeout 30 --memory-size 256 --environment $envFile --region $Region | Out-Null
}
if ($LASTEXITCODE -ne 0) { Fail "Lambda create/update failed" }
& $aws lambda wait function-updated --function-name $fn --region $Region

# 4. Function URL, AuthType NONE (demo), no CORS (the TV app is not a browser)
$url = & $aws lambda get-function-url-config --function-name $fn --region $Region --query FunctionUrl --output text 2>$null
if ($LASTEXITCODE -ne 0) {
    $url = & $aws lambda create-function-url-config --function-name $fn --auth-type NONE --region $Region --query FunctionUrl --output text
    if ($LASTEXITCODE -ne 0) { Fail "create-function-url-config failed" }
    & $aws lambda add-permission --function-name $fn --statement-id public-url --action lambda:InvokeFunctionUrl `
        --principal "*" --function-url-auth-type NONE --region $Region | Out-Null
    # Newer accounts also need lambda:InvokeFunction scoped to URL invocations.
    & $aws lambda add-permission --function-name $fn --statement-id public-url-invoke --action lambda:InvokeFunction `
        --principal "*" --invoked-via-function-url --region $Region | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warning "InvokeFunction permission not added (older CLI?). If the URL returns 403, add it in the console." }
}

# 5. Save the URL and token to .env (never printed, never committed)
$secrets["RECAP_URL"] = $url
if ($token) { $secrets["RECAP_TOKEN"] = $token } else { $secrets.Remove("RECAP_TOKEN") }
@("# Recap service secrets. Git-ignored; written by recap\deploy.ps1 and read by the TV build.") +
    ($secrets.Keys | ForEach-Object { "$_=$($secrets[$_])" }) | Out-File -Encoding ascii $dotenv

Write-Host ""
Write-Host "Function URL: $url"
if ($token) { Write-Host "RECAP_TOKEN:  saved to .env (required as header x-recap-token)" } else { Write-Host "RECAP_TOKEN:  (none - URL is open)" }
Write-Host "Model:        $ModelId"
Write-Host "Rebuild the TV app so it picks up .env: .\gradlew.bat :tv:assembleDebug"
