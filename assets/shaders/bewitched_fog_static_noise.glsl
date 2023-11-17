
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

%uniform u_perlin512x512
%uniform u_time

float noise(vec2 pos) {
    return (
        texture2D(u_perlin512x512, pos + vec2(0.0, 0.0)).r +
        texture2D(u_perlin512x512, pos + vec2(0.0, 0.1)).r +
        texture2D(u_perlin512x512, pos + vec2(0.1, 0.0)).r +
        texture2D(u_perlin512x512, pos + vec2(0.1, 0.1)).r +
        texture2D(u_perlin512x512, pos + vec2(0.0, -0.1)).r +
        texture2D(u_perlin512x512, pos + vec2(-0.1, 0.0)).r +
        texture2D(u_perlin512x512, pos + vec2(-0.1, -0.1)).r
    ) / 7.0;
}

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    vec2 sampleCoords = v_texCoords + vec2(
        sin(v_texCoords.y + u_time * 0.1) * 0.1,
        cos(v_texCoords.x + u_time * 0.1) * 0.1
    );
    float noise = noise(sampleCoords * 0.3);
    noise = noise - texture2D(u_perlin512x512, sampleCoords).r * 0.3;
    noise = noise * (0.2 + (1.0 + sin(u_time * 0.4) * 0.2));
    vec4 fogColor = vec4(0.990, 0.590, 0.910, 1.0);
    gl_FragColor = mix(color, fogColor, max(0.0, noise - 0.4) * 1.3);
}
