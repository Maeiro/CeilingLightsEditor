package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.world.PackGather", methodName = "part", strictMatch = true)
public final class PartPatch {
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.AllArguments Object[] args, @Patch.Local("ceilingEditorPart") Object plan) {
        plan = EditorModels.beginPart(args);
        return EditorModels.skip(plan);
    }

    @Patch.OnExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Patch.Local("ceilingEditorPart") Object plan, @Patch.Return boolean placed) {
        EditorModels.endPart(plan, placed);
    }
}
