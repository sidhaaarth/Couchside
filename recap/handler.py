"""'Catch me up' recap Lambda (Function URL). Python 3.13, boto3 only."""
import base64
import json
import os

# Amazon Nova via the Bedrock Converse API, tried in order. us.* profiles are required in us-east-2.
MODEL_IDS = os.environ.get("MODEL_ID", "us.amazon.nova-2-lite-v1:0,us.amazon.nova-micro-v1:0").split(",")
MAX_TRANSCRIPT = 200_000  # a 2-hour film is ~75k chars of "[m:ss] line" transcript

SYSTEM = (
    'You catch up a viewer who joined "{title}" late; it is now at {position}. '
    "The user message is the film's dialogue so far, without speaker names. Using ONLY that dialogue, "
    "write a spoiler-free catch-up as plain prose in one to three short paragraphs: who's who, what has "
    "happened, and where things stand now. Describe the story only, not the viewer. No bullets, no "
    "headers, no speculation about what happens next. The viewer is {pct}% of the way through, so "
    "aim for about {target} words: the further in, the fuller the catch-up should be."
)

# The word target grows with the share of the film already watched: MIN_WORDS at the start,
# MAX_WORDS at the end. 150 words fits one phone screen below the compact controls at 17sp.
MIN_WORDS, MAX_WORDS = 40, 150
ASSUMED_FILM_MINUTES = 120  # when the TV doesn't know the duration yet


def _minutes(t):
    """'m:ss' (minutes may exceed 59) -> float minutes, or None."""
    try:
        m, s = t.split(":")
        return int(m) + int(s) / 60
    except (AttributeError, ValueError):
        return None


def progress(position, duration):
    pos, dur = _minutes(position), _minutes(duration)
    if pos is None:
        return 1.0
    return min(pos / (dur or ASSUMED_FILM_MINUTES), 1.0)


def target_words(share):
    return round(MIN_WORDS + (MAX_WORDS - MIN_WORDS) * share)


def clip(text, limit):
    """Hard stop for the phone screen: trim to the last full sentence within the word limit."""
    words = text.split()
    if len(words) <= limit:
        return text
    cut = " ".join(words[:limit])
    end = max(cut.rfind(". "), cut.rfind("! "), cut.rfind("? "), cut.rfind("."))
    return cut[: end + 1] if end > 0 else cut + "…"

_client = None


def _bedrock():
    global _client
    if _client is None:
        import boto3  # lazy: bundled in Lambda, not needed for local tests
        _client = boto3.client("bedrock-runtime")
    return _client


def _resp(status, payload):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(payload)}


def handler(event, context=None):
    token = os.environ.get("RECAP_TOKEN")
    if token:
        headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
        if headers.get("x-recap-token") != token:
            return _resp(401, {"error": "unauthorized"})

    try:
        raw = event.get("body") or ""
        if event.get("isBase64Encoded"):
            raw = base64.b64decode(raw).decode("utf-8")
        body = json.loads(raw) if raw else {}
        if not isinstance(body, dict):
            raise ValueError
    except (ValueError, UnicodeDecodeError):
        return _resp(400, {"error": "body must be a JSON object"})

    transcript = body.get("transcript")
    if not isinstance(transcript, str) or not transcript.strip():
        return _resp(400, {"error": "missing transcript"})
    if len(transcript) > MAX_TRANSCRIPT:
        return _resp(400, {"error": f"transcript exceeds {MAX_TRANSCRIPT} chars"})
    title = str(body.get("title") or "this show")[:200]
    position = str(body.get("position") or "the current moment")[:20]

    share = progress(position, str(body.get("duration") or ""))
    target = target_words(share)
    limit = min(MAX_WORDS, target + 25)  # hard cap so it always fits the phone screen
    system = SYSTEM.format(title=title, position=position, pct=round(share * 100), target=target)
    for model_id in MODEL_IDS:
        try:
            out = _bedrock().converse(
                modelId=model_id.strip(),
                system=[{"text": system}],
                messages=[{"role": "user", "content": [{"text": transcript}]}],
                inferenceConfig={"maxTokens": limit * 2 + 60},
            )
        except Exception as e:  # botocore ClientError etc. — try the next model
            print(f"bedrock error ({model_id}): {e!r}")
            continue
        text = "".join(b.get("text", "") for b in out["output"]["message"]["content"]).strip()
        if text:
            print(f"recap by {model_id}: {len(text.split())} words, limit {limit}")
            return _resp(200, {"recap": clip(text, limit)})
    return _resp(502, {"error": "model call failed"})
