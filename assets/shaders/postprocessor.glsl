
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

//%uniform u_time

%include shaders/includes/color_utils.glsl

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    vec2 coords = v_texCoords;
    float dist = distance(coords, vec2(0.5));
//    color = mix(color, vec4(0.0, 0.0, 0.0, 1.0), 0.3);
//    outColor = mix(color, vec4(0.0, 0.0, 0.0, 1.0), dist);
    outColor = mix(color, vec4(0.0, 0.0, 0.0, 1.0), dist * dist * dist * 1.3);
}

