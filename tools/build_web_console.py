"""Assemble offline console sources; --check detects stale packaged resources."""
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'feature/feature_app/src/main/web'
OUTPUT = ROOT / 'feature/feature_app/src/main/res/raw/web_console.html'


def render():
    template = (SOURCE / 'index.html').read_text(encoding='utf-8')
    def include(match):
        name = match.group(1)
        source = (SOURCE / name).read_text(encoding='utf-8').rstrip() + '\n'
        tag = 'style' if name.endswith('.css') else 'script'
        return '<' + tag + '>\n' + source + '</' + tag + '>'
    return re.sub(r'\{\{([a-z-]+\.(?:js|css))\}\}', include, template)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    expected = render()
    if args.check:
        if OUTPUT.read_text(encoding='utf-8') != expected:
            raise SystemExit('Console resource is stale. Run python tools/build_web_console.py')
        print('Offline console source/resource consistency passed')
    else:
        OUTPUT.write_text(expected, encoding='utf-8')
        print('Offline console resource generated')
