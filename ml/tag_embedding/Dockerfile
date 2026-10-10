# 태그 구조 임베딩 학습 배치, 서버(arm64)에서 직접 빌드한다
FROM python:3.12-slim

WORKDIR /app

# cron 로그에 진행이 바로 찍히게 출력 버퍼를 끈다
ENV PYTHONUNBUFFERED=1

# CPU 전용 PyTorch 를 먼저 받는다, 기본 색인은 CUDA 판이라 이미지가 크다
# PyTorch Geometric 2.8 이 지원하는 마지막 판(2.12)에 맞춘다
COPY requirements.txt .
RUN pip install --no-cache-dir torch==2.12.1 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

COPY tag_embedding/ tag_embedding/
COPY tests/ tests/

ENTRYPOINT ["python", "-m", "tag_embedding.main"]
