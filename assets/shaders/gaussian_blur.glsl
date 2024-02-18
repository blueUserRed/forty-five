
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

%uniform u_resolution

uniform float u_radius;
uniform vec2 u_dir;

void main() {
    vec2 tc = v_texCoords;
    float resolution = u_resolution.x * u_dir.x + u_resolution.y * u_dir.y;
    float blur = u_radius / resolution;
    float hstep = u_dir.x;
    float vstep = u_dir.y;

    const int numSamples = 9;

    vec2 samples[numSamples];

    samples[0] = vec2(tc.x - 4.0 * blur * hstep, tc.y - 4.0 * blur * vstep);
    samples[1] = vec2(tc.x - 3.0 * blur * hstep, tc.y - 3.0 * blur * vstep);
    samples[2] = vec2(tc.x - 2.0 * blur * hstep, tc.y - 2.0 * blur * vstep);
    samples[3] = vec2(tc.x - 1.0 * blur * hstep, tc.y - 1.0 * blur * vstep);
    samples[4] = vec2(tc.x, tc.y);
    samples[5] = vec2(tc.x + 1.0 * blur * hstep, tc.y + 1.0 * blur * vstep);
    samples[6] = vec2(tc.x + 2.0 * blur * hstep, tc.y + 2.0 * blur * vstep);
    samples[7] = vec2(tc.x + 3.0 * blur * hstep, tc.y + 3.0 * blur * vstep);
    samples[8] = vec2(tc.x + 4.0 * blur * hstep, tc.y + 4.0 * blur * vstep);

    float weights[numSamples];

    weights[0] = 0.0162162162;
    weights[1] = 0.0540540541;
    weights[2] = 0.1216216216;
    weights[3] = 0.1945945946;
    weights[4] = 0.2270270270;
    weights[5] = 0.1945945946;
    weights[6] = 0.1216216216;
    weights[7] = 0.0540540541;
    weights[8] = 0.0162162162;

    vec4 sum = vec4(0.0);
    float weightAcc = 0.0;
    for (int i = 0; i < numSamples; i++) {
        vec4 result = texture2D(u_texture, samples[i]);
        float weight = weights[i];
        float weightWithAlpha = weight * result.a;
        sum += vec4(result.rgb * weightWithAlpha, result.a * weight);
        weightAcc += weightWithAlpha;
    }

    sum.rgb /= weightAcc;

    vec4 result = v_color * sum;
    gl_FragColor = result;
}
