# Tools for make_pack.py: Pillow, fontTools and free symbol fonts.
FROM debian:stable-slim
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pil python3-fonttools \
    fonts-dejavu-core fonts-symbola fonts-noto-core && rm -rf /var/lib/apt/lists/*
