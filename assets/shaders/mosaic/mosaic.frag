#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP 
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform vec2 u_resolution;

#define squareSize 0.007

vec4 sampleAt(vec2 pos) {
    return v_color * texture2D(u_texture, pos);
}

void main() {
    vec2 pos = gl_FragCoord.xy / u_resolution;
    vec2 squarePos = vec2(
        pos.x - mod(pos.x, squareSize),
        pos.y - mod(pos.y, squareSize)
    );

    vec4 s1 = sampleAt(squarePos + vec2(0.0, 0.0));
    vec4 s2 = sampleAt(squarePos + vec2(squareSize, 0.0));
    vec4 s3 = sampleAt(squarePos + vec2(0.0, squareSize));
    vec4 s4 = sampleAt(squarePos + vec2(squareSize, squareSize));
    vec4 s5 = sampleAt(squarePos + vec2(squareSize / 2.0, squareSize / 2.0));

    vec4 value = (s1 + s2 + s3 + s4 + s5) / 5.0;

    gl_FragColor = value;
}