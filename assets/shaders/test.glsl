
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

%uniform u_time
%uniform u_resolution

%include shaders/includes/noise_utils.glsl

float fbm2D(vec2 coords) {
    float amplitude = 1.0;
    float frequency = 1.0;
    float value = 0.0;
    float ampAcc = 0.0;
    for (int i = 0; i < 5; i++) {
        value += snoise(coords * frequency) * amplitude;
        ampAcc += amplitude;
        frequency *= 2.4;
        amplitude /= 2.4;
    }
    return value / ampAcc;
}

void main() {
    vec4 color = texture2D(u_texture, v_texCoords);
    float sampleX = v_texCoords.x * 3.0
        + sin(u_time * 0.8 + v_texCoords.y * 20.0 + v_texCoords.x * 1.3) * 0.05
        + sin(u_time * 4.0 + v_texCoords.y * 10.0 + v_texCoords.x * 1.4 + 2383.141) * 0.03
        + sin(u_time * 1.4 + v_texCoords.y * 40.0 + v_texCoords.x * 1.6) * 0.01;
    float noise = fbm2D(vec2(sampleX, u_time * 0.25) * 0.4) * 0.8;
    noise += abs(sin(v_texCoords.x * 4.0 + u_time)) * 0.4;
    noise /= 1.2;
    float cutOff = noise * u_resolution.y;
    vec3 addition = vec3(0.0, 0.0, 0.0);
    addition += vec3(0.8, 0.0, 0.0) * step(v_texCoords.y, noise);
    addition += vec3(0.0, 0.6, 0.0) * step(v_texCoords.y, noise - 0.1);
    addition -= vec3(0.0, 0.3, 0.0) * step(v_texCoords.y, noise - 0.2);
    addition -= vec3(0.1, 0.2, 0.0) * step(v_texCoords.y, noise - 0.4);
    gl_FragColor = vec4(color.rgb + addition, 1.0);
}
