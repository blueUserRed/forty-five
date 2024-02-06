
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

vec2 gaussSample(vec2 at, float weight, vec4 middle) {
    vec4 result = texture2D(u_texture, at);
//    return result;
//    return result * weight;
    return (result * result.a + middle * (1.0 - result.a)) * weight;
}

void main() {
    vec4 sum = vec4(0.0);

    vec2 tc = v_texCoords;

    float resolution = u_resolution.x * u_dir.x + u_resolution.y * u_dir.y;
    float blur = u_radius / resolution;

    float hstep = u_dir.x;
    float vstep = u_dir.y;

    vec4 middle = texture2D(u_texture, vec2(tc.x, tc.y));

    sum += gaussSample(vec2(tc.x - 4.0 * blur * hstep, tc.y - 4.0 * blur * vstep), 0.0162162162, middle);
    sum += gaussSample(vec2(tc.x - 3.0 * blur * hstep, tc.y - 3.0 * blur * vstep), 0.0540540541, middle);
    sum += gaussSample(vec2(tc.x - 2.0 * blur * hstep, tc.y - 2.0 * blur * vstep), 0.1216216216, middle);
    sum += gaussSample(vec2(tc.x - 1.0 * blur * hstep, tc.y - 1.0 * blur * vstep), 0.1945945946, middle);
    sum += gaussSample(vec2(tc.x, tc.y), 0.2270270270, middle);
    sum += gaussSample(vec2(tc.x + 1.0 * blur * hstep, tc.y + 1.0 * blur * vstep), 0.1945945946, middle);
    sum += gaussSample(vec2(tc.x + 2.0 * blur * hstep, tc.y + 2.0 * blur * vstep), 0.1216216216, middle);
    sum += gaussSample(vec2(tc.x + 3.0 * blur * hstep, tc.y + 3.0 * blur * vstep), 0.0540540541, middle);
    sum += gaussSample(vec2(tc.x + 4.0 * blur * hstep, tc.y + 4.0 * blur * vstep), 0.0162162162, middle);

//    gl_FragColor = v_color * vec4(sum.rgb, sum.a * 6.0);
//    gl_FragColor = v_color * vec4(sum.rgb, sum.a * 6.0);
    gl_FragColor = v_color * sum;
}
