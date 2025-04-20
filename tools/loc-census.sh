#!/usr/bin/env bash
set -euo pipefail

# tools/loc-census.sh
# Counts physical, substantive non-test source lines across all Vibe modules.
# Excludes comments, blank lines, test files, generated sources, and dependencies.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

python3 -c "
import os, re, sys

modules = [
    'protocol', 'transport-nio', 'domain', 'storage-local', 'storage-ratis',
    'delivery', 'server', 'sdk-java', 'desktop', 'sdk-typescript', 'web', 'benchmarks'
]

def count_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception:
        return 0
    # Strip multi-line comments
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    lines = content.splitlines()
    substantive = 0
    for l in lines:
        s = l.strip()
        if s and not s.startswith('//') and not s.startswith('#') and not s.startswith('*'):
            substantive += 1
    return substantive

print(f'{\"Module\":<20} | {\"Files\":<8} | {\"Substantive LOC\":<18}')
print('-' * 52)
total_files = 0
total_lines = 0

for mod in modules:
    mod_files = 0
    mod_lines = 0
    mod_path = os.path.join('${REPO_ROOT}', mod)
    if not os.path.exists(mod_path):
        continue
    for root, dirs, files in os.walk(mod_path):
        if any(x in root for x in ['/test/', '/test', 'node_modules', 'build', 'dist', '.gradle', 'target']):
            continue
        for file in files:
            if file.endswith(('.java', '.ts', '.tsx')) and not file.endswith('.d.ts'):
                fp = os.path.join(root, file)
                mod_files += 1
                lines = count_file(fp)
                mod_lines += lines
    print(f'{mod:<20} | {mod_files:<8} | {mod_lines:<18}')
    total_files += mod_files
    total_lines += mod_lines

print('-' * 52)
print(f'{\"TOTAL\":<20} | {total_files:<8} | {total_lines:<18}')
"
