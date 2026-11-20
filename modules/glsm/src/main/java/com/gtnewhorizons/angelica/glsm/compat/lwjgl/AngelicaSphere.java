package com.gtnewhorizons.angelica.glsm.compat.lwjgl;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.util.glu.Sphere;

import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.util.glu.GLU.GLU_FILL;
import static org.lwjgl.util.glu.GLU.GLU_INSIDE;
import static org.lwjgl.util.glu.GLU.GLU_LINE;
import static org.lwjgl.util.glu.GLU.GLU_NONE;
import static org.lwjgl.util.glu.GLU.GLU_POINT;
import static org.lwjgl.util.glu.GLU.GLU_SILHOUETTE;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;

@SuppressWarnings("unused")
public class AngelicaSphere extends Sphere {

    private static final float PI = (float) Math.PI;

    private void emitNormal(float x, float y, float z) {
        float mag = (float) Math.sqrt(x * x + y * y + z * z);
        if (mag > 0.00001F) {
            x /= mag;
            y /= mag;
            z /= mag;
        }
        GLSMCurrentAttribs.glNormal3f(x, y, z);
    }

    private void emitTexCoord(float x, float y) {
        if (super.getTextureFlag()) {
            GLSMCurrentAttribs.glTexCoord2f(x, y);
        }
    }

    @Override
    public void draw(float radius, int slices, int stacks) {
        float drho;
        float dtheta;
        float ds;
        float dt;
        float nsign;
        float rho;
        float theta;
        float x;
        float y;
        float z;
        float s;
        float t;

        int imin;
        int imax;
        boolean normals = super.getNormals() != GLU_NONE;

        nsign = super.getOrientation() == GLU_INSIDE ? -1.0f : 1.0f;
        drho = PI / stacks;
        dtheta = 2.0f * PI / slices;

        if (super.getDrawStyle() == GLU_FILL) {
            if (!super.getTextureFlag()) {
                GLSMDraws.glBegin(GL_TRIANGLE_FAN);
                GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
                GLSMDraws.glVertex3f(0.0f, 0.0f, nsign * radius);
                for (int j = 0; j <= slices; j++) {
                    theta = j == slices ? 0.0f : j * dtheta;
                    x = -(float) Math.sin(theta) * (float) Math.sin(drho);
                    y = (float) Math.cos(theta) * (float) Math.sin(drho);
                    z = nsign * (float) Math.cos(drho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLSMDraws.glEnd();
            }

            ds = 1.0f / slices;
            dt = 1.0f / stacks;
            t = 1.0f;
            if (super.getTextureFlag()) {
                imin = 0;
                imax = stacks;
            } else {
                imin = 1;
                imax = stacks - 1;
            }

            for (int i = imin; i < imax; i++) {
                rho = i * drho;
                GLSMDraws.glBegin(GL_TRIANGLE_STRIP);
                s = 0.0f;
                for (int j = 0; j <= slices; j++) {
                    theta = j == slices ? 0.0f : j * dtheta;
                    x = -(float) Math.sin(theta) * (float) Math.sin(rho);
                    y = (float) Math.cos(theta) * (float) Math.sin(rho);
                    z = nsign * (float) Math.cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    emitTexCoord(s, t);
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                    x = -(float) Math.sin(theta) * (float) Math.sin(rho + drho);
                    y = (float) Math.cos(theta) * (float) Math.sin(rho + drho);
                    z = nsign * (float) Math.cos(rho + drho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    emitTexCoord(s, t - dt);
                    s += ds;
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLSMDraws.glEnd();
                t -= dt;
            }

            if (!super.getTextureFlag()) {
                GLSMDraws.glBegin(GL_TRIANGLE_FAN);
                GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, -1.0f);
                GLSMDraws.glVertex3f(0.0f, 0.0f, -radius * nsign);
                rho = PI - drho;
                s = 1.0f;
                for (int j = slices; j >= 0; j--) {
                    theta = j == slices ? 0.0f : j * dtheta;
                    x = -(float) Math.sin(theta) * (float) Math.sin(rho);
                    y = (float) Math.cos(theta) * (float) Math.sin(rho);
                    z = nsign * (float) Math.cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    s -= ds;
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLSMDraws.glEnd();
            }
        } else if (super.getDrawStyle() == GLU_LINE || super.getDrawStyle() == GLU_SILHOUETTE) {
            for (int i = 1; i < stacks; i++) {
                rho = i * drho;
                GLSMDraws.glBegin(GL_LINE_LOOP);
                for (int j = 0; j < slices; j++) {
                    theta = j * dtheta;
                    x = (float) Math.cos(theta) * (float) Math.sin(rho);
                    y = (float) Math.sin(theta) * (float) Math.sin(rho);
                    z = (float) Math.cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLSMDraws.glEnd();
            }
            for (int j = 0; j < slices; j++) {
                theta = j * dtheta;
                GLSMDraws.glBegin(GL_LINE_STRIP);
                for (int i = 0; i <= stacks; i++) {
                    rho = i * drho;
                    x = (float) Math.cos(theta) * (float) Math.sin(rho);
                    y = (float) Math.sin(theta) * (float) Math.sin(rho);
                    z = (float) Math.cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
                GLSMDraws.glEnd();
            }
        } else if (super.getDrawStyle() == GLU_POINT) {
            GLSMDraws.glBegin(GL_POINTS);
            if (normals) {
                GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, nsign);
            }
            GLSMDraws.glVertex3f(0.0f, 0.0f, radius);
            if (normals) {
                GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, -nsign);
            }
            GLSMDraws.glVertex3f(0.0f, 0.0f, -radius);
            for (int i = 1; i < stacks - 1; i++) {
                rho = i * drho;
                for (int j = 0; j < slices; j++) {
                    theta = j * dtheta;
                    x = (float) Math.cos(theta) * (float) Math.sin(rho);
                    y = (float) Math.sin(theta) * (float) Math.sin(rho);
                    z = (float) Math.cos(rho);
                    if (normals) {
                        emitNormal(x * nsign, y * nsign, z * nsign);
                    }
                    GLSMDraws.glVertex3f(x * radius, y * radius, z * radius);
                }
            }
            GLSMDraws.glEnd();
        }
    }
}
