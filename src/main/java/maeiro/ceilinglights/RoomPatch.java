package maeiro.ceilinglights;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.light.SourceRegister", methodName = "room", strictMatch = true)
public final class RoomPatch {
    @Patch.OnEnter
    public static void enter(@Patch.This Object register,
            @Patch.Argument(0) int id, @Patch.Argument(1) int x, @Patch.Argument(2) int y,
            @Patch.Argument(3) int z, @Patch.Argument(4) int width, @Patch.Argument(5) int height,
            @Patch.Argument(6) long room, @Patch.Argument(7) long building,
            @Patch.Argument(value = 8, readOnly = false) boolean active) {
        if (LightRuntime.replaceRoom(register, id, room, building, active)) active = false;
    }
}
