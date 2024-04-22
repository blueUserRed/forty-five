
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
    float time = u_time + ca_timeOffset;
    float value = 1.0 - abs(sin(time * ca_speed)) * ca_mag;
    v_color = a_color;
    vec2 newTexCoords = a_texCoord0;
    newTexCoords -= 0.5;
    newTexCoords *= value;
    newTexCoords += 0.5;
    v_texCoords = newTexCoords;
    gl_Position =  u_projTrans * a_position;
}

~~~section fragment

%include shaders/includes/default_fragment.glsl
