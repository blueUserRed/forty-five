
~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

in LOWP vec4 v_color;
in vec2 v_texCoords;
in vec4 v_position;
uniform sampler2D u_texture;

uniform float u_progress;

out vec4 outColor;

void main() {
    float progress = u_progress;
    vec2 center = vec2(0.5, 0.5);
    float dist = abs(length(center - v_texCoords));
    float alpha = 1.0 - dist;
    alpha *= 1.0;
    alpha = alpha * alpha * alpha * alpha * alpha * alpha * alpha * alpha * alpha * alpha;
    alpha *= 1.0;
    alpha = min(alpha, 1.0);
    alpha = alpha * (1.0 - progress) + progress;

    vec4 texColor = texture(u_texture, v_texCoords);

    vec3 color = texColor.rgb;
    float invAlpha = 1.0 - alpha;
    color = mix(color, vec3(0.3, 1.0, 0.3), (invAlpha * invAlpha) * 1.0);

    outColor = vec4(color.r, color.g, color.b, alpha * texColor.a);
}

