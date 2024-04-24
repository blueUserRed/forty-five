
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
varying vec4 v_position;
uniform sampler2D u_texture;

%constArg ca_texSize vec2
%constArg ca_cardSize vec2
%constArg ca_timeOffset float

%uniform u_time

float distanceFromGlow(float time, vec2 pos) {
    float dist = abs(length(vec2(time) - pos));
    return dist;
}

vec3 glow(float time, vec2 pos, vec3 origColor) {
    float dist = distanceFromGlow(time, pos);
    float glow = 1.0  / (pow(dist, 2.0));
    vec3 gold = vec3(0.990, 0.909, 0.319);
    vec3 mixed = origColor + (gold * max(1.0 - dist, 0.0));
    return mixed;
}

void main() {
    vec4 baseColor = v_color * texture2D(u_texture, v_texCoords);

    float intervall = 1.0;
    float time = u_time + ca_timeOffset;
    time = mod(time, intervall) / intervall;
    time = time * 4.0 - 2.0;

    vec2 texCoords = vec2(v_texCoords.x * ca_texSize.x, v_texCoords.y * ca_texSize.y);
    vec2 inCard = vec2(mod(texCoords.x, ca_cardSize.x), mod(texCoords.y, ca_cardSize.y));
    vec2 pos = inCard / ca_cardSize;

    float dist = distanceFromGlow(time, pos);

    vec3 color = glow(time, pos, baseColor.rgb);
    gl_FragColor = vec4(color, baseColor.a);
}
