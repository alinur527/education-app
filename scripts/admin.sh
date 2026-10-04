#!/bin/sh
set -eu
task_script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_root=$(dirname -- "$task_script_dir")
if [ -x "$task_root/.venv/bin/python" ]; then
    exec "$task_root/.venv/bin/python" -X utf8 "$task_script_dir/admin_operator.py" "$@"
fi
exec python3 -X utf8 "$task_script_dir/admin_operator.py" "$@"
