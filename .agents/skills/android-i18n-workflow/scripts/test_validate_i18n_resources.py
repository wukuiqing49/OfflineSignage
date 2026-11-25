"""覆盖跨语言复数与损坏资源的门禁回归。"""

import tempfile
import subprocess
import sys
import unittest
from pathlib import Path

from validate_i18n_resources import validate_res_dir, placeholders, resource_configuration


class ResourceValidationTest(unittest.TestCase):
    def test_missing_explicit_resource_directory_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, '-B', str(Path(__file__).with_name('validate_i18n_resources.py')),
                 '--res-dir', str(Path(directory)/'missing')],
                capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('resource directory not found', result.stdout)

    def test_argument_identity_and_reordering(self):
        self.assertNotEqual(placeholders('%s %d'), placeholders('%d %s'))
        self.assertEqual(placeholders('%1$s %2$d'), placeholders('%2$d %1$s'))
        self.assertEqual(placeholders('%s %d'), placeholders('%2$d %1$s'))
        self.assertNotEqual(placeholders('%1$s'), placeholders('%2$s'))

    def test_relative_date_and_literal_percent(self):
        self.assertEqual(placeholders('%1$tY %<tm'), placeholders('%1$tm %1$tY'))
        self.assertNotEqual(placeholders('%1$tY'), placeholders('%1$tm'))
        self.assertEqual(placeholders('%% %n'), ())
        self.assertEqual(placeholders('%%%s'), ('1:s',))
        self.assertEqual(placeholders('%2$s %s %<s'), placeholders('%2$s %1$s %1$s'))

    def test_resource_qualifiers(self):
        self.assertEqual(resource_configuration(Path('values-in')), ('id', ()))
        self.assertEqual(resource_configuration(Path('values-in-rID-night')), ('id-ID', ('night',)))
        self.assertEqual(resource_configuration(Path('values-car')), ('', ('car',)))
        self.assertEqual(resource_configuration(Path('values-en-night')), ('en', ('night',)))
        self.assertEqual(resource_configuration(Path('values-mcc310-en-rUS-night-v31')), ('en-US', ('mcc310', 'night', 'v31')))
        self.assertEqual(resource_configuration(Path('values-b+zh+Hans+CN-night')), ('zh-Hans-CN', ('night',)))

    def test_partial_overrides_inherit_but_night_only_is_not_full_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contents = {
                'values': '<string name="a">A %1$s</string><string name="b">B</string>',
                'values-en': '<string name="a">A %1$s</string><string name="b">B</string>',
                'values-en-night': '<string name="a">Night %1$s</string>',
                'values-en-rUS': '<string name="b">US B</string>',
                'values-car': '<string name="b">Car B</string>',
            }
            for folder, content in contents.items():
                (root/folder).mkdir()
                (root/folder/'strings.xml').write_text(f'<resources>{content}</resources>', encoding='utf-8')
            errors = []
            validate_res_dir(root, errors, [], True, ['en'])
            self.assertEqual(errors, [])
            (root/'values-en-night/strings.xml').write_text('<resources><string name="a">%1$d</string></resources>', encoding='utf-8')
            validate_res_dir(root, errors, [], True, ['en'])
            self.assertTrue(any('placeholder mismatch' in e for e in errors))
            (root/'values-en/strings.xml').unlink()
            (root/'values-en').rmdir()
            errors = []
            validate_res_dir(root, errors, [], True, ['en'])
            self.assertTrue(any('required locale en' in e for e in errors))

    def validate(self, localized, base=None):
        base = base or '<plurals name="count"><item quantity="one">%d item</item><item quantity="other">%d items</item></plurals>'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for locale, text in (("values", base), ("values-ar", localized)):
                (root / locale).mkdir()
                (root / locale / "strings.xml").write_text(f"<resources>{text}</resources>", encoding="utf-8")
            errors, warnings = [], []
            validate_res_dir(root, errors, warnings, strict_missing=True)
            return errors

    def test_language_specific_plural_categories(self):
        for quantities in (("other",), ("zero", "one", "two", "few", "many", "other")):
            with self.subTest(quantities=quantities):
                items = ''.join(f'<item quantity="{q}">%d عنصر</item>' for q in quantities)
                self.assertEqual([], self.validate(f'<plurals name="count">{items}</plurals>'))

    def test_placeholder_loss_in_one_branch_fails(self):
        errors = self.validate('<plurals name="count"><item quantity="one">عنصر</item><item quantity="other">%d عناصر</item></plurals>')
        self.assertTrue(any('placeholder mismatch' in error for error in errors))

    def test_missing_other_and_duplicate_quantity_fail(self):
        errors = self.validate('<plurals name="count"><item quantity="one">%d</item><item quantity="one">%d</item></plurals>')
        self.assertTrue(any('invalid plural' in error for error in errors))
        self.assertTrue(any('duplicate quantity' in error for error in errors))

    def test_duplicate_resource_and_broken_xml_fail(self):
        self.assertTrue(any('duplicate resource' in error for error in self.validate('<string name="label">a</string><string name="label">b</string>', '<string name="label">label</string>')))
        self.assertTrue(any('invalid XML' in error for error in self.validate('<string name="label">broken')))

    def test_missing_whole_required_locale_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'values').mkdir()
            (root / 'values/strings.xml').write_text('<resources><string name="title">标题</string></resources>', encoding='utf-8')
            errors = []
            validate_res_dir(root, errors, [], True, ['en'])
            self.assertTrue(any('required locale en is missing' in e for e in errors))
            (root / 'values-en').mkdir()
            (root / 'values-en/strings.xml').write_text('<resources><string name="title">Title</string></resources>', encoding='utf-8')
            errors = []
            validate_res_dir(root, errors, [], True, ['en'])
            self.assertEqual(errors, [])

    def test_array_count_and_placeholder_order(self):
        base = '<string-array name="options"><item>%1$s</item><item>%1$d</item></string-array>'
        self.assertEqual(self.validate(base, base), [])
        self.assertTrue(self.validate('<string-array name="options"><item>%1$s</item></string-array>', base))
        self.assertTrue(self.validate('<string-array name="options"><item>%1$d</item><item>%1$s</item></string-array>', base))

    def test_missing_translation_fails(self):
        self.assertTrue(any('missing' in error for error in self.validate('')))


if __name__ == '__main__':
    unittest.main()
