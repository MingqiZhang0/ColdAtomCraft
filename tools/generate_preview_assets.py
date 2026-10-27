import json
from pathlib import Path
root=Path(__file__).resolve().parents[1] / 'neoforge-mod/src/main/resources'
base=root/'assets'/'coldatomcraft'

def write_json(p,obj):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf8')

def box(frm,to,texture):
    return {'from':frm,'to':to,'faces':{face:{'texture':f'#{texture}'} for face in ('north','south','east','west','up','down')}}

def model(kind,variant):
    if kind=='diagnostic_laser':
        lamp=('minecraft:block/redstone_block' if variant=='on' else 'minecraft:block/gray_concrete')
        els=[
          box([2,0,2],[14,3,14],'foot'),
          box([3,3,3],[13,11,13],'case'),
          box([4,11,4],[12,12,12],'display'),
          box([6,6,13],[10,10,14],'rim'),
          box([7,7,14],[9,9,15],'led'),
          box([1,6,5],[3,9,9],'rim'),
        ]
        textures={'foot':'minecraft:block/polished_andesite',
                  'case':'minecraft:block/iron_block',
                  'display':'minecraft:block/black_concrete',
                  'rim':'minecraft:block/deepslate_tiles',
                  'led':lamp}
    else:
        color=['minecraft:block/light_blue_concrete','minecraft:block/lime_concrete','minecraft:block/purple_concrete'][variant]
        els=[
          box([2,0,2],[14,3,14],'foot'),
          box([3,3,3],[13,12,13],'case'),
          box([4,12,4],[12,13,12],'screen'),
          box([5,5,13],[11,11,14],'rim'),
          box([7,7,14],[9,9,15],'opening'),
          box([12,7,6],[15,10,10],'preset'),
        ]
        textures={'foot':'minecraft:block/polished_andesite',
                  'case':'minecraft:block/light_gray_concrete',
                  'screen':'minecraft:block/black_concrete',
                  'rim':'minecraft:block/iron_block',
                  'opening':'minecraft:block/black_concrete',
                  'preset':color}
    textures['particle']=textures['case']
    return {'parent':'minecraft:block/block','ambientocclusion':False,'textures':textures,'elements':els}

for kind,variants in [('diagnostic_laser',['off','on']),('diagnostic_power_meter',[0,1,2])]:
    states={}
    for variant in variants:
        mdl=f'coldatomcraft:block/{kind}_{variant}'
        write_json(base/'models'/'block'/f'{kind}_{variant}.json',model(kind,variant))
        for facing,rot in [('south',0),('east',270),('north',180),('west',90)]:
            attr=('enabled='+('true' if variant=='on' else 'false') if kind=='diagnostic_laser' else f'aperture={variant}')
            states[f'{attr},facing={facing}']={'model':mdl,'y':rot,'uvlock':True}
    write_json(base/'blockstates'/f'{kind}.json',{'variants':states})
    first=variants[0]
    write_json(base/'items'/f'{kind}.json',{'model':{'type':'minecraft:model','model':f'coldatomcraft:block/{kind}_{first}'}})
    write_json(root/'data'/'coldatomcraft'/'loot_table'/'blocks'/f'{kind}.json',
      {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'coldatomcraft:{kind}'}],
          'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

write_json(base/'lang'/'en_us.json', {
'itemGroup.coldatomcraft.lab_preview':'CAC Diagnostic Lab (DEV)',
'block.coldatomcraft.diagnostic_laser':'Diagnostic Laser (Placeholder)',
'block.coldatomcraft.diagnostic_power_meter':'Diagnostic Power Meter (Placeholder)',
'message.coldatomcraft.preview.laser_rotated':'[CAC DEV] Laser points %s',
'message.coldatomcraft.preview.laser_on':'[CAC DEV] Laser shutter OPEN (test preset)',
'message.coldatomcraft.preview.laser_off':'[CAC DEV] Laser shutter CLOSED',
'message.coldatomcraft.preview.aperture':'[CAC DEV] Aperture radius: %s mm',
'message.coldatomcraft.preview.reading':'[CAC DEV] Ideal power: %s mW (distance %s blocks)',
'message.coldatomcraft.preview.no_source':'[CAC DEV] No diagnostic source in front (24 blocks)',
'message.coldatomcraft.preview.blocked':'[CAC DEV] Optical route blocked by a non-air block',
'message.coldatomcraft.preview.unloaded':'[CAC DEV] Optical route enters an unloaded chunk',
'message.coldatomcraft.preview.wrong_facing':'[CAC DEV] Source output points the wrong way',
'message.coldatomcraft.preview.shutter_closed':'[CAC DEV] Source found, shutter is closed',
})
write_json(base/'lang'/'zh_cn.json', {
'itemGroup.coldatomcraft.lab_preview':'CAC 实验交互测试（开发）',
'block.coldatomcraft.diagnostic_laser':'测试激光器（占位模型）',
'block.coldatomcraft.diagnostic_power_meter':'测试光功率计（占位模型）',
'message.coldatomcraft.preview.laser_rotated':'[CAC 测试] 激光器转向：%s',
'message.coldatomcraft.preview.laser_on':'[CAC 测试] 激光器已开启（演示参数）',
'message.coldatomcraft.preview.laser_off':'[CAC 测试] 激光器已关闭',
'message.coldatomcraft.preview.aperture':'[CAC 测试] 探测孔径半径：%s mm',
'message.coldatomcraft.preview.reading':'[CAC 测试] 理想光功率：%s mW（间隔 %s 格）',
'message.coldatomcraft.preview.no_source':'[CAC 测试] 正前方 24 格内没有测试光源',
'message.coldatomcraft.preview.blocked':'[CAC 测试] 光路被非空气方块遮挡',
'message.coldatomcraft.preview.unloaded':'[CAC 测试] 光路进入未加载区块',
'message.coldatomcraft.preview.wrong_facing':'[CAC 测试] 激光器朝向错误',
'message.coldatomcraft.preview.shutter_closed':'[CAC 测试] 找到激光器，但快门关闭',
})
print('assets done')
