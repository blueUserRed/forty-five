attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;
uniform float u_time;

void main() {
    u_time;

    v_color = a_color;
    v_texCoords = a_texCoord0;
    float val = sin(u_time * 4 + a_position.x + a_position.y) / 10.0;
    val *= sin(u_time / 20) * 2;
    gl_Position =  u_projTrans * a_position + vec4(0.0, val, 0.0, 0.0);
}
