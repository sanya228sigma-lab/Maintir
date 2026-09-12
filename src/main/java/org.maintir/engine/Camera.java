package org.maintir.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {
    public Vector3f position = new Vector3f(16, 25, 16);
    public float yaw = -90.0f;
    public float pitch = 0.0f;

    private float lastX = 640, lastY = 360;
    private boolean firstMouse = true;

    public void handleMouse(long window) {
        double[] xpos = new double[1], ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);

        if (firstMouse) {
            lastX = (float) xpos[0];
            lastY = (float) ypos[0];
            firstMouse = false;
        }

        float xoffset = (float) xpos[0] - lastX;
        float yoffset = lastY - (float) ypos[0]; // Инвертировано по Y
        lastX = (float) xpos[0];
        lastY = (float) ypos[0];

        float sensitivity = 0.1f;
        yaw += xoffset * sensitivity;
        pitch += yoffset * sensitivity;

        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    public Vector3f getFront() {
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        return front.normalize();
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(getFront()), new Vector3f(0, 1, 0));
    }
}