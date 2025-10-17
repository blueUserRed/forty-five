
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
    vec2 resolution = vec2(textureSize(u_texture, 0));
    outColor = texture2D(u_texture, magnify(v_texCoords, resolution)) * v_color;
}
