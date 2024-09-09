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

#define SQRT_TWO 1.41421

void main() {

    float squareScale = (1.0 / 1.6);
    float w = squareScale;
    float h = squareScale;
    float xs = (1.0 - w) / 2.0;
    float ys = (1.0 - h) / 2.0;
    float x = v_texCoords.x;
    float y = v_texCoords.y;

    float dx = max(max(xs - x, x - (xs + w)), 0.0);
    float dy = max(max(ys - y, y - (ys + h)), 0.0);
    float dist = sqrt(dx * dx + dy * dy);

    // This is equivalent to pythagoras (sqrt(x * x + y * y)) when x == y and is faster to compute
    float maxDist = SQRT_TWO * xs;

    float value = dist * (1.0 / maxDist);
    value = 1.0 - value;
    value = 0.6 * (value * value);

    gl_FragColor = vec4(245.0 / 255.0, 198.0 / 255.0, 1.0 / 255.0, value);
}
