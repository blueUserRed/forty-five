
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

uniform float u_pos;

%constArg ca_firstColor color
%constArg ca_secondColor color

void main() {
    float p = step(v_texCoords.x, u_pos);
    outColor = (ca_firstColor * p + ca_secondColor * (1.0 - p)) * texture2D(u_texture, v_texCoords).a;
}
