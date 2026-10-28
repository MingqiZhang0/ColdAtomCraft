#!/usr/bin/env python3
"""Deterministic Minecraft-native DEV placeholders for D14 optical devices.

No Blender asset is invented or overwritten. Explicit rotations label the
front/input face; a selector-colour marker identifies the four test presets.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT/'neoforge-mod/src/main/resources'
ASSETS = MOD/'assets/coldatomcraft'
DATA = MOD/'data/coldatomcraft/loot_table/blocks'


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def cube(lo, hi, key):
    return {'from': list(lo), 'to':list(hi),
            'faces':{face:{'texture':'#'+key} for face in ('north','south','east','west','up','down')}}


kinds = {
    'diagnostic_mirror': ('minecraft:block/light_blue_concrete', 'Mirror', '反射镜'),
    'diagnostic_splitter': ('minecraft:block/cyan_concrete', '50:50 Splitter', '50:50 分束器'),
    'diagnostic_lens': ('minecraft:block/blue_stained_glass', 'Thin Lens', '理想薄透镜'),
    'diagnostic_half_wave_plate': ('minecraft:block/purple_stained_glass', 'Half-wave Plate', '半波片'),
}
variants = ['north','east','south','west','up','down']
# Model's front opening is in local +Z. Incoming travel SOUTH -> front NORTH.
rotations = {'north':{'y':0},'south':{'y':180},'east':{'y':90},'west':{'y':270},
             'up':{'x':90}, 'down':{'x':270}}
colours = ['minecraft:block/white_concrete','minecraft:block/yellow_concrete',
           'minecraft:block/lime_concrete','minecraft:block/red_concrete']
for kind, (face_texture, en, zh) in kinds.items():
    states = {}
    for selector in range(4):
        model_name = f'{kind}_{selector}'
        elements = [
            cube((2,0,2),(14,3,14),'base'),
            cube((7,3,7),(9,10,9),'post'),
            cube((3,10,3),(13,14,13),'housing'),
            cube((4,4,12),(12,12,15),'face'),
            cube((6,6,14),(10,10,16),'port'),
            cube((10,12,9),(13,15,12),'marker'),
        ]
        model = {
            'parent':'minecraft:block/block','ambientocclusion':False,
            'textures':{'base':'minecraft:block/polished_andesite',
                        'post':'minecraft:block/iron_block',
                        'housing':'minecraft:block/light_gray_concrete',
                        'face':face_texture, 'port':'minecraft:block/black_concrete',
                        'marker':colours[selector],
                        'particle':'minecraft:block/iron_block'},
            'elements':elements,
        }
        write_json(ASSETS/'models/block'/f'{model_name}.json',model)
        for direction in variants:
            state=f'port={direction}_{selector}'
            states[state]={'model':f'coldatomcraft:block/{model_name}',
                           **rotations[direction],'uvlock':True}
    write_json(ASSETS/'blockstates'/f'{kind}.json',{'variants':states})
    write_json(ASSETS/'items'/f'{kind}.json',{
        'model':{'type':'minecraft:model','model':f'coldatomcraft:block/{kind}_0'}})
    write_json(DATA/f'{kind}.json',{'type':'minecraft:block','pools':[
        {'rolls':1,'entries':[{'type':'minecraft:item','name':f'coldatomcraft:{kind}'}],
         'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

# Existing power meter's 6-direction front is required for vertical reflected paths.
meter = ASSETS/'blockstates/diagnostic_power_meter.json'
state = json.loads(meter.read_text(encoding='utf-8'))
for preset in range(3):
    for direction, rot in [('up',270),('down',90)]:
        state['variants'][f'aperture={preset},facing={direction}']={
            'model':f'coldatomcraft:block/diagnostic_power_meter_{preset}',
            'x':rot,'uvlock':True}
write_json(meter,state)

# D12 localization must remain regenerable even when the historical D10
# placeholder generator overwrites complete language files. Values below
# are copied exactly from the D13 source archive, not invented strings.
D12_LEGACY_GUI_TEXT = {
  "en_us": {
    "gui.coldatomcraft.laser.direction": "Output facing",
    "gui.coldatomcraft.laser.disclaimer": "DEV preview: ideal vacuum TEM00, not calibrated",
    "gui.coldatomcraft.laser.done": "Done",
    "gui.coldatomcraft.laser.left": "Turn Left",
    "gui.coldatomcraft.laser.offset": "Waist position",
    "gui.coldatomcraft.laser.power": "Output power",
    "gui.coldatomcraft.laser.power_off": "Laser: OFF (click to enable)",
    "gui.coldatomcraft.laser.power_on": "Laser: ON (click to disable)",
    "gui.coldatomcraft.laser.right": "Turn Right",
    "gui.coldatomcraft.laser.title": "CAC Laser Control Panel",
    "gui.coldatomcraft.laser.units": "Waist origin = output port; + means downstream",
    "gui.coldatomcraft.laser.waist": "Waist radius",
    "gui.coldatomcraft.laser.wavelength": "Vacuum wavelength",
    "message.coldatomcraft.preview.config_missing": "[CAC preview] Laser found, but its settings are not loaded"
  },
  "zh_cn": {
    "gui.coldatomcraft.laser.direction": "\u8f93\u51fa\u671d\u5411",
    "gui.coldatomcraft.laser.disclaimer": "\u5f00\u53d1\u9884\u89c8\uff1a\u7406\u60f3\u771f\u7a7a TEM00\uff0c\u975e\u6807\u5b9a\u4eea\u5668",
    "gui.coldatomcraft.laser.done": "\u5173\u95ed",
    "gui.coldatomcraft.laser.left": "\u5de6\u8f6c 90\u00b0",
    "gui.coldatomcraft.laser.offset": "\u675f\u8170\u4f4d\u7f6e",
    "gui.coldatomcraft.laser.power": "\u8f93\u51fa\u529f\u7387",
    "gui.coldatomcraft.laser.power_off": "\u6fc0\u5149\u5668\uff1a\u5173\u95ed\uff08\u70b9\u51fb\u5f00\u542f\uff09",
    "gui.coldatomcraft.laser.power_on": "\u6fc0\u5149\u5668\uff1a\u5f00\u542f\uff08\u70b9\u51fb\u5173\u95ed\uff09",
    "gui.coldatomcraft.laser.right": "\u53f3\u8f6c 90\u00b0",
    "gui.coldatomcraft.laser.title": "CAC \u6fc0\u5149\u5668\u53c2\u6570\u63a7\u5236\u53f0",
    "gui.coldatomcraft.laser.units": "\u675f\u8170\u4f4d\u7f6e\u4ee5\u8f93\u51fa\u53e3\u4e3a\u96f6\uff1b\u6b63\u503c\u8868\u793a\u524d\u65b9",
    "gui.coldatomcraft.laser.waist": "\u675f\u8170\u534a\u5f84",
    "gui.coldatomcraft.laser.wavelength": "\u771f\u7a7a\u6ce2\u957f",
    "message.coldatomcraft.preview.config_missing": "[CAC \u6d4b\u8bd5] \u627e\u5230\u6fc0\u5149\u5668\uff0c\u4f46\u53c2\u6570\u5c1a\u672a\u52a0\u8f7d"
  }
}

for locale in ['en_us','zh_cn']:
    path=ASSETS/'lang'/f'{locale}.json'
    lang=json.loads(path.read_text(encoding='utf-8'))
    lang.update(D12_LEGACY_GUI_TEXT[locale])
    for name,(texture,en,zh) in kinds.items():
        lang[f'block.coldatomcraft.{name}']= (en+' (DEV)' if locale=='en_us' else zh+'（开发）')
    if locale=='en_us':
        lang.update({
            'message.coldatomcraft.d14.optic_ports':'[CAC D14] Input travel: %s | output / preset: %s',
            'message.coldatomcraft.d14.reading':'[CAC D14] Ideal one-branch collected power: %s mW (local 24-block source scan)',
            'message.coldatomcraft.d14.ambiguous':'[CAC D14] %s rays reach meter: coherence/mode overlap unsupported, NO reading',
            'message.coldatomcraft.d14.incomplete':'[CAC D14] Optical network hit safety limit: NO measurement',
            'message.coldatomcraft.d14.no_beam':'[CAC D14] No compatible beam found in local source scan',
        })
    else:
        lang.update({
            'message.coldatomcraft.d14.optic_ports':'[CAC D14] 入射传播方向：%s | 输出或档位：%s',
            'message.coldatomcraft.d14.reading':'[CAC D14] 理想单光路接收功率：%s mW（局部 24 格光源搜索）',
            'message.coldatomcraft.d14.ambiguous':'[CAC D14] %s 条光路抵达探测器：未支持相干/模式合束，拒绝给出读数',
            'message.coldatomcraft.d14.incomplete':'[CAC D14] 光路触及安全计算上限：拒绝测量',
            'message.coldatomcraft.d14.no_beam':'[CAC D14] 局部光源搜索范围内未发现相容入射光束',
        })
    lang['gui.coldatomcraft.laser.invalid'] = ('Saved settings invalid; reapply a parameter!'
        if locale=='en_us' else '存档参数异常，请重新调整任一参数！')
    write_json(path,lang)

print('D14 assets: 4 optics x 24 states + vertical power meter, with DEV placeholders')

# D21 assets are additive, and must survive the existing D10/D14 generator.
from pathlib import Path as _D21Path
import runpy as _d21_runpy
_d21_script = _D21Path(__file__).with_name('generate_d21_assets.py')
if _d21_script.is_file():
    _d21_runpy.run_path(str(_d21_script), run_name='__main__')
