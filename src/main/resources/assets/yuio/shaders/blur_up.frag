#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform BlurData {
    vec4 u_Data;
};

void main() {
    vec2 o = u_Data.xy * u_Data.z;

    fragColor = (
        texture(u_Texture, uv + vec2(-o.x * 2.0, 0.0)) +
        texture(u_Texture, uv + vec2(-o.x, o.y)) * 2.0 +
        texture(u_Texture, uv + vec2(0.0, o.y * 2.0)) +
        texture(u_Texture, uv + o) * 2.0 +
        texture(u_Texture, uv + vec2(o.x * 2.0, 0.0)) +
        texture(u_Texture, uv + vec2(o.x, -o.y)) * 2.0 +
        texture(u_Texture, uv + vec2(0.0, -o.y * 2.0)) +
        texture(u_Texture, uv - o) * 2.0
    ) / 12.0;
    fragColor.a = 1.0;
}