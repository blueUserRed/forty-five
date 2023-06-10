
~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

%uniform u_time
%uniform u_resolution


float distanceFromGlow(float time) {
    vec2 pos = gl_FragCoord.xy / u_resolution.xy;
    float dist = abs(length(vec2(time) - pos));
    return dist;
}

vec4 glow(float time, vec3 origColor) {
    float dist = distanceFromGlow(time);
    float glow = 1.0  / (pow(dist, 2.0));
    vec3 gold = vec3(0.990, 0.849, 0.239);
    vec3 mixed = mix(origColor, gold, 1.0 - dist);
    return vec4(mixed, 1.0);
}

void main() {

    vec4 baseColor = v_color * texture2D(u_texture, v_texCoords);

    float intervall = 10.0;
    float time = u_time;
    time = mod(time, intervall) / intervall;
    time = time * 4.0 - 2.0;

    gl_FragColor = glow(time, baseColor.rgb);
}
