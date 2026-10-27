"""Static D12 integration checks: verifies wiring, not NeoForge runtime behavior."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge-mod/src/main/java/dev/coldatomcraft/neoforge"
ASSETS = ROOT / "neoforge-mod/src/main/resources/assets/coldatomcraft"

class D12IntegrationContract(unittest.TestCase):
    def source(self, path):
        return (JAVA / path).read_text(encoding="utf-8")

    def test_right_click_opens_menu_not_direct_toggle(self):
        body = self.source("preview/DiagnosticLaserBlock.java")
        self.assertIn('serverPlayer.openMenu(source)', body)
        self.assertIn('useWithoutItem(', body)
        self.assertIn('useItemOn(', body)
        self.assertNotIn('player.isShiftKeyDown()', body)

    def test_registered_block_entity_and_menu(self):
        body = self.source("preview/PreviewBlocks.java")
        for expected in ['Registries.BLOCK_ENTITY_TYPE', 'Registries.MENU',
                'LASER_BLOCK_ENTITY', 'LASER_MENU', 'BLOCK_ENTITIES.register(bus)', 'MENUS.register(bus)']:
            self.assertIn(expected, body)

    def test_client_only_gui_registration(self):
        body = self.source("client/LaserControlScreen.java")
        self.assertIn('value = Dist.CLIENT', body)
        self.assertIn('RegisterMenuScreensEvent', body)
        self.assertIn('handleInventoryButtonClick', body)
        self.assertIn('AbstractSliderButton', body)
        self.assertIn('BUTTON_TOGGLE', body)

    def test_saved_and_network_synced_block_entity(self):
        body = self.source("preview/LaserSettingsBlockEntity.java")
        for expected in ['saveAdditional(ValueOutput', 'loadAdditional(ValueInput',
                'getUpdatePacket()', 'getUpdateTag(', 'sendBlockUpdated(',
                'LaserTuning.fromStored(', 'setChanged()']:
            self.assertIn(expected, body)

    def test_server_actions_authenticated_and_bounded(self):
        body = self.source("preview/LaserControlMenu.java")
        for expected in ['stillValid(player)', 'distanceToSqr(',
                'source.tuning().withField(field, candidate)', 'broadcastChanges()',
                'player.level().isClientSide()' ]:
            # level.isClientSide() is checked directly by the server menu
            if expected == 'player.level().isClientSide()':
                expected = 'level.isClientSide()'
            self.assertIn(expected, body)

    def test_source_specific_meter_and_client_beam(self):
        self.assertIn('source.tuning()', self.source('preview/DiagnosticBeamScan.java'))
        client = self.source('client/DiagnosticBeamParticles.java')
        self.assertIn('LaserSettingsBlockEntity source', client)
        self.assertIn('displayedRadiusBlocks(zMeters, tuning)', client)
        self.assertIn('PreviewBeamColour.rgb(tuning.wavelengthNanometers())', client)

    def test_jars_require_d12_classes(self):
        inspected = (ROOT / 'tools/inspect_mod_jar.py').read_text()
        gradle = (ROOT / 'neoforge-mod/build.gradle').read_text()
        for name in ['LaserTuning.class', 'LaserSettingsBlockEntity.class', 'LaserControlMenu.class',
                     'LaserControlScreen.class', 'PreviewBeamColour.class']:
            self.assertIn(name, inspected)
            self.assertIn(name, gradle)

    def test_translation_and_asset_json(self):
        for locale in ['zh_cn', 'en_us']:
            data = json.loads((ASSETS / 'lang' / (locale + '.json')).read_text(encoding='utf-8'))
            for key in ['title', 'power_on', 'power_off', 'direction', 'power',
                        'wavelength', 'waist', 'offset', 'done', 'disclaimer']:
                self.assertIn('gui.coldatomcraft.laser.' + key, data)
        for p in ASSETS.rglob('*.json'):
            json.loads(p.read_text(encoding='utf-8'))

if __name__ == '__main__':
    unittest.main()
