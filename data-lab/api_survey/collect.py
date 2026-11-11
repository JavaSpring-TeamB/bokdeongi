"""공공서비스(혜택) API 3종 전체 수집.

사용법 (레포 루트에서):
    python data-lab/api_survey/collect.py                 # 3종 모두
    python data-lab/api_survey/collect.py --endpoints serviceList --per-page 100

- 원본 응답은 data-lab/data/raw/gov24/{endpoint}/page_0001.json 으로 저장한다 (커밋 안 됨)
- 이미 받은 페이지는 건너뛰므로, 중간에 끊겨도 다시 실행하면 이어서 받는다
- 개발 계정 호출 한도(10,000)를 아끼기 위해 --max-calls로 이번 실행의 호출 수를 제한한다
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import BASE_URL, ENDPOINTS, RAW_DIR, load_env, mask, service_key  # noqa: E402

AUTH_HINT = (
    "인증 오류입니다. 활용신청 직후에는 키가 등록되기까지 시간이 걸릴 수 있습니다(보통 1시간 이내). "
    "잠시 후 다시 실행하고, 그래도 안 되면 .env에 Decoding 키를 넣었는지 확인하세요."
)


class CallBudget:
    def __init__(self, limit: int):
        self.limit = limit
        self.used = 0

    def take(self) -> None:
        if self.used >= self.limit:
            raise SystemExit(f"이번 실행의 호출 한도(--max-calls {self.limit})에 도달했습니다. 다시 실행하면 이어서 받습니다.")
        self.used += 1


def fetch_page(endpoint: str, page: int, per_page: int, key: str, budget: CallBudget, retries: int = 3) -> dict:
    query = urllib.parse.urlencode({"page": page, "perPage": per_page, "serviceKey": key})
    url = f"{BASE_URL}/{endpoint}?{query}"
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    for attempt in range(1, retries + 1):
        budget.take()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")[:300]
            if error.code in (401, 403):
                raise SystemExit(f"[{endpoint}] HTTP {error.code}: {body}\n{AUTH_HINT}")
            print(f"[{endpoint}] page {page} HTTP {error.code} (시도 {attempt}/{retries}): {body}")
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            print(f"[{endpoint}] page {page} 오류 (시도 {attempt}/{retries}): {error}")
        time.sleep(2 * attempt)
    raise SystemExit(f"[{endpoint}] page {page}를 받지 못했습니다. 잠시 후 다시 실행하세요.")


def collect(endpoint: str, per_page: int, key: str, budget: CallBudget, delay: float) -> None:
    out_dir = RAW_DIR / endpoint
    out_dir.mkdir(parents=True, exist_ok=True)

    first_path = out_dir / "page_0001.json"
    if first_path.exists():
        first = json.loads(first_path.read_text(encoding="utf-8"))
    else:
        first = fetch_page(endpoint, 1, per_page, key, budget)
        first_path.write_text(json.dumps(first, ensure_ascii=False), encoding="utf-8")

    if first.get("perPage") and int(first["perPage"]) != per_page:
        raise SystemExit(
            f"[{endpoint}] 저장된 1페이지의 perPage({first['perPage']})가 지금 값({per_page})과 다릅니다. "
            f"{out_dir} 폴더를 지우고 다시 받거나 같은 --per-page로 실행하세요."
        )

    total = int(first.get("totalCount") or first.get("matchCount") or 0)
    received = len(first.get("data", []))
    if received < min(per_page, total):
        raise SystemExit(
            f"[{endpoint}] 1페이지에 {received}건만 왔습니다(요청 {per_page}건, 전체 {total}건). "
            f"API가 한 번에 주는 최대 건수가 {received}건인 것 같습니다. "
            f"{out_dir} 폴더를 지우고 --per-page {received}로 다시 실행하세요."
        )
    pages = max(1, math.ceil(total / per_page))
    print(f"[{endpoint}] 전체 {total}건, {pages}페이지 (perPage {per_page})")

    for page in range(2, pages + 1):
        path = out_dir / f"page_{page:04d}.json"
        if path.exists():
            continue
        data = fetch_page(endpoint, page, per_page, key, budget)
        path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        print(f"[{endpoint}] {page}/{pages} 저장 ({len(data.get('data', []))}건)")
        time.sleep(delay)


def main() -> None:
    parser = argparse.ArgumentParser(description="공공서비스(혜택) API 전체 수집")
    parser.add_argument("--endpoints", nargs="+", choices=ENDPOINTS, default=list(ENDPOINTS))
    parser.add_argument("--per-page", type=int, default=500, help="한 번에 받을 건수 (기본 500)")
    parser.add_argument("--max-calls", type=int, default=300, help="이번 실행에서 쓸 최대 호출 수 (기본 300)")
    parser.add_argument("--delay", type=float, default=0.3, help="호출 사이 대기 초 (초당 호출 제한 대비)")
    args = parser.parse_args()

    key = service_key(load_env())
    print(f"인증키 {mask(key)} 사용, 저장 위치 {RAW_DIR}")
    budget = CallBudget(args.max_calls)
    for endpoint in args.endpoints:
        collect(endpoint, args.per_page, key, budget, args.delay)
    print(f"완료. 이번 실행 호출 수 {budget.used}회")


if __name__ == "__main__":
    main()
