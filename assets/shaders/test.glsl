
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

%include shaders/includes/color_utils.glsl

//void main() {
//    vec4 baseColor = v_color * texture2D(u_texture, v_texCoords);
//    vec3 color = baseColor.rgb;
//    vec3 hsl = rgb2hsl(color);
//
//    float grayScale = baseColor.r + baseColor.g + baseColor.b;
//    grayScale /= 3;
//    float diff = fwidth(grayScale);
//    hsl.b -= diff;
//
//    vec3 rgb = hsl2rgb(hsl);
//    if (v_texCoords.x > 0.5) {
//        outColor = vec4(rgb, baseColor.a);
//    } else {
//        outColor = baseColor;
//    }
//    outColor = vec4(rgb, baseColor.a);
}
