package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "ceilinglights.Lamps", methodName = "square", strictMatch = true)
public final class CapturePatch {
    @Patch.OnEnter
    public static void enter(@Patch.AllArguments Object[] args) {
        LightRuntime.beginCapture(args);
    }

    @Patch.OnExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Patch.Thrown Throwable thrown) {
        LightRuntime.endCapture(thrown);
    }
}
