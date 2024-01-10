
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
%uniform u_iceTexture

%include shaders/includes/noise_utils.glsl

void main() {
//    vec4 origColor = v_color * texture2D(u_texture, v_texCoords);
    vec3 ice = texture2D(u_iceTexture, v_texCoords).rgb;

//    vec3 color = mix(origColor.rgb, iceBlend, 0.8);
    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = center - v_texCoords;
    float alphaKey = step(0.51, 1.0 - length(toCenter));
    float noise = 1.0 - snoise(v_texCoords * 3.0 + vec2(u_time * 0.1));
    noise = mix(noise, 1.0 - snoise(v_texCoords * 10.0 - vec2(u_time * 0.1)), 0.3);
    gl_FragColor = vec4(ice + vec3(0.0, 0.0, 0.25), (0.6 + noise) * 0.35 * alphaKey);
}
