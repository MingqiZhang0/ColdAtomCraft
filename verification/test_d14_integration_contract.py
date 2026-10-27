"""D14 integration guards; static checks do not pretend to be a game runtime."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MOD=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge'
ASSETS=ROOT/'neoforge-mod/src/main/resources/assets/coldatomcraft'


def read(path): return (ROOT/path).read_text(encoding='utf-8')

class D14IntegrationContract(unittest.TestCase):
    def test_four_optical_block_types_and_creative_items_are_registered(self):
        blocks=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/PreviewBlocks.java')
        for name in ['MIRROR','SPLITTER','LENS','HALF_WAVE']:
            self.assertIn(' '+name+' = BLOCKS.registerBlock(',blocks)
            self.assertIn(name+'_ITEM = ITEMS.registerSimpleBlockItem(',blocks)
            self.assertIn('output.accept('+name+'_ITEM.get());',blocks)
    def test_server_and_client_use_same_pure_solver(self):
        adapter=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java')
        server=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticBeamScan.java')
        client=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/client/DiagnosticBeamParticles.java')
        self.assertIn('PreviewOpticalNetwork3D.solve(sources, position -> at(level, position))',adapter)
        self.assertIn('OpticalBenchWorld.trace(level, sources)',server)
        self.assertIn('OpticalBenchWorld.trace(level, sources)',client)
        self.assertNotIn('setBlockAndUpdate(',client)
        self.assertNotIn('sendParticles(',client)
    def test_no_minecraft_imports_in_optical_solver(self):
        source=read('experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewOpticalNetwork3D.java')
        self.assertNotIn('net.minecraft.',source)
        self.assertNotIn('net.neoforged.',source)
        self.assertIn('ParaxialAbcd.thinLens(',source)
        self.assertIn('JonesMatrix.halfWave(',source)
        self.assertIn('GaussianCircularAperture.capture(',source)
        self.assertIn('Math.addExact(',source)
        self.assertIn('MAX_ELEMENTS_PER_BRANCH',source)
    def test_bad_multipath_readings_are_rejected_not_summed(self):
        solver=read('experiment-core/src/main/java/dev/coldatomcraft/experiment/PreviewOpticalNetwork3D.java')
        meter=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticBeamScan.java')
        self.assertIn('MULTIPLE_UNRESOLVED',solver)
        self.assertIn('if (hitsHere > 1)',solver)
        self.assertIn('case MULTIPLE_UNRESOLVED',meter)
        self.assertIn('case INCOMPLETE',meter)
    def test_server_scan_avoids_forcing_chunk_loading(self):
        adapter=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java')
        self.assertIn('if (!level.hasChunkAt(worldPos)) return new Unloaded()',adapter)
        self.assertIn('if (!level.hasChunkAt(probe)) continue',adapter)
        self.assertNotIn('level.getChunk(',adapter)
    def test_invalid_laser_storage_is_fail_closed(self):
        be=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/LaserSettingsBlockEntity.java')
        scan=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java')
        self.assertIn('configurationValid',be)
        self.assertIn('LaserTuning.isValidStored(',be)
        self.assertIn('configurationValid = true;',be)
        self.assertIn('output.putInt(VALID, configurationValid ? 1 : 0)',be)
        self.assertIn('&& source.configurationValid()',scan)
    def test_ports_are_six_direction_and_have_four_presets(self):
        state=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticPortPreset.java')
        for direction in ['NORTH','EAST','SOUTH','WEST','UP','DOWN']:
            for selector in range(4): self.assertIn(f'{direction}_{selector}(',state)
        self.assertIn('getSerializedName()',state)
    def test_device_states_persist_under_vanilla_blockstate(self):
        optic=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticOpticBlock.java')
        self.assertIn('EnumProperty.create("port", OpticPortPreset.class)',optic)
        self.assertIn('state.setValue(PORT, next)',optic)
        self.assertIn('createBlockStateDefinition(',optic)
        self.assertNotIn('newBlockEntity(',optic)
    def test_power_meter_can_face_all_cardinal_directions(self):
        meter=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticPowerMeterBlock.java')
        self.assertIn('BlockStateProperties.FACING',meter)
        asset=json.loads((ASSETS/'blockstates/diagnostic_power_meter.json').read_text(encoding='utf-8'))
        self.assertEqual(18,len(asset['variants']))
    def test_all_optic_assets_and_localization_exist(self):
        en=json.loads((ASSETS/'lang/en_us.json').read_text(encoding='utf-8'))
        zh=json.loads((ASSETS/'lang/zh_cn.json').read_text(encoding='utf-8'))
        self.assertEqual(set(en),set(zh))
        for kind in ['mirror','splitter','lens','half_wave_plate']:
            name='diagnostic_'+kind
            states=json.loads((ASSETS/'blockstates'/f'{name}.json').read_text(encoding='utf-8'))
            self.assertEqual(24,len(states['variants']))
            self.assertIn('block.coldatomcraft.'+name,zh)
            for s in states['variants'].values():
                model_name=s['model'].split(':',1)[1]
                self.assertTrue((ASSETS/'models'/f'{model_name}.json').is_file())
            self.assertTrue((ASSETS/'items'/f'{name}.json').is_file())
    def test_ci_has_formal_jdk25_and_offline_d14_gate(self):
        ci=read('.github/workflows/ci.yml')
        self.assertIn('java-version: \'25\'',ci)
        self.assertIn('python tools/check_d14_optics.py',ci)
        self.assertIn(':neoforge-mod:build',ci)
        self.assertIn('D14-optical-bench-neoforge',ci)
    def test_no_automatic_model_approval_or_world_energy_mutation(self):
        optic=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/DiagnosticOpticBlock.java')
        adapter=read('neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview/OpticalBenchWorld.java')
        self.assertNotIn('ModelApproval.OWNER_APPROVED',optic+adapter)
        self.assertNotIn('.hurt(',optic+adapter)
        self.assertNotIn('setBlockAndUpdate(',adapter)

if __name__=='__main__': unittest.main(verbosity=2)
