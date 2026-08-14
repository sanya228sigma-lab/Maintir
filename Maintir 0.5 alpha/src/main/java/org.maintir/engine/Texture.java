package org.maintir.engine;

import org.lwjgl.BufferUtils;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

public class Texture {
    private final int id;

    public Texture(String resourcePath) {
        ByteBuffer imageBuffer;
        try (InputStream in = Texture.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Текстура не найдена в папке resources: " + resourcePath);
            }

            byte[] bytes = in.readAllBytes();
            imageBuffer = BufferUtils.createByteBuffer(bytes.length);
            imageBuffer.put(bytes).flip();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения файла текстуры: " + resourcePath, e);
        }

        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        IntBuffer channels = BufferUtils.createIntBuffer(1);

        stbi_set_flip_vertically_on_load(true);

        // Загружаем картинку из буфера памяти, а не по прямому пути диска
        ByteBuffer image = stbi_load_from_memory(imageBuffer, width, height, channels, 4);

        if (image == null) {
            throw new RuntimeException("STB Image не смог декодировать " + resourcePath + " | Ошибка: " + stbi_failure_reason());
        }

        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);

        // Настройки пиксель-арта для 64x64
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);

        stbi_image_free(image);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void cleanup() {
        glDeleteTextures(id);
    }
}