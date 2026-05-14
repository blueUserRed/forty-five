
~~~section vertex

in vec4 a_position;
in vec4 a_color;
in vec2 a_texCoord0;
uniform mat4 u_projTrans;
out vec4 v_color;
out vec2 v_texCoords;

#define shoot_timeOffset 1500.0
#define shoot_speed 14.0
#define shoot_mag 0.4

#define popout_timeOffset 0.0
#define popout_speed 30.0
#define popout_mag 0.1

#define shake_timeOffset 1500.0
#define shake_speed 25.0
#define shake_mag 0.0028

uniform bool u_doShoot;
uniform float u_shootTime;
uniform bool u_doPopout;
uniform float u_popoutTime;
uniform bool u_doShake;
uniform float u_shakeTime;


vec2 popoutAnimTexCoords() {
    float time = u_popoutTime + popout_timeOffset;
    float value = 1.0 - abs(sin(u_shootTime * popout_speed)) * popout_mag;
    vec2 newTexCoords = a_texCoord0;
    newTexCoords -= 0.5;
    newTexCoords *= value;
    newTexCoords += 0.5;
    return newTexCoords;
}

vec4 shakeAnimOffset() {
    float time = u_shakeTime + shake_timeOffset;
    float value = (sin(u_shakeTime * shake_speed) + 1.0) * shake_mag;
    return vec4(value, -value, 0.0, 0.0);
}

vec4 shootAnimOffset() {
    float time = u_shootTime + shoot_timeOffset;
    float val = sin(u_shootTime * shoot_speed + a_position.x + a_position.y) * shoot_mag;
    val *= sin(time / 20.0) * 2.0;
    return vec4(0.0, val, 0.0, 0.0);
}

void main() {
    v_color = a_color;
    v_texCoords = a_texCoord0;
    if (u_doPopout) v_texCoords = popoutAnimTexCoords();
    vec4 position = u_projTrans * a_position;
    if (u_doShoot) position += shootAnimOffset();
    if (u_doShake) position += shakeAnimOffset();
    gl_Position = position;
}

~~~section fragment

#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

in LOWP vec4 v_color;
in vec2 v_texCoords;
uniform sampler2D u_texture;
out vec4 outColor;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    vec2 coords = v_texCoords;
    float dist = distance(coords, vec2(0.5));
    outColor = mix(color, vec4(0.0, 0.0, 0.0, 1.0), dist * dist * dist * 1.3);
}

