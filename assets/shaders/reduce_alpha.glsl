
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

uniform float u_alphaReduction;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
//    color.a = max(color.a - u_alphaReduction, 0.0);
//    color.a -= 2000.0;
//    color.a = 0.0;
    color.a -= u_alphaReduction;
    gl_FragColor = color;
}
