package com.dhj.actinium.compat.componentmodelhider;

import com.dhj.actinium.runtime.ActiniumRuntime;
import net.minecraft.util.math.BlockPos;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * The hider's API surface, isolated so {@link ComponentModelHiderCompat} and its call sites link
 * while the mod is absent. Only loaded once {@link ComponentModelHiderCompat#IS_LOADED} is true.
 *
 * <p>The hider is part of Cleanroom's modded ecosystem and has no 1.10.2 build, so its
 * {@code MultiblockWorldSavedData} is reached reflectively. The three calls the mesher needs are
 * exposed here and the hidden-state predicate stays owned by the mod, so the gating semantics do
 * not change: this is the mod's own answer, forwarded.</p>
 */
final class ComponentModelHiderBridge {

    private static final String SAVED_DATA_CLASS = "com.cleanroommc.multiblocked.persistence.MultiblockWorldSavedData";
    private static final String BUILDING_FIELD = "isBuildingChunk";

    private static volatile Class<?> savedDataClass;
    private static volatile MethodHandle isModelDisabled;
    private static volatile Field isBuildingChunk;

    private ComponentModelHiderBridge() {
    }

    /**
     * Whether {@code pos} is hidden. Consulted only while the hider's build flag is armed by
     * {@link #beginBuild()}; outside a build the mod's own gate reports every position as enabled.
     */
    static boolean isHidden(BlockPos pos) {
        try {
            MethodHandle handle = isModelDisabled;
            if (handle == null) {
                Class<?> type = savedDataClass();
                if (type == null) {
                    return false;
                }
                handle = MethodHandles.lookup().findStatic(type, "isModelDisabled",
                        MethodType.methodType(boolean.class, BlockPos.class));
                isModelDisabled = handle;
            }
            return (boolean) handle.invoke(pos);
        } catch (Throwable t) {
            ActiniumRuntime.logger().warn("Component Model Hider predicate failed; treating the position as visible", t);
            return false;
        }
    }

    /**
     * Sets the hider's per-thread build flag, arming its {@code BlockModelRenderer} redirects and
     * CodeChickenLib hooks.
     */
    static void beginBuild() {
        setBuilding(true);
    }

    /** Clears the flag set by {@link #beginBuild()}; the thread local defaults to false. */
    static void endBuild() {
        setBuilding(false);
    }

    private static void setBuilding(boolean building) {
        try {
            Field field = isBuildingChunk;
            if (field == null) {
                Class<?> type = savedDataClass();
                if (type == null) {
                    return;
                }
                field = type.getField(BUILDING_FIELD);
                field.setAccessible(true);
                isBuildingChunk = field;
            }
            Object threadLocal = field.get(null);
            if (threadLocal instanceof ThreadLocal) {
                @SuppressWarnings("unchecked")
                ThreadLocal<Boolean> flag = (ThreadLocal<Boolean>) threadLocal;
                flag.set(building);
            } else {
                ActiniumRuntime.logger().warn(
                        "Component Model Hider build flag is not a ThreadLocal; chunk builds will not be armed");
            }
        } catch (ReflectiveOperationException e) {
            ActiniumRuntime.logger().warn("Component Model Hider build flag could not be set", e);
        }
    }

    private static Class<?> savedDataClass() {
        Class<?> type = savedDataClass;
        if (type == null) {
            try {
                type = Class.forName(SAVED_DATA_CLASS);
                savedDataClass = type;
            } catch (ClassNotFoundException e) {
                ActiniumRuntime.logger().debug(
                        "Component Model Hider saved data not found; hidden blocks are not skipped", e);
                return null;
            }
        }
        return type;
    }
}
