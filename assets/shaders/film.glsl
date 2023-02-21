
~~~section vertex

attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;

void main() {
    v_color = a_color;
    v_color.a = v_color.a * (255.0/254.0);
    v_texCoords = a_texCoord0;
    gl_Position =  u_projTrans * a_position;
}

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

%include shaders/includes/noise_utils.glsl

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

%uniform u_time
%uniform u_resolution

bool floatCompare(float a, float b, float epsilon) {
    return a < b + epsilon && a > b - epsilon;
}

vec4 grayScale(vec4 color) {
    float value = (color.r + color.g + color.b) / 3.0;
    return vec4(value, value, value, 1.0);
}

#define LineEpsilon 0.002
void main() {
    vec2 pos = gl_FragCoord.xy / u_resolution;

    float lineHeight = fbm(vec2(u_time / 28.0), 2, 0.8, 3.0) + 0.4;
    bool isLine = floatCompare(lineHeight, pos.y, LineEpsilon);

    vec4 baseColor = v_color * texture2D(u_texture, v_texCoords);
    baseColor = grayScale(baseColor);

    baseColor += vec4(random(pos + vec2(u_time))) / 10.0;

    float noise = snoise((pos * 10.0) + vec2(u_time * 50.0));
    bool turnBlack = noise > 0.97;

    gl_FragColor = isLine || turnBlack ? vec4(0.0, 0.0, 0.0, 1.0) : baseColor;
}
