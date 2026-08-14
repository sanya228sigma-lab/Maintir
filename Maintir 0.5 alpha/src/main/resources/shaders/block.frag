#version 330 core
in vec2 TexCoord;
in vec3 Normal;
in float TexIndex;

out vec4 FragColorOut;

uniform sampler2DArray uTextureArray;
uniform vec3 lightDir;

void main() {
    float ambient = 0.4;
    float diff = max(dot(Normal, normalize(lightDir)), 0.0);

    // Сэмплируем нужный слой массиве текстур
    vec4 texColor = texture(uTextureArray, vec3(TexCoord, TexIndex));

    vec3 result = (ambient + diff * 0.6) * texColor.rgb;
    FragColorOut = vec4(result, texColor.a);
}