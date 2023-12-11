
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

uniform float u_lineLength;

void main() {
    float patternLength = 3.0;
    float patternBlack = 0.6;
    // vec4 color = v_color * texture2D(u_texture, v_texCoords);
    float dist = v_texCoords.y * u_lineLength * 0.05;
    float alpha = mod(dist, patternLength) < patternLength * patternBlack ? 1.0 : 0.0;
    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
}
