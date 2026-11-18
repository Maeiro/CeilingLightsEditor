package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.light.LightSources", methodName = "forget", strictMatch = true)
public final class ForgetPatch {
    @Patch.OnEnter
    public static void enter() {
        LightRuntime.forget();
    }
}
