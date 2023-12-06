
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

//uniform vec4 u_excludeRegion;

uniform vec2 u_center;
uniform float u_radius;
//uniform float u_width;
//uniform float u_height;

void main() {
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    float cutoff = 0.0;
//    float cutoff = u_radius * 0.8;
    float dist = distance(gl_FragCoord.xy, u_center);
    if (dist < cutoff) {
        gl_FragColor = vec4(1.0, 0.0, 0.0, 0.0);
//        gl_FragColor = vec4(color.rgb, 0.0);
    } else if (dist < u_radius) {
        gl_FragColor = vec4(color.rgb, color.a * (dist / u_radius));
    } else {
        gl_FragColor = color;
    }
}

//void main() {
////    float x = u_excludeRegion.x;
////    float y = u_excludeRegion.y;
////    float width = u_excludeRegion.z;
////    float height = u_excludeRegion.w;
//
//    vec4 color = v_color * texture2D(u_texture, v_texCoords);
//u_x;u_y;u_width;u_height;
//
////    gl_FragColor = color;
////    if (v_texCoords.x > u_x && v_texCoords.x < u_x + u_width) {
////        gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
////    }
////    if (v_texCoords.y > u_y && v_texCoords.y < u_y + u_height) {
////        gl_FragColor = vec4(0.0, 0.0, 1.0, 1.0);
////    }
////    if (v_texCoords.x > u_x && v_texCoords.x < u_x + u_width && v_texCoords.y > u_y && v_texCoords.y < u_y + u_height) {
////        gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
////    }
//
////    if (v_texCoords.x > u_x && v_texCoords.x < u_x + u_width && v_texCoords.y > u_y && v_texCoords.y < u_y + u_height) {
////        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
////    } else {
////        gl_FragColor = color;
////    }
//
//    vec2 coords = gl_FragCoord.xy;
//
//    if (coords.x > u_x && coords.x < u_x + u_width && coords.y > u_y && coords.y < u_y + u_height) {
//        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
//    } else {
//        gl_FragColor = color;
//    }
//}
