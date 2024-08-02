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
uniform float u_progress;
uniform vec2 u_center;


float getNewDist(float oldDist){
    float e=3.0;//steepness of the curve, can be changed
    float a = pow(2, e);
    a=a/(a-1.0);
    return a-a/pow(oldDist+1.0, e);//from 0-1 returns 0-1 but "warped"
}

void main() {

    float progress = abs(sin(abs(float(u_time*0.85))));//TODO change back to u_progress
    vec2 center = vec2(0.5, 0.2);//TODO change back to u_center

    vec2 tc = v_texCoords;
    vec2 distToCenter= tc-center;

    vec2 totalDistanceInThatDirection=vec2(0.0);
    if (distToCenter.x<0) totalDistanceInThatDirection.x=-center.x;
    else totalDistanceInThatDirection.x=1-center.x;

    if (distToCenter.y<0) totalDistanceInThatDirection.y=-center.y;
    else totalDistanceInThatDirection.y=1-center.y;

    vec2 percent=distToCenter/totalDistanceInThatDirection;

    vec2 newPercent=vec2(getNewDist(percent.x),getNewDist(percent.y));

    vec2 strechedPos=center+newPercent*totalDistanceInThatDirection;

    vec2 diffOldNew=strechedPos-tc;


    vec4 result = texture2D(u_texture, tc+diffOldNew*progress);

    if (sqrt((tc.x-center.x)*(tc.x-center.x)+(tc.y-center.y)*(tc.y-center.y))<0.0018){
        result=vec4(1.0,0.0,0.0,1.0);
    }
//    result.a=progress;
    gl_FragColor = result;
    //    gl_FragColor = vec4(0.0);
}
