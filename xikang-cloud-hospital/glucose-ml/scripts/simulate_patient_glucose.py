"""Simulate patient home glucose entries via patient portal API (no ai_* tables)."""

from __future__ import annotations

import argparse
import random
import sys
import time
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
ENV_PATH = ROOT.parent / ".env"

PATIENTS = {
    9001: {"username": "patient_tang", "password": "patient123"},
    9002: {"username": "patient_xx", "password": "patient123"},
    9003: {"username": "patient_hx", "password": "patient123"},
}


def load_base_url() -> str:
    base = "http://localhost:8080"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("API_BASE_URL="):
                base = line.split("=", 1)[1].strip().rstrip("/")
                break
    return base


def login(session: requests.Session, base_url: str, username: str, password: str) -> str:
    resp = session.post(
        f"{base_url}/api/auth/login",
        json={"username": username, "password": password},
        timeout=30,
    )
    resp.raise_for_status()
    body = resp.json()
    data = body.get("data") or {}
    token = data.get("token")
    if not token:
        raise SystemExit(f"登录失败: {body}")
    return str(token)


def post_observation(
    session: requests.Session,
    base_url: str,
    token: str,
    register_id: int,
    value: float,
) -> None:
    headers = {"Authorization": f"Bearer {token}"}
    resp = session.post(
        f"{base_url}/api/medtech/follow-up/patient/observations",
        headers=headers,
        json={
            "registerId": register_id,
            "metricValue": round(value, 1),
            "note": "模拟器自动录入",
        },
        timeout=30,
    )
    resp.raise_for_status()
    print(f"register={register_id} glucose={value:.1f} -> {resp.json().get('message', 'ok')}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Simulate periodic patient glucose reports")
    parser.add_argument("--register-id", type=int, default=9001)
    parser.add_argument("--interval-minutes", type=float, default=5.0)
    parser.add_argument("--base-glucose", type=float, default=7.5)
    parser.add_argument("--once", action="store_true", help="Submit one reading and exit")
    args = parser.parse_args()

    patient = PATIENTS.get(args.register_id)
    if not patient:
        raise SystemExit(f"未配置 register_id={args.register_id} 的演示患者账号")

    base_url = load_base_url()
    session = requests.Session()
    token = login(session, base_url, patient["username"], patient["password"])

    current = args.base_glucose
    while True:
        drift = random.uniform(-0.8, 0.8)
        current = max(4.5, min(14.0, current + drift))
        try:
            post_observation(session, base_url, token, args.register_id, current)
        except requests.RequestException as exc:
            print(f"写入失败: {exc}", file=sys.stderr)
        if args.once:
            break
        time.sleep(max(30.0, args.interval_minutes * 60))


if __name__ == "__main__":
    main()
