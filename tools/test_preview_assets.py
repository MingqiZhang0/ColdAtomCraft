#!/usr/bin/env python3
"""Validate the DEV placeholder JSON assets without running Minecraft.

Static checks only: they do not prove the 26.3 model bakery actually renders.
"""
import json
import math
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'neoforge-mod/src/main/resources/assets/coldatomcraft'
DATA = ROOT / 'neoforge-mod/src/main/resources/data/coldatomcraft'


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


class PreviewAssetTests(unittest.TestCase):
    def test_state_coverage(self):
        fixtures = [
            ('diagnostic_laser', ['enabled=false', 'enabled=true']),
            ('diagnostic_power_meter', [f'aperture={i}' for i in range(3)]),
        ]
        for name, settings in fixtures:
            variants = read_json(ASSETS / 'blockstates' / f'{name}.json')['variants']
            expected = {f'{setting},facing={face}' for setting in settings
                        for face in ('north', 'east', 'south', 'west')}
            with self.subTest(name=name):
                self.assertEqual(expected, set(variants))

    def test_model_references_and_rotation(self):
        yaw = {'south':0,'east':270,'north':180,'west':90}
        for name in ('diagnostic_laser','diagnostic_power_meter'):
            variants = read_json(ASSETS/'blockstates'/f'{name}.json')['variants']
            for state, definition in variants.items():
                face = state.split('facing=')[1]
                with self.subTest(name=name,state=state):
                    self.assertEqual(yaw[face], definition['y'])
                    self.assertTrue(definition['uvlock'])
                    namespace, _, model_id = definition['model'].partition(':')
                    self.assertEqual('coldatomcraft', namespace)
                    self.assertTrue((ASSETS/'models'/f'{model_id}.json').is_file())

    def test_all_models_are_vanilla_textured_cuboids(self):
        models = sorted((ASSETS/'models'/'block').glob('*.json'))
        self.assertEqual(5, len(models))
        for p in models:
            value = read_json(p)
            with self.subTest(model=p.name):
                self.assertEqual('minecraft:block/block', value['parent'])
                self.assertGreaterEqual(len(value['elements']), 4)
                textures = value['textures']
                for k, v in textures.items():
                    self.assertTrue(v.startswith('minecraft:block/'), (p.name,k,v))
                for elt in value['elements']:
                    self.assertTrue(all(0 <= x <= 16 for x in elt['from']+elt['to']))
                    self.assertTrue(all(a < b for a,b in zip(elt['from'],elt['to'])))
                    self.assertEqual({'north','south','east','west','up','down'}, set(elt['faces']))
                    for desc in elt['faces'].values():
                        self.assertIn(desc['texture'][1:], textures)


    def test_model_forward_matches_logic_facing(self):
        # Minecraft blockstate yaw is opposite to +Y right-handed mathematical rotation.
        expected = {'south':(0,1),'north':(0,-1),'east':(1,0),'west':(-1,0)}
        for name in ('diagnostic_laser','diagnostic_power_meter'):
            variants=read_json(ASSETS/'blockstates'/f'{name}.json')['variants']
            for state, definition in variants.items():
                facing=state.split('facing=')[1]
                angle=math.radians(definition['y'])
                actual=(-round(math.sin(angle)),round(math.cos(angle)))
                with self.subTest(model=name,state=state):
                    self.assertEqual(expected[facing],actual)

    def test_optical_port_anchor_matches_placeholder_geometry(self):
        for suffix,role in (('diagnostic_laser_on','led'),('diagnostic_power_meter_0','opening')):
            model=read_json(ASSETS/'models'/'block'/f'{suffix}.json')
            fronts=[e['to'][2] for e in model['elements']
                    if e['faces']['south']['texture']==('#'+role)]
            self.assertEqual([15],fronts)
            self.assertEqual(7/16,(fronts[0]-8)/16)

    def test_item_definitions_resolve_to_block_model(self):
        for name in ('diagnostic_laser','diagnostic_power_meter'):
            with self.subTest(item=name):
                definition=read_json(ASSETS/'items'/f'{name}.json')['model']
                self.assertEqual('minecraft:model',definition['type'])
                self.assertTrue(definition['model'].startswith('coldatomcraft:block/'))
                path=ASSETS/'models'/(definition['model'].split(':',1)[1]+'.json')
                self.assertTrue(path.exists())

    def test_translations_are_complete(self):
        english=read_json(ASSETS/'lang'/'en_us.json')
        chinese=read_json(ASSETS/'lang'/'zh_cn.json')
        self.assertEqual(set(english),set(chinese))
        self.assertIn('itemGroup.coldatomcraft.lab_preview',english)
        for name in ('diagnostic_laser','diagnostic_power_meter'):
            self.assertIn('block.coldatomcraft.'+name,english)

    def test_block_loot_tables_exist(self):
        for name in ('diagnostic_laser','diagnostic_power_meter'):
            with self.subTest(block=name):
                value=read_json(DATA/'loot_table'/'blocks'/f'{name}.json')
                self.assertEqual('minecraft:block',value['type'])
                self.assertEqual('coldatomcraft:'+name,
                                 value['pools'][0]['entries'][0]['name'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
