"""Exercise the real evidence runner's subprocess boundary, without Docker calls."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import acceptance


class AcceptanceCommandTests(unittest.TestCase):
    def test_hanging_child_is_terminated_at_configured_deadline(self):
        with patch.dict(os.environ, {"P20_COMMAND_TIMEOUT": "0.05"}):
            with self.assertRaises(subprocess.TimeoutExpired):
                acceptance.command(sys.executable, "-c", "import time; time.sleep(2)")

    def test_successful_child_receives_stdin_and_returns_actual_output(self):
        self.assertEqual(acceptance.command(sys.executable, "-c", "import sys; print(sys.stdin.read().upper())", stdin="demo"), "DEMO")


if __name__ == "__main__":
    unittest.main()
