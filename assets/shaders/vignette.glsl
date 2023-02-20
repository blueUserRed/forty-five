
~~~section vertex

attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;

void main() {
    v_color = a_color;
    v_color.a = v_color.a * (255.0/254.0);
    v_texCoords = a_texCoord0;
    gl_Position =  u_projTrans * a_position;
}

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

%uniform u_time
%uniform u_resolution

%constArg ca_color color
%constArg ca_speed float
%constArg ca_movement float
%constArg ca_minMovement float

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);

    vec2 pos = gl_FragCoord.xy / u_resolution;

    float dist = distance(pos, vec2(0.5));
    float wave = abs(sin(u_time * 0.1 * ca_speed)) * ca_movement;
    float percent = dist * clamp(wave, ca_minMovement, 1.0);

    color = mix(color, ca_color, percent);

    gl_FragColor = color;
}
