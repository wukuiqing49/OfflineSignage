"""Read-only static-site checks. No networking or file mutation."""
import argparse
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.refs, self.ids = [], set()
        self.base = False
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get('id'):
            self.ids.add(attrs['id'])
        if tag == 'a' and attrs.get('name'):
            self.ids.add(attrs['name'])
        if tag == 'base':
            self.base = True
        for key in ('href', 'src', 'poster'):
            if key in attrs:
                self.refs.append(attrs[key])


def validate(site):
    root = Path(site).resolve()
    issues = []

    def issue(rule, path, detail):
        issues.append(dict(rule=rule, path=str(path), detail=detail))

    if not root.is_dir():
        return [dict(rule='SITE_ROOT', path=str(root), detail='Directory not found')]
    pages, css = {}, {}
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            issue('SYMLINK', relative, 'Review symbolic link before deployment')
            continue
        if not path.is_file():
            continue
        if any(part in {'.git', '.site-audit', 'node_modules', '__pycache__'} for part in path.relative_to(root).parts) or path.name in {'project-facts.json', 'generation-manifest.json', 'local.properties'} or path.name.startswith('.env') or path.suffix.lower() in {'.jks', '.keystore', '.pem', '.key'}:
            issue('PRIVATE_FILE', relative, 'Internal file in public directory')
        if path.suffix.lower() not in {'.html', '.css', '.json', '.webmanifest'}:
            continue
        try:
            text = path.read_text(encoding='utf-8')
            if '\ufffd' in text:
                issue('ENCODING', relative, 'Replacement character found')
            if path.suffix.lower() == '.html':
                pages[path] = Page(text)
                if pages[path].base:
                    issue('HTML_BASE', relative, 'base URL requires manual URL resolution review')
            elif path.suffix.lower() == '.css':
                css[path] = re.findall(r'url\(\s*[\"\']?([^\"\')]+)', text)
            else:
                json.loads(text)
        except (ValueError, UnicodeError) as error:
            issue('PARSE', relative, str(error))
    if not pages:
        issue('NO_HTML', '.', 'No HTML pages found')
    for source, refs in [(p, page.refs) for p, page in pages.items()] + list(css.items()):
        for ref in refs:
            try:
                url = urlsplit(ref.strip())
            except ValueError:
                issue('INVALID_URL', source.relative_to(root), ref)
                continue
            if url.scheme or url.netloc:
                if url.scheme.lower() == 'javascript':
                    issue('SCRIPT_URL', source.relative_to(root), 'javascript URL requires review')
                continue
            decoded = unquote(url.path)
            target = (root / decoded.lstrip('/') if decoded.startswith('/') else source.parent / decoded).resolve() if decoded else source
            if not target.is_relative_to(root):
                issue('PATH_ESCAPE', source.relative_to(root), ref)
                continue
            if target.is_dir():
                target /= 'index.html'
            elif not target.exists() and not target.suffix and target.with_suffix('.html').is_file():
                target = target.with_suffix('.html')
            if not target.is_file():
                issue('MISSING_TARGET', source.relative_to(root), ref)
            elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].ids:
                issue('MISSING_ANCHOR', source.relative_to(root), ref)
    return issues


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', required=True, type=Path)
    args = parser.parse_args()
    issues = validate(args.site)
    print(json.dumps(dict(passed=not issues, issues=issues), ensure_ascii=False, indent=2))
    return int(bool(issues))


if __name__ == '__main__':
    raise SystemExit(main())
