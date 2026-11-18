package maeiro.ceilinglights;

import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.light.SourceRegister", methodName = "on", strictMatch = true)
public final class SelectionPatch {
    @Patch.OnExit(suppress = Throwable.class)
    public static void exit(@Patch.Argument(0) List<Object> entries) {
        LightRuntime.filter(entries);
    }
}
