#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec4 uColor;

void main() {
    float a = texture(uTexture, vTexCoord).r;
    fragColor = vec4(uColor.rgb, uColor.a * a);
}
