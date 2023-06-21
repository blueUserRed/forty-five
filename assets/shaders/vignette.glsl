
~~~section vertex

%include shaders/includes/default_vertex.glsl

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
