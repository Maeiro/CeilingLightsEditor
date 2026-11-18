package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.IsoCell;

@Patch(className = "viewpoint.light.LightSources", methodName = "update", strictMatch = true)
public final class UpdatePatch {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) IsoCell cell, @Patch.Argument(1) int player) {
        LightRuntime.update(cell);
    }
}
