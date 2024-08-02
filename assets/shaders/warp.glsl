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
uniform float u_progress;// between 0-1
uniform vec2 u_center;// between 0-1 for x and y
uniform float u_depth;// recommended beween 3 and 30, how strong it zooms


float getNewDist(float oldDist){
    float depth=5.0;
    if (u_depth>1.0){
        depth=u_depth;
    }
    float a = pow(2.0, depth);
    a=a/(a-1.0);
    return a-a/pow(oldDist+1.0, depth);//from 0-1 returns 0-1 but "warped"
}

float hypo(vec2 oldDist){
    return sqrt(oldDist.x*oldDist.x+oldDist.y*oldDist.y);
    //    return abs(oldDist.x)+abs(oldDist.y);
}

void main() {

    //    float progress = (sin(abs(float(u_time*0.85)))+1.0)/2.0;
    //        float progress = 1.0;
    float progress = u_progress;

    //    vec2 center = vec2(0.5, 0.8);
    vec2 center = u_center;

    vec2 tc = v_texCoords;
    vec2 distToCenter= tc-center;

    float k = distToCenter.y/distToCenter.x;
    float d = tc.y - tc.x * k;

    //the position where the extended line between the center and the pixel hits the 00-01-11-10 square
    vec2 borderIntersection;
    if (tc.x > center.x) borderIntersection= vec2(1.0, k+d);
    else borderIntersection=vec2(0.0, d);

    if (borderIntersection.y>1.0) borderIntersection=vec2((1.0-d) / k, 1.0);
    else if (borderIntersection.y<0.0) borderIntersection=vec2(-d/k, 0.0);

    vec2 borderToCenter =borderIntersection-center;
    float newPercent=getNewDist(hypo(distToCenter)/hypo(borderToCenter));
    vec2 strechedPos = center+borderToCenter*newPercent;

    vec2 diffOldNew=strechedPos-tc;
    vec4 result = texture2D(u_texture, tc+diffOldNew*progress);

    gl_FragColor = result;
}

//  //bad warp, as a backup or future refernce
//
//float getNewDist(float oldDist){
//    float e=3.0;//steepness of the curve, can be changed
//    float a = pow(2, e);
//    a=a/(a-1.0);
//    return a-a/pow(oldDist+1.0, e);//from 0-1 returns 0-1 but "warped"
//}
//
//void main() {
//
//    float progress = abs(sin(abs(float(u_time*0.85))));
//    vec2 center = vec2(0.5, 0.2);
//
//    vec2 tc = v_texCoords;
//    vec2 distToCenter= tc-center;
//
//    vec2 totalDistanceInThatDirection=vec2(0.0);
//    if (distToCenter.x<0) totalDistanceInThatDirection.x=-center.x;
//    else totalDistanceInThatDirection.x=1-center.x;
//
//    if (distToCenter.y<0) totalDistanceInThatDirection.y=-center.y;
//    else totalDistanceInThatDirection.y=1-center.y;
//
//    vec2 percent=distToCenter/totalDistanceInThatDirection;
//
//    vec2 newPercent=vec2(getNewDist(percent.x),getNewDist(percent.y));
//
//    vec2 strechedPos=center+newPercent*totalDistanceInThatDirection;
//
//    vec2 diffOldNew=strechedPos-tc;
//
//
//    vec4 result = texture2D(u_texture, tc+diffOldNew*progress);
//
//    if (sqrt((tc.x-center.x)*(tc.x-center.x)+(tc.y-center.y)*(tc.y-center.y))<0.0018){
//        result=vec4(1.0,0.0,0.0,1.0);
//    }
////    result.a=progress;
//    gl_FragColor = result;
//    //    gl_FragColor = vec4(0.0);
//}
