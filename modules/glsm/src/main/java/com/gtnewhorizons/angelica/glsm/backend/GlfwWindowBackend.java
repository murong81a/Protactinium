package com.gtnewhorizons.angelica.glsm.backend;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFW;
// Upstream imports org.lwjgl.opengl.Display, whose LWJGL 2 meaning was "the window LWJGL
// itself created". On a 1.10.2 client the window belongs to Minecraft and is created through
// the lwjgl3ify compatibility layer, which is where the GLFW handle actually lives, so this
// import names that class instead. The lwjgl3ify runtime also redirects the LWJGL 2
// org.lwjgl.opengl.Display calls in already compiled mods to this same implementation.
import org.lwjglx.opengl.Display;

import java.util.List;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/** GLFW window operations used by the existing LWJGL runtime. */
// Keeps its LWJGL 3 GLFW types: lwjgl3ify has no org.lwjglx.glfw.GLFW, so a rewritten reference fails
// to link with NoClassDefFoundError the moment the window handle is queried.
@Lwjgl3Aware
public final class GlfwWindowBackend implements WindowBackend {
    @Override
    public long currentContextHandle() {
        return GLFW.glfwGetCurrentContext();
    }

    @Override
    public boolean supportsFileDrop() {
        return true;
    }

    @Override
    public void startFileDrop() {
        GlfwFileDropWatcher.start();
    }

    @Override
    public void stopFileDrop() {
        GlfwFileDropWatcher.stop();
    }

    @Override
    public List<String> pollDroppedFiles() {
        return GlfwFileDropWatcher.pollDroppedFiles();
    }

    @Override
    public void setExclusiveFullscreen(boolean fullscreen) {
        Display.setFullscreen(fullscreen);
    }

    @Override
    public void setSwapInterval(boolean vsync) {
        GLFW.glfwSwapInterval(vsync ? 1 : 0);
    }

    @Override
    public long refreshPeriodNanos() {
        final long window = Display.getWindow();
        if (window == 0L) return 0L;
        long monitor = GLFW.glfwGetWindowMonitor(window);
        if (monitor == 0L) monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) return 0L;
        final GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        return mode == null ? 0L : RenderBackend.periodFromIntegerHz(mode.refreshRate());
    }

    @Override
    public void pumpMessages() {
        Display.processMessages();
    }
}
