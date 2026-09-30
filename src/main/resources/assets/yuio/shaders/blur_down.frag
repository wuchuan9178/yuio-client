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
        texture(u_Texture, uv) * 4.0 +
        texture(u_Texture, uv - o) +
        texture(u_Texture, uv + o) +
        texture(u_Texture, uv + vec2(o.x, -o.y)) +
        texture(u_Texture, uv - vec2(o.x, -o.y))
    ) / 8.0;
    fragColor.a = 1.0;
}