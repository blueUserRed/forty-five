
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
in vec4 v_position;
uniform sampler2D u_texture;
out vec4 outColor;

%uniform u_time

uniform float u_pos;

%constArg ca_firstColor color
%constArg ca_secondColor color

%include shaders/includes/noise_utils.glsl

void main() {
    float pos = u_pos;
    vec2 timeOffset = vec2(u_time, u_time * 0.3) * 0.2;
    float noise = snoise(vec2(v_texCoords.x, v_texCoords.y * 1.2) * 1.2 + timeOffset);
    noise += snoise(vec2(v_texCoords.x, v_texCoords.y * 0.2) * 10.0 + timeOffset * 4.0) * 0.5;
    noise -= 0.5 + (0.5 * 0.5);
    pos += noise * 0.02;
    float p = step(v_texCoords.x, pos);
    outColor = (ca_firstColor * p + ca_secondColor * (1.0 - p)) * texture2D(u_texture, v_texCoords).a;
//    outColor = vec4(noise, noise, noise, 1.0);
}
