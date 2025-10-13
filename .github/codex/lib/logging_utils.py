"""
Heartbeat and step summary helpers.
"""
from __future__ import annotations
import time, threading, sys, os

def heartbeat(tag="bot", every_s=60):
    def _beat():
        while True:
            print(f"::notice::{tag} heartbeat", flush=True)
            time.sleep(every_s)
    t = threading.Thread(target=_beat, daemon=True)
    t.start()

def add_step_summary(text: str):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        try:
            with open(path, "a", encoding="utf-8") as f:
                f.write(text + "\n")
        except Exception:
            pass
