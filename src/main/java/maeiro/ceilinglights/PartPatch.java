package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.world.PackGather", methodName = "part", strictMatch = true)
public final class PartPatch {
    @Patch.OnExit(suppress = Throwable.class)
    public static void exit(@Patch.AllArguments Object[] args, @Patch.Return boolean placed) {
        if (placed && args.length == 5) LightRuntime.capturePart(args);
    }
}
