#!/usr/bin/env python3
import sys
import io
import re
import zipfile
import urllib.request
import urllib.error

def fetch_and_distill(run_id, repo="dslayer6989/NuvioDesktop", token=None):
    url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/logs"
    headers = {"User-Agent": "log-distiller", "Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=45)
        zip_data = resp.read()
    except urllib.error.HTTPError as e:
        print(f"Error fetching logs for run {run_id}: HTTP {e.code}")
        return

    with zipfile.ZipFile(io.BytesIO(zip_data)) as z:
        ansi_cleaner = re.compile(r"\x1b\[[0-9;]*m")
        ts_cleaner = re.compile(r"^\d{4}-\d\d-\d\dT[\d:.]+Z ?")

        print(f"=== DISTILLED LOG REPORT FOR RUN {run_id} ===")
        found_failure = False

        for name in sorted(z.namelist()):
            if not name.endswith(".txt") or "system" in name:
                continue
            lines = [
                ansi_cleaner.sub("", ts_cleaner.sub("", l))
                for l in z.read(name).decode("utf-8", "replace").split("\n")
            ]
            
            # Check if this log file contains failure markers
            has_error = any("FAILURE:" in l or "FAILED" in l or "##[error]" in l for l in lines)
            if not has_error:
                continue

            found_failure = True
            print(f"\n--- Failing Step / Job: {name} ---")
            
            # Extract key compiler and failure lines
            for i, line in enumerate(lines):
                if re.search(r"What went wrong|Execution failed for task|CMake Error|Package .* not found|^e: ", line):
                    snippet = lines[max(0, i - 1): min(len(lines), i + 6)]
                    for s in snippet:
                        if not re.match(r"^\s+at (org\.gradle|java\.|jdk\.)", s):
                            print(f"  {s[:200]}")
                    print("  ...")

        if not found_failure:
            print("No explicit Gradle or CMake failure markers found in step logs.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: fetch_run_log.py <RUN_ID> [REPO]")
        sys.exit(1)
    run_id = sys.argv[1]
    repo = sys.argv[2] if len(sys.argv) > 2 else "dslayer6989/NuvioDesktop"
    fetch_and_distill(run_id, repo)
