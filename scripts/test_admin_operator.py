import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch
import json

spec = importlib.util.spec_from_file_location("admin_operator", Path(__file__).with_name("admin_operator.py"))
operator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operator)


class OperatorSafetyTests(unittest.TestCase):
    def setUp(self):
        self.user = {"id": "318ff55f-0934-440b-9ca3-99481f7b57fc", "email": "one@example.com", "role": "STUDENT", "revision": 4}

    @patch.object(operator, "local_docker")
    def test_no_or_multiple_users_never_promoted(self, _):
        for data in ["", json.dumps(self.user)+"\n"+json.dumps(self.user)]:
            with patch.object(operator, "query", return_value=data) as query:
                with self.assertRaises(ValueError):
                    operator.promote(self.user["email"], lambda _: self.fail("must not ask"))
                self.assertEqual(1, query.call_count)

    @patch.object(operator, "local_docker")
    def test_mismatched_confirmation_never_writes(self, _):
        with patch.object(operator, "query", return_value=json.dumps(self.user)) as query:
            with self.assertRaises(ValueError):
                operator.promote(self.user["email"], lambda _: "yes")
            self.assertEqual(1, query.call_count)

    @patch.object(operator, "local_docker")
    def test_explicit_id_confirmation_uses_bound_sql_variables(self, _):
        with patch.object(operator, "query", side_effect=[json.dumps(self.user), "PROMOTED\n"]) as query:
            operator.promote("one@example.com", lambda _: f"PROMOTE {self.user['id']}")
            sql = query.call_args.args[0]
            self.assertIn("FOR UPDATE", sql)
            self.assertIn("OPERATOR_PROMOTE_ADMIN", sql)
            self.assertEqual(4, query.call_args.kwargs["revision"])

    def test_remote_context_rejected(self):
        from types import SimpleNamespace
        with patch.dict("os.environ", {"DOCKER_HOST": "ssh://remote"}), patch.object(operator, "run", return_value=SimpleNamespace(stdout='[{"Endpoints":{"docker":{"Host":"unix:///var/run/docker.sock"}}}]')):
            with self.assertRaises(ValueError):
                operator.local_docker()

    def test_checked_endpoint_is_pinned_across_context_changes(self):
        from types import SimpleNamespace
        with patch.dict("os.environ", {"DOCKER_HOST": "tcp://127.0.0.1:2375", "DOCKER_CONTEXT": "local-test"}), patch.object(operator, "run", return_value=SimpleNamespace(stdout='[{"Endpoints":{"docker":{"Host":"unix:///var/run/docker.sock"}}}]')):
            pinned=operator.local_docker()
            self.assertNotIn("DOCKER_CONTEXT",pinned)
            self.assertEqual("unix:///var/run/docker.sock",pinned["DOCKER_HOST"])
            with patch.dict("os.environ", {"DOCKER_HOST": "ssh://remote", "DOCKER_CONTEXT": "remote"}):
                self.assertEqual("unix:///var/run/docker.sock",pinned["DOCKER_HOST"])


if __name__ == "__main__":
    unittest.main()
