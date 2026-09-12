package org.maintir.engine;

import org.lwjgl.BufferUtils;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.glTexSubImage3D;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

public class TextureArray {
    private final int id;

    public TextureArray(String[] resourcePaths) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);

        int width = 64;
        int height = 64;
        int layers = resourcePaths.length;

        // Выделяем память под массив слоев 64x64
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        for (int i = 0; i < layers; i++) {
            ByteBuffer image = loadResourceToBuffer(resourcePaths[i]);
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, image);
        }

        // Фильтрация для пиксель-арта
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        // Анизотропная фильтрация (если доступна): резче текстуры под углом, без шевеления
        if (org.lwjgl.opengl.GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float maxAniso = org.lwjgl.opengl.GL11.glGetFloat(0x84FF); // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            glTexParameterf(GL_TEXTURE_2D_ARRAY, 0x84FE, Math.min(8.0f, maxAniso)); // GL_TEXTURE_MAX_ANISOTROPY_EXT
        }
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    private ByteBuffer loadResourceToBuffer(String path) {
        try (InputStream in = TextureArray.class.getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("Не найдена текстура: " + path);
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();

            IntBuffer w = BufferUtils.createIntBuffer(1);
            IntBuffer h = BufferUtils.createIntBuffer(1);
            IntBuffer c = BufferUtils.createIntBuffer(1);

            org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer img = org.lwjgl.stb.STBImage.stbi_load_from_memory(buffer, w, h, c, 4);
            if (img == null) throw new RuntimeException("Ошибка декодирования STB: " + path);
            return img;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки: " + path, e);
        }
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    public void cleanup() {
        glDeleteTextures(id);
    }
}