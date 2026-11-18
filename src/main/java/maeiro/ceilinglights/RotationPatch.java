package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.world.PackGather", methodName = "place", strictMatch = true)
public final class RotationPatch {
    @Patch.OnExit(suppress = Throwable.class)
    public static void exit(@Patch.AllArguments Object[] args, @Patch.Return Object op) {
        EditorModels.rotate(op);
    }
}
