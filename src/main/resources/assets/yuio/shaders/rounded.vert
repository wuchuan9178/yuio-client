#version 330 core

layout (location = 0) in vec2 pos;

// MeshRenderer 每帧写进来的投影/模型矩阵, 跟 Meteor 的 UI 管线共用同一套
layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

// 圆角矩形绕着自身中心旋转的角度(弧度), 由 RoundedData.u_Params.z 传入
layout (std140) uniform RoundedData {
    vec4 u_Rect;
    vec4 u_Radii;
    vec4 u_Fill;
    vec4 u_Fill2;
    vec4 u_Uv;
    vec4 u_Params;
};

out vec2 v_Pos;

void main() {
    vec2 p = pos;

    if (u_Params.z != 0.0) {
        vec2 local = pos - u_Rect.xy;
        float c = cos(u_Params.z);
        float s = sin(u_Params.z);
        p = u_Rect.xy + vec2(local.x * c - local.y * s, local.x * s + local.y * c);
    }

    v_Pos = p;
    gl_Position = u_Proj * u_ModelView * vec4(p, 0.0, 1.0);
}
