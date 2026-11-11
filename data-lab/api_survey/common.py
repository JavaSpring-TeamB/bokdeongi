"""API 실측 공통 설정. 외부 라이브러리 없이 표준 라이브러리만 쓴다."""
from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import unquote

BASE_URL = "https://api.odcloud.kr/api/gov24/v3"
ENDPOINTS = ("serviceList", "serviceDetail", "supportConditions")

DATA_LAB = Path(__file__).resolve().parent.parent
RAW_DIR = DATA_LAB / "data" / "raw" / "gov24"      # .gitignore 대상 (커밋 안 됨)
REPORT_PATH = DATA_LAB / "reports" / "api_survey.md"  # 커밋 대상


def find_env_file(start: Path) -> Path | None:
    """start부터 위로 올라가며 .env를 찾는다 (레포 루트의 .env)."""
    for folder in (start, *start.parents):
        candidate = folder / ".env"
        if candidate.is_file():
            return candidate
    return None


def load_env(start: Path | None = None) -> dict[str, str]:
    """.env를 읽어 dict로 돌려준다. 이미 설정된 OS 환경변수가 우선한다."""
    values: dict[str, str] = {}
    env_file = find_env_file(start or DATA_LAB)
    if env_file:
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip().strip('"').strip("'")
    for key in list(values):
        if os.environ.get(key):
            values[key] = os.environ[key]
    return values


def service_key(env: dict[str, str]) -> str:
    """인증키를 디코딩된 형태로 돌려준다. 인코딩 키를 넣어도 동작하게 한 번 풀어 준다."""
    key = env.get("DATA_GO_KR_SERVICE_KEY") or os.environ.get("DATA_GO_KR_SERVICE_KEY", "")
    if not key:
        raise SystemExit(
            "DATA_GO_KR_SERVICE_KEY가 비어 있습니다. 레포 루트의 .env에 공공데이터포털 인증키(Decoding)를 넣어 주세요."
        )
    return unquote(key) if "%" in key else key


def mask(key: str) -> str:
    """로그에 키를 남기지 않기 위한 마스킹."""
    return key[:4] + "…" + key[-2:] if len(key) > 8 else "****"
