
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

uniform vec2 u_center;
uniform float u_radius;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    float dist = distance(gl_FragCoord.xy, u_center);
    if (dist < u_radius) {
        gl_FragColor = vec4(color.rgb, color.a * (dist / u_radius));
    } else {
        gl_FragColor = color;
    }
}
