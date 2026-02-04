
~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

uniform vec4 u_color;

// ignore v_color and use u_color instead, because libgdx sets v_color to strange values when rendering fonts
in LOWP vec4 v_color;
in vec2 v_texCoords;
uniform sampler2D u_texture;
out vec4 outColor;

void main() {
    vec4 texture = texture2D(u_texture, v_texCoords);
    outColor = vec4(u_color.rgb, texture.a * u_color.a);
}

