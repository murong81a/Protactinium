#!/usr/bin/env python3
"""Point mixins at third-party classes by name, so the annotation processor stops remapping them.

The Mixin annotation processor treats every `@Mixin(SomeClass.class)` as a Minecraft class and
tries to look the members up in the MCP->SRG tables. A target that belongs to another mod is not in
those tables, so the processor fails the compile with "Mixin target ... could not be found" even
though the mixin itself is correct and `remap = false` already tells the runtime not to remap it.

The fix is to express the target the way Mixin expects for a non-Minecraft class:

    @Mixin(SomeModClass.class)                     ->  @Mixin(targets = "pkg.SomeModClass", remap = false)

This script performs that rewrite for the targets it is given, and leaves every other mixin alone.
It also removes the now-unused import of the mod class, because keeping a compile-time reference to
a class that only exists in a mod jar would defeat the purpose.
"""

import pathlib
import re
import sys

# (file-name, fully-qualified mod class)
TARGETS = [
    ("MekanismGlowBlendMixin.java", "mekanism.client.render.MekanismRenderer"),
    ("MixinGlStateTracker.java", "codechicken.lib.render.state.GlStateTracker"),
    ("MixinItemRenderWeaponBase.java", "com.hbm.render.item.weapon.sedna.ItemRenderWeaponBase"),
    ("MixinKirinoConfigHub.java", "com.cleanroommc.kirino.config.KirinoConfigHub"),
    ("MixinParticleCollection.java", "org.orecruncher.dsurround.client.fx.particle.mote.ParticleCollection"),
    ("MixinRenderUtil.java", "com.hbm.util.RenderUtil"),
    ("MixinTabConfigurationTransfer.java", "cofh.core.gui.element.tab.TabConfigurationTransfer"),
]


def rewrite(text: str, qualified: str) -> tuple[str, bool]:
    simple = qualified.rsplit(".", 1)[-1]
    original = text

    # @Mixin(Foo.class) -> @Mixin(targets = "pkg.Foo", remap = false)
    pattern = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?" + re.escape(simple) + r"\.class\s*\)")
    text = pattern.sub(f'@Mixin(targets = "{qualified}", remap = false)', text)

    # @Mixin(value = Foo.class, remap = false) and similar shapes
    pattern2 = re.compile(
        r"@Mixin\(\s*value\s*=\s*" + re.escape(simple) + r"\.class\s*,\s*remap\s*=\s*false\s*\)"
    )
    text = pattern2.sub(f'@Mixin(targets = "{qualified}", remap = false)', text)

    # @Mixin(value = Foo.class, ...) with other options: drop the class literal, keep the rest.
    pattern3 = re.compile(r"@Mixin\(\s*value\s*=\s*" + re.escape(simple) + r"\.class\s*,")
    text = pattern3.sub(f'@Mixin(targets = "{qualified}",', text)

    # The import is no longer needed and would keep a compile dependency on a mod class.
    text = re.sub(r"^import " + re.escape(qualified) + r";\r?\n", "", text, flags=re.MULTILINE)
    return text, text != original


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    changed = 0
    for name, qualified in TARGETS:
        matches = list(root.rglob(name))
        if not matches:
            print(f"not found: {name}")
            continue
        path = matches[0]
        text = path.read_text(encoding="utf-8")
        updated, did = rewrite(text, qualified)
        if did:
            path.write_text(updated, encoding="utf-8", newline="\n")
            changed += 1
            print(f"{name}: now targets {qualified} by name")
        else:
            print(f"{name}: no change")
    print(f"-- {changed} files rewritten")
    return 0


if __name__ == "__main__":
    sys.exit(main())
