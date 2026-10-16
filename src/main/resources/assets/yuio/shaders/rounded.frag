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

// mode: 0 solid, 1 stroke, 2 gradient, 3 textured, 4 glass
const float MODE_STROKE = 1.0;
const float MODE_GRADIENT = 2.0;
const float MODE_TEXTURED = 3.0;
const float MODE_GLASS = 4.0;

// 玻璃染色: u_Fill.a(染色强度) 最大时, 模糊画面仍保留这个比例的亮度
const float GLASS_MIN_BRIGHTNESS = 0.5;
// 模糊后提饱和, 否则大范围模糊会把画面平均成灰色
const float GLASS_SATURATION = 1.22;

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
    if (u_Params.y == MODE_GLASS) {
        vec2 uv = mix(u_Uv.xy, u_Uv.zw, p / (2.0 * halfSize) + 0.5);

        // 模糊半径(像素)由 MODE_GLASS 复用 stroke 字段(u_Params.x)传进来。
        // 面板在 UV 里占 |u_Uv.zw - u_Uv.xy|, 在帧缓冲里占 2*halfSize 像素, 两者相除就是"1 UV = 多少像素",
        // 所以半径换算不依赖任何外部常量。取绝对值: v 方向可能是反的, 而圆盘采样是对称的。
        vec2 px2uv = abs(u_Uv.zw - u_Uv.xy) / max(2.0 * halfSize, vec2(1.0));
        vec2 blur = px2uv * max(u_Params.x, 0.0);

        // 24 抽样圆盘模糊: golden-angle 螺旋让采样点均匀铺满整个圆盘(而不是只落在几个环上,
        // 环状采样看起来像轻微重影而不是模糊)。每像素再整体旋转一个伪随机角度, 把螺旋的
        // 规则纹理打散成噪点 —— 这正是磨砂玻璃的质感。
        vec2 frag = floor(gl_FragCoord.xy);
        float rot = fract(sin(dot(frag, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 24; i++) {
            float fi = float(i) + 0.5;
            float rr = sqrt(fi * 0.04166667);   // fi / 24
            float ang = fi * 2.39996323 + rot;  // 黄金角
            acc += texture(u_Texture, uv + vec2(cos(ang), sin(ang)) * blur * rr).rgb;
        }
        acc *= 0.04166667;                      // 权重和 = 1

        // u_Fill.a = 染色强度, u_Fill2.a = 面板整体不透明度
        // 关键: 模糊画面占的比例是 keep, 而不是原来 MODE_TEXTURED 的 (1 - tintStrength)。
        // 旧写法把 2/3 的模糊结果直接换成近黑平色, 看起来就是一块半透明玻璃。
        float keep = mix(1.0, GLASS_MIN_BRIGHTNESS, u_Fill.a);
        vec3 tinted = acc * keep + u_Fill.rgb * (1.0 - keep);
        float luma = dot(tinted, vec3(0.2126, 0.7152, 0.0722));
        rgb = clamp(mix(vec3(luma), tinted, GLASS_SATURATION), 0.0, 1.0);
        // 不乘 tex.a: 主帧缓冲的颜色附件 alpha 不可靠(世界只写 rgb 时它一直是 0),
        // 一旦乘进去整块玻璃面板就变成完全透明 —— 也就是"看不到任何模糊"。
        // 面板的不透明度只由 u_Fill2.a(background-alpha) 决定, 形状由 SDF alpha 决定。
        a *= u_Fill2.a;
    } else if (u_Params.y == MODE_TEXTURED) {
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
