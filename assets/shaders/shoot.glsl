
~~~section vertex

attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;

%uniform u_time
%constArg ca_timeOffset float
%constArg ca_speed float
%constArg ca_mag float

void main() {
    v_color = a_color;
    v_texCoords = a_texCoord0;
    float time = u_time + ca_timeOffset;
    float val = sin(time * ca_speed + a_position.x + a_position.y) * ca_mag;
    val *= sin(time / 20.0) * 2.0;
    gl_Position =  u_projTrans * a_position + vec4(0.0, val, 0.0, 0.0);
}

~~~section fragment

%include shaders/includes/default_fragment.glsl
