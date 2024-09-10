/*
Contains the default fragment shader
*/
~~~section export

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

void main() {
    outColor = v_color * texture2D(u_texture, v_texCoords);
}
