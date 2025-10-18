
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

out vec4 outColor;

vec2 saturate(vec2 x) {
    return clamp(x, vec2(0.0), vec2(1.0));
}

vec2 magnify(vec2 uv, vec2 resolution) {
    uv *= resolution;
    return (saturate(fract(uv) / saturate(fwidth(uv))) + floor(uv) - 0.5) / resolution;
}

void main() {
    vec4 texture = texture2D(u_texture, v_texCoords);
//    float gray = abs(color.r + color.g + color.b) / 3.0;
//    gray *= color.a;
//    float diff = fwidth(gray);
//    outColor = vec4(diff, diff, diff, 1.0);
    vec3 color = texture * v_color;
    outColor = vec4(color.r, color.g, color.b, texture.a);
}
