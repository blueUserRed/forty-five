
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

%uniform u_time
%uniform u_perlin512x512

out vec4 outColor;

%constArg ca_duration float
%constArg ca_border float
%constArg ca_borderColor color
%constArg ca_noiseTexScale float

void main() {
    float duration = 1.5;
    float border = 0.2;

    float progress = u_time / ca_duration;
    float noise = texture2D(u_perlin512x512, v_texCoords * ca_noiseTexScale).r;
    vec4 baseColor = texture2D(u_texture, v_texCoords);


    if (noise < progress) {
        outColor = vec4(baseColor.rgb, 0.0);
    } else if (noise < progress + ca_border) {
        outColor = vec4(ca_borderColor.rgb, baseColor.a);
    } else {
        outColor = baseColor;
    }
}
