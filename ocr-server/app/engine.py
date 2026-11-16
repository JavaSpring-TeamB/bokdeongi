"""PaddleOCR로 글자를 읽는다. 모델은 처음 부를 때 한 번만 올린다.

원본 파일은 임시 파일로만 쓰고 읽은 직후(실패해도) 지운다. 텍스트는 로그에 남기지 않는다.
"""
import os
import tempfile
from dataclasses import dataclass
from threading import Lock

_ocr = None
_lock = Lock()


@dataclass
class OcrOutput:
    lines: list[str]
    mean_score: float


def _engine():
    global _ocr
    with _lock:
        if _ocr is None:
            # 모델은 한 번 받으면 로컬에 있으므로 매번 하는 서버 연결 확인을 건너뛴다
            os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
            from paddleocr import PaddleOCR  # 무거워서 여기서 import
            _ocr = PaddleOCR(
                # 모델 이름을 하나라도 정하면 lang이 무시되므로 인식 모델도 한국어로 직접 정한다
                text_detection_model_name="PP-OCRv5_mobile_det",  # server_det보다 가볍고 CPU에서 빠름
                text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
                use_doc_orientation_classify=False,
                use_doc_unwarping=False,
                use_textline_orientation=False,
                enable_mkldnn=False,  # 윈도우 CPU에서 oneDNN 에러(ConvertPirAttribute2RuntimeAttribute) 회피
            )
        return _ocr


def read(data: bytes, suffix: str) -> OcrOutput:
    fd, path = tempfile.mkstemp(suffix=suffix)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        lines: list[str] = []
        scores: list[float] = []
        for page in _engine().predict(path):  # PDF는 쪽마다 하나씩 나온다
            res = page.json.get("res", page.json) if hasattr(page, "json") else page
            lines.extend(t for t in res.get("rec_texts", []) if t and t.strip())
            scores.extend(float(s) for s in res.get("rec_scores", []))
        return OcrOutput(lines, sum(scores) / len(scores) if scores else 0.0)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass