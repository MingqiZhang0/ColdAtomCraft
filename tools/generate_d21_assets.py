#!/usr/bin/env python3
"""Regenerate D21 DEV-only native cuboid placeholders (not Owner Blender art)."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft'
DATA=ROOT/'neoforge-mod/src/main/resources/data/coldatomcraft'


def put(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n',encoding='utf-8')


def cube(lo,hi,texture):
    return {'from':lo,'to':hi,'faces':{d:{'texture':'#'+texture} for d in ('north','south','east','west','up','down')}}

models={
 'diagnostic_optical_table': {
    'textures': {'base':'minecraft:block/polished_andesite','top':'minecraft:block/light_gray_concrete',
                 'grid':'minecraft:block/iron_block','particle':'minecraft:block/polished_andesite'},
    'elements': [cube([0,0,0],[16,10,16],'base'),cube([0,10,0],[16,12,16],'top'),
                 cube([2,12,0],[3,13,16],'grid'),cube([13,12,0],[14,13,16],'grid'),
                 cube([0,12,2],[16,13,3],'grid'),cube([0,12,13],[16,13,14],'grid')]},
 'diagnostic_power_logger': {
    'textures': {'base':'minecraft:block/polished_andesite','case':'minecraft:block/iron_block',
                 'screen':'minecraft:block/black_concrete','marker':'minecraft:block/lime_concrete',
                 'particle':'minecraft:block/iron_block'},
    'elements':[cube([2,0,2],[14,3,14],'base'),cube([3,3,3],[13,11,13],'case'),
                cube([4,11,4],[12,12,12],'screen'),cube([6,12,5],[10,13,9],'marker')]},
 'diagnostic_linear_stage': {
    'textures': {'rail':'minecraft:block/iron_block','surface':'minecraft:block/polished_andesite',
                 'marker':'minecraft:block/orange_concrete','particle':'minecraft:block/iron_block'},
    'elements':[cube([1,0,1],[15,3,15],'surface'),cube([3,3,0],[5,5,16],'rail'),
                cube([11,3,0],[13,5,16],'rail'),cube([6,3,4],[10,6,12],'marker')]},
 'diagnostic_rotary_stage': {
    'textures': {'base':'minecraft:block/polished_andesite','ring':'minecraft:block/iron_block',
                 'marker':'minecraft:block/yellow_concrete','particle':'minecraft:block/iron_block'},
    'elements':[cube([1,0,1],[15,3,15],'base'),cube([3,3,3],[13,4,13],'ring'),
                cube([5,4,5],[11,6,11],'base'),cube([7,6,11],[9,7,14],'marker')]},
 'diagnostic_auto_preset_stage': {
    'textures': {'base':'minecraft:block/polished_andesite', 'ring':'minecraft:block/iron_block',
                 'marker':'minecraft:block/redstone_block','particle':'minecraft:block/iron_block'},
    'elements':[cube([1,0,1],[15,3,15],'base'),cube([3,3,3],[13,4,13],'ring'),
                cube([5,4,5],[11,6,11],'base'),cube([7,6,11],[9,8,14],'marker')]},
}

for name, value in models.items():
    put(ASSETS/'models/block'/f'{name}.json',{'parent':'minecraft:block/block','ambientocclusion':False,**value})
    states={'':{'model':f'coldatomcraft:block/{name}'}}
    if name in ('diagnostic_linear_stage','diagnostic_rotary_stage'):
        yaw={'south':0,'east':270,'north':180,'west':90}
        states={f'facing={face}':{'model':f'coldatomcraft:block/{name}','y':angle,'uvlock':True}
                for face,angle in yaw.items()}
    put(ASSETS/'blockstates'/f'{name}.json',{'variants':states})
    put(ASSETS/'items'/f'{name}.json',{'model':{'type':'minecraft:model',
                                               'model':f'coldatomcraft:block/{name}'}})
    put(DATA/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[
        {'rolls':1,'entries':[{'type':'minecraft:item','name':f'coldatomcraft:{name}'}]}]})
translations={
 'diagnostic_optical_table':('Optical Breadboard (DEV)','光学平台（开发预览）'),
 'diagnostic_power_logger':('Power Data Logger (DEV)','光功率数据记录仪（开发预览）'),
 'diagnostic_linear_stage':('Manual Linear Rail (DEV)','手动平移滑轨（开发预览）'),
 'diagnostic_rotary_stage':('Manual Rotary Mount (DEV)','手动旋转台（开发预览）'),
 'diagnostic_auto_preset_stage':('Automatic Four-Preset Scan Stage (DEV)','自动四档光学扫描台（开发预览）'),
}

# D21B GUI keys must be restored when the historical D10/D14 asset generator
# rebuilds the language files. These are engineering UI strings, not Blender art.
D21B_WORKSTATION_TRANSLATIONS = {
    'gui.coldatomcraft.logger.all_recent': ('Range: all 512 recent', '范围：最近 512 点'),
    'gui.coldatomcraft.logger.close': ('Done', '关闭'),
    'gui.coldatomcraft.logger.error': ('STORAGE ERROR - sampling stopped', '存储错误：采样已停止'),
    'gui.coldatomcraft.logger.export': ('Export CSV / SVG', '导出 CSV / SVG'),
    'gui.coldatomcraft.logger.fifteen_minutes': ('Range: 15 min (ticks)', '范围：15 分钟（游戏时钟）'),
    'gui.coldatomcraft.logger.five_minutes': ('Range: 5 min (ticks)', '范围：5 分钟（游戏时钟）'),
    'gui.coldatomcraft.logger.newer': ('Newer', '更新'),
    'gui.coldatomcraft.logger.no_points': ('No saved samples in this range.', '这个时间范围内没有采样点'),
    'gui.coldatomcraft.logger.older': ('Older', '更早'),
    'gui.coldatomcraft.logger.show_history': ('Disk history', '磁盘历史'),
    'gui.coldatomcraft.logger.show_recent': ('Recent 512', '最近512点'),
    'gui.coldatomcraft.logger.archive_page': ('Archive page', '存档页'),
    'gui.coldatomcraft.logger.archive_note': ('Server CSV: <=16 KiB reads; Export saves this page', '服务器CSV每页最多读16 KiB；导出当前页'),
    'gui.coldatomcraft.logger.compare_off': ('Compare: OFF', '双通道：关闭'),
    'gui.coldatomcraft.logger.compare_on': ('Compare: ON', '双通道：开启'),
    'gui.coldatomcraft.logger.compare_next': ('Next recorder', '切换记录仪'),
    'gui.coldatomcraft.logger.compare_note': ('A / B: same game-tick axis, no interpolation; export 2-channel page', 'A/B 共用游戏时间轴；无插值；导出双通道页'),
    'gui.coldatomcraft.logger.history_error': ('Archive error: see server log', '历史数据异常：请检查服务器'),
    'gui.coldatomcraft.logger.one_minute': ('Range: 1 min (ticks)', '范围：1 分钟（游戏时钟）'),
    'gui.coldatomcraft.logger.page': ('Page', '页'),
    'gui.coldatomcraft.logger.pause': ('Pause recording', '暂停记录'),
    'gui.coldatomcraft.logger.preview_note': ('Game ticks, not wall time; invalid points are gaps.', '游戏 Tick 非真实时间；无效点留空；刷新更新数据。'),
    'gui.coldatomcraft.logger.record': ('Start recording', '开始记录'),
    'gui.coldatomcraft.logger.refresh': ('Refresh', '刷新'),
    'gui.coldatomcraft.logger.state_paused': ('Paused - server data snapshot', '已暂停：服务端数据快照'),
    'gui.coldatomcraft.logger.state_recording': ('Recording (100 game ticks / sample)', '记录中（每 100 游戏 Tick 采样）'),
    'gui.coldatomcraft.logger.syncing': ('Synchronizing server samples...', '正在同步服务器数据点……'),
    'gui.coldatomcraft.logger.title': ('CAC Power Measurement Workstation', 'CAC 光功率测量工作站'),
}

for locale,index in (('en_us',0),('zh_cn',1)):
    path=ASSETS/'lang'/f'{locale}.json'
    content=json.loads(path.read_text(encoding='utf-8'))
    for name,titles in translations.items():
        content[f'block.coldatomcraft.{name}']=titles[index]
    for key,titles in D21B_WORKSTATION_TRANSLATIONS.items():
        content[key]=titles[index]
    put(path,content)
print('D21 assets: optical table, logger, linear/rotary stage')
