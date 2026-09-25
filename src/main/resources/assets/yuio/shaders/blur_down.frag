// 降采样: 5 点箱式滤波。刻意不写 precision lowp —— texel 偏移只有 1e-3 量级,
// lowp 会把它量化成 0, 整条链就退化成原样拷贝。
#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform BlurData {
    vec4 u_Data;    // xy = 半 texel, z = 偏移, w 未用
};

void main() {
    vec2 o = u_Data.xy * u_Data.z;

    fragColor = (
        texture(u_Texture, uv) * 4.0 +
        texture(u_Texture, uv - o) +
        texture(u_Texture, uv + o) +
        texture(u_Texture, uv + vec2(o.x, -o.y)) +
        texture(u_Texture, uv - vec2(o.x, -o.y))
    ) / 8.0;
    fragColor.a = 1.0;
}
