"""Resource coverage and private metadata boundary checks for the feature release."""
import ast
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class FeatureResourceTests(unittest.TestCase):
    def test_all_five_languages_cover_every_feature_string(self):
        expected = None
        for folder in ('values', 'values-en', 'values-de', 'values-fr', 'values-ru'):
            path = ROOT / 'app/src/main/res' / folder / 'feature_strings.xml'
            items = ET.fromstring(path.read_text(encoding='utf-8')).findall('string')
            names = [item.attrib['name'] for item in items]
            self.assertEqual(len(names), len(set(names)))
            self.assertTrue(all(''.join(item.itertext()).strip() for item in items))
            expected = set(names) if expected is None else expected
            self.assertEqual(expected, set(names), folder)

    def test_chapter_projection_is_bounded_and_drops_private_fields(self):
        path = ROOT / 'app/src/main/res/raw/metadata_worker.py'
        source = ast.parse(path.read_text(encoding='utf-8'))
        nodes = [node for node in source.body if
                 isinstance(node, ast.FunctionDef) and node.name == 'compact' or
                 isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'FORMAT_FIELDS' for t in node.targets)]
        scope = {}
        exec(compile(ast.Module(body=nodes, type_ignores=[]), str(path), 'exec'), scope)
        chapter = dict(title='Real source chapter', start_time=1, end_time=3,
                       url='https://private.example/?token=secret', cookie='secret')
        result = scope['compact'](dict(chapters=[chapter]*501))
        self.assertEqual(500, len(result['chapters']))
        self.assertEqual({'title': 'Real source chapter', 'start_time': 1, 'end_time': 3}, result['chapters'][0])
        self.assertEqual([], scope['compact']({})['chapters'])


if __name__ == '__main__':
    unittest.main()
