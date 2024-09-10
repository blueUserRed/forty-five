
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
%uniform u_iceTexture

void main() {
    vec3 ice = texture2D(u_iceTexture, v_texCoords).rgb;

    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = center - v_texCoords;
    float alphaKey = step(0.51, 1.0 - length(toCenter));
    outColor = vec4(ice + vec3(0.0, 0.0, 0.25), 0.5 * alphaKey);
}
