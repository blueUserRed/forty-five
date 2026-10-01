
~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

in vec4 v_color;
in vec2 v_texCoords;
uniform sampler2D u_texture;

out vec4 outColor;

%uniform u_resolution
%uniform u_time

%uniform u_perlin512x512

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);

//    vec2 pos = gl_FragCoord.xy / u_resolution;

//    float dist = distance(pos, vec2(0.5));
////    float wave = sin(dist * dist * 20 - u_time * 10);
////    wave = abs(wave - 0.3) + 0.3;
////
//    float invDist = 1.0 / dist;
////    float wave2 = sin(invDist * invDist * 0.2 + u_time * 3);
//    float wave2 = sin(invDist * 0.2 + u_time * 3);
//    wave2 = abs(wave2);
//
//    color = mix(color, vec4(0.0, 0.7, 0.0, 1.0), wave2);

//    float progress = abs(sin(u_time * 0.5));
//    progress = progress * progress * progress * progress * progress;
//    progress *= 2;
//    progress -= 1;
//    float dist = v_texCoords.x - progress;
//    dist = max(0.0, dist);
//    dist = min(1.0, dist);

    float value = texture2D(u_perlin512x512, v_texCoords).r;

    color = mix(color, vec4(0.0, 0.7, 0.0, 1.0), value);

    outColor = color;
}
