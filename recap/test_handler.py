import base64
import json
import os
import unittest

import handler


class FakeBedrock:
    def __init__(self, failing=()):
        self.calls = []
        self.failing = set(failing)

    def converse(self, modelId, system, messages, inferenceConfig):
        self.calls.append((modelId, system[0]["text"]))
        if modelId in self.failing:
            raise RuntimeError("AccessDenied")
        return {"output": {"message": {"role": "assistant", "content": [
            {"text": "- Ann and Bo are rivals.\n- They're stuck in a lift."}]}}, "stopReason": "end_turn"}


BODY = {"title": "Lift", "position": "12:34", "transcript": "[12:30] ANN: Bo, push the button.\n"}


class HandlerTest(unittest.TestCase):
    def setUp(self):
        self.fake = handler._client = FakeBedrock()
        os.environ.pop("RECAP_TOKEN", None)

    def call(self, body, **extra):
        return handler.handler({"body": body, **extra})

    def test_ok(self):
        r = self.call(json.dumps(BODY))
        self.assertEqual(r["statusCode"], 200)
        self.assertIn("rivals", json.loads(r["body"])["recap"])
        model, system = self.fake.calls[0]
        self.assertEqual(model, handler.MODEL_IDS[0])
        self.assertIn('"Lift" late; it is now at 12:34', system)

    def test_falls_back_to_next_model(self):
        self.fake = handler._client = FakeBedrock(failing={handler.MODEL_IDS[0]})
        r = self.call(json.dumps(BODY))
        self.assertEqual(r["statusCode"], 200)
        self.assertEqual([m for m, _ in self.fake.calls], handler.MODEL_IDS)

    def test_all_models_fail(self):
        handler._client = FakeBedrock(failing=set(handler.MODEL_IDS))
        self.assertEqual(self.call(json.dumps(BODY))["statusCode"], 502)

    def test_target_grows_with_share_watched(self):
        share = handler.progress
        self.assertEqual([share("0:00", "100:00"), share("50:00", "100:00"), share("120:00", "100:00")], [0, 0.5, 1.0])
        self.assertEqual(share("11:00", "12:00"), 11 / 12)  # near the end of a short film counts as near the end
        self.assertEqual(share("60:00", ""), 0.5)          # unknown duration: assume a 2-hour film
        self.assertEqual(share("bad", "100:00"), 1.0)
        self.assertEqual([handler.target_words(s) for s in (0, 0.5, 1)], [40, 95, 150])
        self.call(json.dumps({**BODY, "position": "58:00", "duration": "116:00"}))
        self.assertIn("50% of the way through, so aim for about 95 words", self.fake.calls[-1][1])

    def test_clip_cuts_at_sentence_end(self):
        self.assertEqual(handler.clip("One two. Three four five six.", 4), "One two.")
        self.assertEqual(handler.clip("Short.", 4), "Short.")

    def test_missing_transcript(self):
        r = self.call(json.dumps({"title": "x"}))
        self.assertEqual(r["statusCode"], 400)
        self.assertEqual(self.fake.calls, [])

    def test_oversized_transcript(self):
        r = self.call(json.dumps({**BODY, "transcript": "a" * (handler.MAX_TRANSCRIPT + 1)}))
        self.assertEqual(r["statusCode"], 400)

    def test_base64_body(self):
        b64 = base64.b64encode(json.dumps(BODY).encode()).decode()
        r = self.call(b64, isBase64Encoded=True)
        self.assertEqual(r["statusCode"], 200)

    def test_token_required(self):
        os.environ["RECAP_TOKEN"] = "s3cret"
        self.assertEqual(self.call(json.dumps(BODY))["statusCode"], 401)
        r = self.call(json.dumps(BODY), headers={"X-Recap-Token": "s3cret"})
        self.assertEqual(r["statusCode"], 200)


if __name__ == "__main__":
    unittest.main()
