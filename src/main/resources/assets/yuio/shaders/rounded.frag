#version 330 core

in vec2 v_Pos;
out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform RoundedData {
    vec4 u_Rect;    // centerX, centerY, halfWidth, halfHeight  (GUI 像素)
    vec4 u_Radii;   // topLeft, topRight, bottomRight, bottomLeft
    vec4 u_Fill;    // solid fill / gradient top / texture tint
    vec4 u_Fill2;   // gradient bottom, 纹理模式下 .a 是面板不透明度
    vec4 u_Uv;      // u1, v1, u2, v2
    vec4 u_Params;  // strokeWidth, mode, unused, unused
};

// mode: 0 solid, 1 stroke, 2 gradient, 3 textured
const float MODE_STROKE = 1.0;
const float MODE_GRADIENT = 2.0;
const float MODE_TEXTURED = 3.0;

void main() {
    vec2 halfSize = u_Rect.zw;
    vec2 p = v_Pos - u_Rect.xy;

    // 单象限圆角半径: 左上/右上/右下/左下
    float r;
    if (p.x < 0.0) r = (p.y < 0.0) ? u_Radii.x : u_Radii.w;
    else           r = (p.y < 0.0) ? u_Radii.y : u_Radii.z;
    r = min(r, min(halfSize.x, halfSize.y));

    // 圆角矩形 SDF, 负值在内部
    vec2 q = abs(p) - halfSize + r;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;

    float alpha;
    if (u_Params.y == MODE_STROKE) {
        float halfStroke = max(u_Params.x, 1.0) * 0.5;
        alpha = 1.0 - smoothstep(halfStroke - 0.5, halfStroke + 0.5, abs(d));
    } else {
        alpha = 1.0 - smoothstep(-0.5, 0.5, d);
    }

    if (alpha <= 0.0) discard;

    vec3 rgb;
    float a = alpha;
    if (u_Params.y == MODE_TEXTURED) {
        vec2 uv = mix(u_Uv.xy, u_Uv.zw, p / (2.0 * halfSize) + 0.5);
        vec4 tex = texture(u_Texture, uv);
        // u_Fill.a = 色调混合强度, u_Fill2.a = 面板整体不透明度
        rgb = mix(tex.rgb, u_Fill.rgb, u_Fill.a);
        a *= tex.a * u_Fill2.a;
    } else if (u_Params.y == MODE_GRADIENT) {
        float t = clamp((p.y + halfSize.y) / max(2.0 * halfSize.y, 0.0001), 0.0, 1.0);
        rgb = mix(u_Fill.rgb, u_Fill2.rgb, t);
        a *= mix(u_Fill.a, u_Fill2.a, t);
    } else {
        rgb = u_Fill.rgb;
        a *= u_Fill.a;
    }

    fragColor = vec4(rgb, a);
}
