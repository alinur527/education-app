"""Explicitly confirmed first-admin promotion using the local Compose database.

No public endpoint, default account, passwords or non-interactive bypass.
"""
import json
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]


def run(args, sql=None, env=None):
    return subprocess.run(args, cwd=ROOT, input=sql, env=env, text=True, encoding="utf-8",
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)


def local_docker():
    # Both context selection and DOCKER_HOST affect where operator credentials go.
    import os
    context = json.loads(run(["docker", "context", "inspect"]).stdout)[0]
    endpoint = context["Endpoints"]["docker"]["Host"] if os.environ.get("DOCKER_CONTEXT") else os.environ.get("DOCKER_HOST") or context["Endpoints"]["docker"]["Host"]
    parsed = urlsplit(endpoint)
    if not (parsed.scheme in {"unix", "npipe"} or
            (parsed.scheme == "tcp" and parsed.hostname in {"localhost", "127.0.0.1", "::1"})):
        raise ValueError("Refusing a remote Docker database. Select a local Docker context.")
    pinned = os.environ.copy()
    pinned.pop("DOCKER_CONTEXT", None)
    pinned["DOCKER_HOST"] = endpoint
    return pinned


def query(sql, *, docker_env=None, **variables):
    command = ["docker", "compose", "exec", "-T", "postgres", "sh", "-c",
               'exec psql -X -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"', "sh"]
    command.extend(f"--set={key}={value}" for key, value in variables.items())
    return run(command, sql, env=docker_env).stdout


def promote(email, confirm=input):
    docker_env=local_docker()
    if not email.strip() or len(email) > 254 or any(ord(c) < 32 for c in email):
        raise ValueError("Invalid email.")
    rows = [json.loads(line) for line in query("""
SELECT json_build_object('id',id,'email',email,'role',role,'revision',revision)
FROM users WHERE is_active AND lower(email)=lower(:'email');
""", docker_env=docker_env, email=email.strip()).splitlines() if line.strip()]
    if len(rows) != 1:
        raise ValueError(f"Expected exactly one active user; found {len(rows)}. No changes.")
    user = rows[0]
    print(f"ID: {user['id']}\nEmail: {user['email']}\nCurrent role: {user['role']}")
    if user["role"] == "ADMIN":
        print("Already ADMIN. No changes.")
        return
    answer = confirm(f"Type PROMOTE {user['id']} to grant ADMIN: ")
    if answer != f"PROMOTE {user['id']}":
        raise ValueError("Confirmation did not match. No changes.")
    # Revalidate and lock after the interactive pause. SQL variables are quoted by
    # psql; no email or identifier is interpolated into SQL text or a shell string.
    output = query(r"""
BEGIN;
CREATE TEMP TABLE operator_target ON COMMIT DROP AS
 SELECT id,role,revision FROM users WHERE id=:'id'::uuid AND is_active
 AND lower(email)=lower(:'email') AND role=:'role' AND revision=:'revision'::bigint FOR UPDATE;
SELECT count(*)=1 AS valid FROM operator_target \gset
\if :valid
 WITH promoted AS (
   UPDATE users SET role='ADMIN',revision=revision+1,updated_at=now()
   WHERE id IN (SELECT id FROM operator_target) RETURNING id,revision
 ) INSERT INTO audit_events(entity_id,entity_type,operation,revision,details)
 SELECT id,'USER','OPERATOR_PROMOTE_ADMIN',revision,
 jsonb_build_object('previousRole',:'role','method','local-compose-cli') FROM promoted;
 COMMIT;
 SELECT 'PROMOTED';
\else
 ROLLBACK;
 \quit 3
\endif
""", docker_env=docker_env, id=user["id"], email=email.strip(), role=user["role"], revision=user["revision"])
    if "PROMOTED" not in output.splitlines():
        raise ValueError("Promotion was not confirmed by the database.")
    print("ADMIN granted. Audit event recorded. Sign out and sign in again.")


def main():
    if len(sys.argv) != 3 or sys.argv[1] != "promote":
        print("Usage: admin.ps1 promote user@example.com / admin.sh promote user@example.com", file=sys.stderr)
        return 2
    try:
        promote(sys.argv[2])
        return 0
    except (ValueError, KeyError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
    except (subprocess.CalledProcessError, OSError, EOFError):
        # Avoid echoing environment, connection strings, or database diagnostics.
        print("Local operator database operation failed or input ended. No promotion confirmed.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
