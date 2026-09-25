#version 330 core

layout (location = 0) in vec2 pos;

// MeshRenderer 每帧写进来的投影/模型矩阵, 跟 Meteor 的 UI 管线共用同一套
layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_Pos;

void main() {
    v_Pos = pos;
    gl_Position = u_Proj * u_ModelView * vec4(pos, 0.0, 1.0);
}
