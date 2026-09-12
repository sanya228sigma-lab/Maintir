#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in float aTexIndex; // Индекс текстуры в массиве
layout (location = 4) in float aSkyLight; // Небесный свет 0..1 (мягкий, по вершинам)
layout (location = 5) in float aBlockLight; // Свет от ламп 0..1

out vec2 TexCoord;
out vec3 Normal;
out float TexIndex;
out vec2 LightLevels;
out vec3 ViewPos;
out vec3 NormalView;
out vec3 LightDirView;
out vec3 WorldPos;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform vec3 lightDir;

void main() {
    TexCoord = aTexCoord;
    Normal = aNormal;
    TexIndex = aTexIndex;
    LightLevels = vec2(aSkyLight, aBlockLight);
    WorldPos = aPos;

    vec4 viewPos = view * model * vec4(aPos, 1.0);
    ViewPos = viewPos.xyz;
    NormalView = mat3(view) * aNormal;
    LightDirView = mat3(view) * lightDir;

    gl_Position = projection * viewPos;
}