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
uniform float u_multiplier;
uniform vec4 u_color;
uniform vec2 u_offset;

%uniform u_time
%uniform u_resolution

void main() {

    // TODO: change to card hightlight system

    int samplePoints = 10;
    float sampleDist = 0.008;

    float curX = v_texCoords.x - (float(samplePoints) / 2.0) * sampleDist;

    float baseY = v_texCoords.y - (float(samplePoints) / 2.0) * sampleDist;
    float curY = baseY;

    vec4 result = vec4(0.0, 0.0, 0.0, 0.0);
    for (int i = 0; i < samplePoints; i++) {
        for (int j = 0; j < samplePoints; j++) {
            float outside = curX < 0.0 || curX > 1.0 || curY < 0.0 || curY > 1.0 ? 1 : 0;
            vec4 color = texture2D(u_texture, vec2(curX, curY));
            color.a *= 1 - outside;
            result += color / (float(samplePoints) * float(samplePoints));

            curY += sampleDist;
        }
        curY = baseY;
        curX += sampleDist;
    }

    result.r = 1.0;
    result.g = 0.0;
    result.b = 0.0;
    result.a *= 0.8;

    float dist = abs(length(vec2(v_texCoords.x, v_texCoords.y) - vec2(0.5, 0.5))) * 0.8;
    dist *= (1.0 / 0.707107);
    dist = 1.0 - dist;
    dist *= 0.8;

    gl_FragColor = vec4(1.0, 0.0, 0.0, dist * result.a);
}
