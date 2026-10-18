#version 330 core

in vec2 v_Pos;
out vec4 fragColor;

uniform sampler2D u_Texture;

layout (std140) uniform RoundedData {
    vec4 u_Rect;    // centerX, centerY, halfWidth, halfHeight  (当前绘制空间单位)
    vec4 u_Radii;   // topLeft, topRight, bottomRight, bottomLeft
    vec4 u_Fill;    // solid fill / gradient top / 染色
    vec4 u_Fill2;   // gradient bottom, 纹理/玻璃模式下 .a 是面板不透明度
    vec4 u_Uv;      // u1, v1, u2, v2
    vec4 u_Params;  // strokeWidth 或模糊半径(texel), mode, 旋转(rad) 或 texelU, 0 或 texelV
};

// mode: 0 solid, 1 stroke, 2 gradient, 3 textured, 4 glass, 5/6 模糊通道(水平/垂直)
const float MODE_STROKE = 1.0;
const float MODE_GRADIENT = 2.0;
const float MODE_TEXTURED = 3.0;
const float MODE_GLASS = 4.0;
const float MODE_BLUR_H = 5.0;
const float MODE_BLUR_V = 6.0;

// 玻璃染色: u_Fill.a(染色强度) 最大时, 模糊画面仍保留这个比例的亮度
const float GLASS_MIN_BRIGHTNESS = 0.55;
// 模糊后提饱和, 否则大范围模糊会把画面平均成灰色
const float GLASS_SATURATION = 1.15;

const int BLUR_TAPS = 16;

void main() {
    vec2 halfSize = max(u_Rect.zw, vec2(1.0e-4));
    vec2 p = v_Pos - u_Rect.xy;
    vec2 uv = mix(u_Uv.xy, u_Uv.zw, p / (2.0 * halfSize) + 0.5);

    // 离屏模糊通道: 整屏覆盖, 不做 SDF 裁边(边缘像素会被 SDF 判成外部而丢样点)
    if (u_Params.y == MODE_BLUR_H || u_Params.y == MODE_BLUR_V) {
        vec2 dir = (u_Params.y == MODE_BLUR_H ? vec2(u_Params.z, 0.0) : vec2(0.0, u_Params.w)) * u_Params.x;

        // 可分离高斯, 16 抽头覆盖 [-r, +r]。多次小半径迭代比一次大半径更平滑, 所以半径由 CPU 侧拆分。
        vec3 acc = vec3(0.0);
        float weightSum = 0.0;
        for (int i = 0; i < BLUR_TAPS; i++) {
            float t = (float(i) + 0.5) / float(BLUR_TAPS) * 2.0 - 1.0;
            float wgt = exp(-2.2 * t * t);
            acc += texture(u_Texture, uv + dir * t).rgb * wgt;
            weightSum += wgt;
        }

        fragColor = vec4(acc / weightSum, 1.0);
        return;
    }

    // 单象限圆角半径: 左上/右上/右下/左下
    float r;
    if (p.x < 0.0) r = (p.y < 0.0) ? u_Radii.x : u_Radii.w;
    else           r = (p.y < 0.0) ? u_Radii.y : u_Radii.z;
    r = min(r, min(halfSize.x, halfSize.y));

    // 圆角矩形 SDF, 负值在内部
    vec2 q = abs(p) - halfSize + r;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;

    // 抗锯齿宽度取屏幕空间导数: 恰好 1 帧缓冲像素, 与当前坐标系无关。
    // 写死 0.5(旧写法)时, 一单位=2 像素的 GUI 空间里过渡只有半个像素, 圆角就会出锯齿。
    float aa = max(fwidth(d), 1.0e-4);
    float edge = aa * 0.5;

    float alpha;
    if (u_Params.y == MODE_STROKE) {
        float halfStroke = max(u_Params.x, 1.0) * 0.5;
        alpha = 1.0 - smoothstep(halfStroke - edge, halfStroke + edge, abs(d));
    } else {
        alpha = 1.0 - smoothstep(-edge, edge, d);
    }

    if (alpha <= 0.0) discard;

    vec3 rgb;
    float a = alpha;
    if (u_Params.y == MODE_GLASS) {
        // 背景在抓屏通道里已经做过多遍可分离高斯(见 BlurRenderer), 这里只取一次样。
        // 旧实现在这里做 24 抽头圆盘采样, 采样密度远不够, 看起来只是一层磨砂雾而不是真模糊。
        vec3 acc = texture(u_Texture, uv).rgb;

        // u_Fill.a = 染色强度, u_Fill2.a = 面板整体不透明度
        float keep = mix(1.0, GLASS_MIN_BRIGHTNESS, u_Fill.a);
        vec3 tinted = acc * keep + u_Fill.rgb * (1.0 - keep);
        float luma = dot(tinted, vec3(0.2126, 0.7152, 0.0722));
        rgb = clamp(mix(vec3(luma), tinted, GLASS_SATURATION), 0.0, 1.0);
        // 不乘 tex.a: 主帧缓冲的颜色附件 alpha 不可靠(世界只写 rgb 时它一直是 0)。
        a *= u_Fill2.a;
    } else if (u_Params.y == MODE_TEXTURED) {
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
