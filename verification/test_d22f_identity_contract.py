"""D22F contract checks; they do not substitute for NeoForge/JDK25 runtime proof."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
NEO=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview'
CORE=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'

def read(p): return p.read_text(encoding='utf-8')

class D22FIdentityContract(unittest.TestCase):
    def test_pure_identity_is_immutable_and_keeps_untrusted_data_quarantined(self):
        src=read(CORE/'PersistentApparatusIdentity.java')
        self.assertIn('final class PersistentApparatusIdentity',src)
        self.assertIn('LEGACY_MIGRATED',src)
        self.assertIn('QUARANTINED',src)
        self.assertIn('UUID.randomUUID()',src)
        self.assertIn('uuid.toString().equals(id)',src)
        self.assertIn('Long.MAX_VALUE',src)
        self.assertNotIn('net.minecraft.',src)
        self.assertNotIn('net.neoforged.',src)

    def test_block_identity_persisted_and_validated_on_server_only(self):
        src=read(NEO/'DiagnosticApparatusIdentityBlockEntity.java')
        for name in ('d22f_apparatus_uuid','d22f_apparatus_revision',
                     'loadAdditional(ValueInput', 'saveAdditional(ValueOutput',
                     'PersistentApparatusIdentity.restored(', 'onLoad()',
                     'confirmControlledChange(', 'level instanceof ServerLevel'):
            self.assertIn(name,src)
        self.assertNotIn('setBlockAndUpdate(',src)
        self.assertNotIn('DiagnosticLaserBlock.ENABLED',src)

    def test_optical_blocks_use_identity_bes_without_changing_optical_states(self):
        for fn in ('DiagnosticOpticBlock.java','DiagnosticPowerMeterBlock.java',
                   'DiagnosticOpticalTableBlock.java'):
            src=read(NEO/fn)
            self.assertIn('implements EntityBlock',src)
            self.assertIn('new DiagnosticApparatusIdentityBlockEntity(pos, state)',src)
        optic=read(NEO/'DiagnosticOpticBlock.java')
        meter=read(NEO/'DiagnosticPowerMeterBlock.java')
        self.assertIn('EnumProperty.create("port", OpticPortPreset.class)',optic)
        self.assertIn('BlockStateProperties.FACING',meter)
        self.assertIn('aperture", 0, 2',meter)

    def test_identity_be_registry_includes_exact_types(self):
        src=read(NEO/'PreviewBlocks.java')
        self.assertIn('"diagnostic_apparatus_identity"',src)
        for block in ('METER','MIRROR','SPLITTER','LENS','HALF_WAVE','OPTICAL_TABLE'):
            self.assertIn(block+'.get()',src)

    def test_existing_scanner_pins_optic_meter_table_and_fails_closed(self):
        src=read(NEO/'AutoPresetStageBlockEntity.java')
        for expected in ('tableIdentity','opticIdentity','meterIdentity',
                         'tableToken','opticToken','meterToken',
                         'identitiesStillMatch(', 'identityUsable()',
                         'confirmControlledChange(', 'Scan rig changed'):
            self.assertIn(expected,src)
        self.assertIn('if (!opticIdentity.confirmControlledChange(',src)
        self.assertNotIn('enableLaser(',src)

    def test_reader_rejects_quarantined_and_never_loads_chunks(self):
        src=read(NEO/'LoadedLabGeometryWorld.java')
        self.assertIn('Binding.PERSISTENT_BLOCK_ENTITY',src)
        self.assertIn('!identity.identityUsable()',src)
        self.assertIn('identity.identityToken()',src)
        self.assertIn('level.hasChunkAt(pos)',src)
        self.assertNotIn('level.getChunk(',src)
        self.assertNotIn('setBlockAndUpdate(',src)

    def test_preview_snapshot_preserves_old_session_evidence_and_adds_token(self):
        src=read(CORE/'LoadedApparatusSnapshot.java')
        for expected in ('BLOCKSTATE_ONLY','BLOCK_ENTITY_INSTANCE',
                         'PERSISTENT_BLOCK_ENTITY','PERSISTENT_ID_CHANGED',
                         'persistentReferences()', 'blockEntityIdentity() != item.observed().blockEntityIdentity()',
                         'duplicate D22F identity inside one rig'):
            self.assertIn(expected,src)
        self.assertIn('authenticatedHardwarePose() { return false; }',src)

    def test_old_manual_grid_slide_does_not_copy_original_identity(self):
        src=read(NEO/'DiagnosticStageBlock.java')
        self.assertIn('level.setBlockAndUpdate(target,optic)',src)
        self.assertIn('level.removeBlock(from,false)',src)
        self.assertIn('new device ID',src)
        self.assertIn('identity.identityUsable()',src)

    def test_real_junit_and_offline_gates_are_in_ci(self):
        ci=read(ROOT/'.github/workflows/ci.yml')
        for item in ('check_d22f_identity.py', 'check_d22f_junit_source.py',
                     'check_d22f_game_identity.py'):
            self.assertIn('python tools/'+item,ci)
        formal=read(ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/PersistentApparatusIdentityTest.java')
        self.assertGreaterEqual(formal.count('@Test'),18)
        inspector=read(ROOT/'tools/inspect_mod_jar.py')
        self.assertIn('PersistentApparatusIdentity.class',inspector)
        self.assertIn('DiagnosticApparatusIdentityBlockEntity.class',inspector)

    def test_engineering_document_forbids_fake_hardware_auth_and_instant_resume(self):
        doc=read(ROOT/'docs/D22F_PERSISTENT_APPARATUS_IDENTITY.md').lower()
        for keyword in ('uuid','nbt','legacy','quarantined','reload','not a',
                        'neoforge','java 25','motor','d22b','power meter'):
            self.assertIn(keyword,doc)

if __name__=='__main__': unittest.main()
