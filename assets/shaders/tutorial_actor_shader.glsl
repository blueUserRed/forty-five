
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

uniform vec2 u_center;
uniform float u_radius;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    float dist = distance(gl_FragCoord.xy, u_center);
    if (dist < u_radius) {
        outColor = vec4(color.rgb, color.a * (dist / u_radius));
    } else {
        outColor = color;
    }
}
