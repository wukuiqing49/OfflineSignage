import tempfile
from pathlib import Path
import unittest
from validate_site import validate


class SiteTests(unittest.TestCase):
    def check(self, files):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, text in files.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text, encoding='utf-8')
            return validate(root)

    def test_nested_clean_route_and_anchor(self):
        self.assertEqual([], self.check({'index.html': '<a href="/help#ok">Help</a>', 'help.html': '<p id="ok">OK</p>'}))

    def test_missing_asset(self):
        self.assertIn('MISSING_TARGET', [x['rule'] for x in self.check({'index.html': '<img src="lost.png">'})])

    def test_missing_anchor(self):
        self.assertIn('MISSING_ANCHOR', [x['rule'] for x in self.check({'index.html': '<a href="#lost">Go</a>'})])

    def test_css_relative_asset(self):
        self.assertEqual([], self.check({'index.html': '<link href="assets/main.css">', 'assets/main.css': 'body {background:url(../image.svg)}', 'image.svg': '<svg/>'}))

    def test_private_audit(self):
        self.assertIn('PRIVATE_FILE', [x['rule'] for x in self.check({'index.html': '<h1>X</h1>', '.site-audit/project-facts.json': '{}'})])

    def test_path_escape(self):
        self.assertIn('PATH_ESCAPE', [x['rule'] for x in self.check({'index.html': '<img src="%2e%2e/private.png">'})])

    def test_invalid_json(self):
        self.assertIn('PARSE', [x['rule'] for x in self.check({'index.html': '<h1>X</h1>', 'site.webmanifest': '{'})])

    def test_invalid_url_reports_instead_of_crashing(self):
        self.assertIn('INVALID_URL', [x['rule'] for x in self.check({'index.html': '<a href="https://[broken">Go</a>'})])

    def test_external_url_not_fetched(self):
        self.assertEqual([], self.check({'index.html': '<a href="https://example.invalid">External</a>'}))


if __name__ == '__main__':
    unittest.main()
