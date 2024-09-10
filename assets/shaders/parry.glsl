
~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

in LOWP vec4 v_color;
in vec2 v_texCoords;
uniform sampler2D u_texture;

out vec4 outColor;

%uniform u_time
%uniform u_resolution

%constArg ca_color color
%constArg ca_speed float
%constArg ca_movement float
%constArg ca_baseStrength float
%constArg ca_fallOff float
%constArg ca_center vec2
%constArg ca_blackStrength float
%constArg ca_blackFallOff float

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);

    vec2 pos = gl_FragCoord.xy / u_resolution;

    float strength = pow(ca_fallOff, distance(pos, ca_center)) / ca_fallOff;
    strength += ca_baseStrength + abs(sin(u_time * 0.1 * ca_speed)) * ca_movement;
    color = mix(color, ca_color, clamp(strength, 0.0, 1.0));

    strength = pow(ca_blackFallOff, distance(pos, ca_center)) / ca_blackFallOff;
    color = mix(color, vec4(0.0, 0.0, 0.0, 0.0), clamp(strength + ca_blackStrength, 0.0, 1.0));

    outColor = color;
}
