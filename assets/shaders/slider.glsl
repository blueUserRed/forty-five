
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
varying vec4 v_position;
uniform sampler2D u_texture;

uniform float u_pos;

%constArg ca_firstColor color
%constArg ca_secondColor color

void main() {
    float p = step(v_texCoords.x, u_pos);
    gl_FragColor = (ca_firstColor * p + ca_secondColor * (1.0 - p)) * texture2D(u_texture, v_texCoords).a;
}
