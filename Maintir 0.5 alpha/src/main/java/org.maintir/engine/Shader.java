package org.maintir.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public class Shader {
    private final int programId;

    // Конструктор 1: Для чтения шейдеров из ФАЙЛОВ в resources
    public Shader(String vertPath, String fragPath) {
        this(loadSource(vertPath), loadSource(fragPath), true);
    }

    // Конструктор 2: Для шейдеров, переданных прямо СТРОКАМИ (используется в LoadingScreen)
    public Shader(String vertSource, String fragSource, boolean isSourceCode) {
        int vert = compileShader(vertSource, GL_VERTEX_SHADER);
        int frag = compileShader(fragSource, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vert);
        glAttachShader(programId, frag);
        glLinkProgram(programId);

        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    private static String loadSource(String path) {
        try (InputStream in = Shader.class.getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("Не найден файл шейдера: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения шейдера: " + path, e);
        }
    }

    private int compileShader(String code, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, code);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Ошибка компиляции шейдера: " + glGetShaderInfoLog(id));
        }
        return id;
    }

    public void use() {
        glUseProgram(programId);
    }

    public void setUniform(String name, Matrix4f matrix) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        matrix.get(buffer);
        glUniformMatrix4fv(glGetUniformLocation(programId, name), false, buffer);
    }

    public void setUniform(String name, Vector3f vector) {
        glUniform3f(glGetUniformLocation(programId, name), vector.x, vector.y, vector.z);
    }
}