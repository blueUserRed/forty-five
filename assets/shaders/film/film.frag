#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP 
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_time;
uniform vec2 u_resolution;


float random (in vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289(((x * 34.0) +1.0) * x); }

float snoise(vec2 v) {

    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);

    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);

    vec2 i1 = vec2(0.0);
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0): vec2(0.0, 1.0);
    vec2 x1 = x0.xy + C.xx - i1;
    vec2 x2 = x0.xy + C.zz;

    i = mod289(i);
    vec3 p = permute(
    permute(i.y + vec3(0.0, i1.y, 1.0))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(
    dot(x0, x0),
    dot(x1, x1),
    dot(x2,x2)
    ), 0.0);

    m = m* m;
    m = m * m;

    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;

    m *= 1.79284291400159 - 0.85373472095314 * (a0* a0 + h * h);

    vec3 g = vec3(0.0);
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * vec2(x1.x,x2.x) + h.yz * vec2(x1.y, x2.y);
    return 130.0 * dot(m, g);
}


bool floatCompare(float a, float b, float epsilon) {
    return a < b + epsilon && a > b - epsilon;
}

vec4 grayScale(vec4 color) {
    float value = (color.r + color.g + color.b) / 3.0;
    return vec4(value, value, value, 1.0);
}

float fbm(vec2 pos, int octaves, float amplitudeDecrease, float frequencyIncrease) {
    float amplitude = 1.0;
    float frequency = 1.0;
    float value = 0.0;
    for (int i = 0; i < octaves; i++) {
        value += snoise(pos * frequency) * amplitude;
        frequency += frequencyIncrease;
        amplitude -= amplitudeDecrease;
    }
    return value;
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
