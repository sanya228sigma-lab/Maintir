#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in float aTexIndex; // Индекс текстуры (0 = Трава, 1 = Камень)

out vec2 TexCoord;
out vec3 Normal;
out float TexIndex;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    TexCoord = aTexCoord;
    Normal = aNormal;
    TexIndex = aTexIndex;
    gl_Position = projection * view * model * vec4(aPos, 1.0);
}