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

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    float value = (color.r + color.g + color.b) / 3.0;
    gl_FragColor = vec4(value, value, value, color.a);
}
