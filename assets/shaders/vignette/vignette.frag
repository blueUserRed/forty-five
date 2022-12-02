#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP 
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_time;
uniform vec2 u_resolution;

uniform vec4 u_arg_color;
uniform float u_arg_speed;
uniform float u_arg_movement;
uniform float u_arg_minMovement;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);

    vec2 pos = gl_FragCoord.xy / u_resolution;

    float dist = distance(pos, vec2(0.5));
    float wave = abs(sin(u_time * 0.1 * u_arg_speed)) * u_arg_movement;
    float percent = dist * clamp(wave, u_arg_minMovement, 1);

    color = mix(color, u_arg_color, percent);

    gl_FragColor = color;

}
