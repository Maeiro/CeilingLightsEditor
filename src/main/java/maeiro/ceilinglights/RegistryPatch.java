package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.light.SourceRegister", methodName = "begin", strictMatch = true)
public final class RegistryPatch {
    @Patch.OnEnter
    public static void enter(@Patch.AllArguments Object[] args) {
        LightRuntime.beginRegistry();
    }
}
