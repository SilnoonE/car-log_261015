"""Usage: python tools/record_upload.py "업로드 내용" (before committing)."""
from datetime import datetime, timezone, timedelta
from pathlib import Path
import argparse

parser = argparse.ArgumentParser(description="한국 날짜로 차곡 업로드 기록 추가")
parser.add_argument("summary")
args = parser.parse_args()
summary = " ".join(args.summary.split())
if not summary:
    parser.error("작업 설명이 필요합니다")
day = datetime.now(timezone(timedelta(hours=9))).date().isoformat()
root = Path(__file__).resolve().parents[1]
folder = root / "updates" / day
folder.mkdir(exist_ok=True)
page = folder / "README.md"
entry = "- " + summary
body = page.read_text(encoding="utf-8") if page.exists() else f"# {day} · 차곡\n"
if entry not in body.splitlines():
    page.write_text(body.rstrip() + "\n\n" + entry + "\n", encoding="utf-8")
index = root / "updates/README.md"
text = index.read_text(encoding="utf-8")
line = f"- [{day}]({day}/README.md) · {summary}"
if line not in text.splitlines():
    text = text.replace("<!-- entries -->", "<!-- entries -->\n" + line, 1)
    index.write_text(text, encoding="utf-8")
print(page.relative_to(root))
