#version 330 core
in vec2 TexCoord;
in vec3 Normal;
in float TexIndex;
in vec2 LightLevels; // x: небесный свет, y: свет ламп (0..1)
in vec3 ViewPos;
in vec3 NormalView;
in vec3 LightDirView;
in vec3 WorldPos;

out vec4 FragColorOut;

uniform sampler2DArray uTextureArray;
uniform vec3 lightDir;     // направление на солнце (меняется за день)
uniform float uSunLevel;   // яркость солнца: 1 день, ~0.12 ночь

// Туман
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

// Размер вьюпорта в пикселях (для лёгкой виньетки); (0,0) — выключена
uniform vec2 uViewport;

void main() {
    vec4 texColor = texture(uTextureArray, vec3(TexCoord, TexIndex));

    // Прозрачные пиксели текстуры (окошко двери) не пишем — сквозь них видно фон
    if (texColor.a < 0.45) discard;

    float sky = LightLevels.x * uSunLevel;
    float blk = LightLevels.y;
    float day = uSunLevel;

    // Днём небо тёплое; ночью — нежный лунный, без синей заливки
    vec3 skyTint = mix(vec3(0.78, 0.84, 1.0), vec3(1.0, 0.98, 0.90), day);
    // Лампа — всегда тёплый янтарь
    vec3 lampTint = vec3(1.0, 0.72, 0.40);

    vec3 N = normalize(Normal);
    vec3 L = normalize(lightDir);

    // Мягкое освещение: свет везде, а не только с солнечной стороны
    float ndl = clamp(dot(N, L), 0.0, 1.0);
    float diffuse = 0.62 + 0.38 * ndl;
    // Верх подбивается небом чуть сильнее
    float upBounce = 0.86 + 0.14 * max(N.y, 0.0);

    vec3 lightSum = skyTint * (0.28 + 0.72 * sky) * diffuse * upBounce
                  + lampTint * (0.05 + 1.25 * blk);

    // Едва заметный тонмаппинг, чтобы лампы не выгорали в белое
    lightSum = lightSum / (1.0 + 0.08 * lightSum);

    vec3 result = texColor.rgb * (0.07 + lightSum);

    // --- Тёплый солненый блик (как раньше): мягкий глянец на солнечной стороне ---
    vec3 V = normalize(-ViewPos);
    vec3 Lv = normalize(LightDirView);
    vec3 H = normalize(Lv + V);
    float spec = pow(clamp(dot(normalize(NormalView), H), 0.0, 1.0), 64.0);
    result += vec3(1.0, 0.92, 0.78) * spec * (0.30 + 0.55 * day) * sky;
    // Слабое отражение неба у воды и травы (лёгкий холодный оттенок только на блике)
    result += vec3(0.65, 0.76, 1.0) * spec * spec * blk * 0.10;

    // --- Очень мягкая ночная десатурация ---
    float n = 1.0 - day;
    float gray = dot(result, vec3(0.299, 0.587, 0.114));
    result = mix(result, vec3(gray), n * 0.18);

    // --- Туман: плавный, цвет неба; лёгкое тепло у солнца ---
    float dist = length(ViewPos);
    float fog = clamp((dist - uFogStart) / max(uFogEnd - uFogStart, 0.001), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog); // smoothstep
    float sunGlow = pow(max(dot(normalize(-ViewPos), Lv), 0.0), 10.0);
    vec3 fogColor = mix(uFogColor, vec3(1.0, 0.90, 0.72), sunGlow * 0.30 * day);
    result = mix(result, fogColor, fog);

    // --- Легчайшая виньетка и мягкий финальный контраст ---
    if (uViewport.x > 1.0 && uViewport.y > 1.0) {
        vec2 ndc = (gl_FragCoord.xy / uViewport) * 2.0 - 1.0;
        result *= 1.0 - 0.045 * dot(ndc, ndc);
    }
    result = max(result, 0.0);
    result = clamp(0.40 + 1.02 * (result - 0.40), 0.0, 1.0);

    FragColorOut = vec4(result, texColor.a);
}