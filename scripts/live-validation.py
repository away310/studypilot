"""Validate a running StudyPilot instance using its configured model provider.

Uses paid model APIs indirectly through the local application. Uploads the eight
sample documents; run against a dedicated validation database.
"""
import argparse
import hashlib
import json
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:18085")
    parser.add_argument("--output", default="output/validation/live-result.json")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    report = {"timestamp": datetime.now(timezone.utc).isoformat(), "scope": "real DashScope, sample corpus, isolated database", "uploads": [], "answers": []}

    def request(path, payload=None, content_type="application/json"):
        data = json.dumps(payload, ensure_ascii=False).encode() if isinstance(payload, dict) else payload
        req = urllib.request.Request(args.base_url.rstrip("/") + path, data=data,
                                     headers={"Content-Type": content_type})
        with urllib.request.urlopen(req, timeout=240) as response:
            body = response.read().decode("utf-8")
            return body if "text/event-stream" in response.headers.get("Content-Type", "") else json.loads(body)

    for attempt in range(45):
        try:
            request("/api/kb/documents")
            break
        except (urllib.error.URLError, TimeoutError):
            if attempt == 44: raise
            time.sleep(1)

    for path in sorted((root / "kb").glob("*.md")):
        boundary = uuid.uuid4().hex
        data = path.read_bytes()
        payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{path.name}\"\r\n"
                   "Content-Type: text/markdown\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
        started = time.monotonic()
        result = request("/api/kb/documents", payload, "multipart/form-data; boundary=" + boundary)
        report["uploads"].append({"file": path.name, "sha256": hashlib.sha256(data).hexdigest(),
                                  "chunks": result["chunkCount"], "seconds": round(time.monotonic() - started, 3)})
        print("Uploaded " + path.name, flush=True)

    started = time.monotonic()
    report["retrieval_eval"] = request("/api/eval/run")
    report["retrieval_eval_seconds"] = round(time.monotonic() - started, 3)
    print("Retrieval evaluation completed", flush=True)

    cases = [
        ("answerable", "混合检索是哪两条通道？怎么融合？", False),
        ("same_topic_unanswerable", "2026年9月14日我公司的Redis生产集群故障根因是什么？请给出具体节点和日志证据。", True),
        ("unrelated", "明天上海是否下雨？", True),
    ]
    for label, question, expected_rejected in cases:
        started = time.monotonic()
        result = request("/api/chat", {"question": question})
        passed = result["rejected"] == expected_rejected
        if not expected_rejected:
            passed = passed and bool(result["citations"]) and "[1]" in result["answer"]
        report["answers"].append({"case": label, "expected_rejected": expected_rejected, "passed": passed,
                                 "seconds": round(time.monotonic() - started, 3), "result": result})
        print(label + ": " + ("PASS" if passed else "FAIL"), flush=True)

    question = "结构感知切分为什么保留标题层级？"
    wire = request("/api/chat/stream", {"question": question})
    events = []
    for frame in wire.replace("\r\n", "\n").split("\n\n"):
        name = "message"
        data = []
        for line in frame.splitlines():
            if line.startswith("event:"): name = line[6:].strip()
            if line.startswith("data:"): data.append(line[5:].lstrip(" "))
        if data: events.append({"event": name, "data": json.loads("\n".join(data))})
    completed = [event["data"] for event in events if event["event"] == "done"]
    report["stream"] = {"event_count": len(events), "delta_count": sum(e["event"] == "delta" for e in events),
                        "passed": bool(completed) and not completed[-1]["rejected"] and not any(e["event"] == "error" for e in events),
                        "result": completed[-1] if completed else None}
    report["passed"] = all(case["passed"] for case in report["answers"]) and report["stream"]["passed"]
    report["limitations"] = "Three answer cases are smoke checks, not a statistical answer-quality or refusal benchmark. Hit@5 is document-level; referenceAnswer is not scored."
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed": report["passed"], "stream_passed": report["stream"]["passed"],
                      "metrics": [{k: r[k] for k in ("mode", "total", "hitAt5", "hitAt1")} for r in report["retrieval_eval"]],
                      "report": str(output)}, ensure_ascii=True), flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
