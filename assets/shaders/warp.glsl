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


float getNewDist(float dist, float maxDist){
    float oldDist=dist/maxDist;
    float depth=35.0;
    if (u_depth>1.0){
        depth=u_depth;
    }
    float a = pow(2.0, depth);
    a=a/(a-1.0);
    return (a-a/pow(oldDist+1.0, depth));//from 0-1 returns 0-1 but "warped"
}

float hypo(vec2 oldDist){
    return sqrt(oldDist.x*oldDist.x+oldDist.y*oldDist.y);
    //    return abs(oldDist.x)+abs(oldDist.y);
}


vec2 getBorderIntersection(float k, vec2 pointOnLine, bool rightOfCenter){
    //the position where the extended line between the center and the pixel hits the (0|0),(0|1),(1|1),(1|0) square

    float d = pointOnLine.y - pointOnLine.x * k;
    vec2 borderIntersection;
    if (rightOfCenter) borderIntersection= vec2(1.0, k+d);
    else borderIntersection=vec2(0.0, d);

    if (borderIntersection.y>1.0) borderIntersection=vec2((1.0-d) / k, 1.0);
    else if (borderIntersection.y<0.0) borderIntersection=vec2(-d/k, 0.0);
    return borderIntersection;
}


float rotate(float oldSlope, float rotation){
    float oldRotation=atan(oldSlope, 1.0);
    float newRotation=oldRotation+rotation;
    float pi=radians(180.0);
    //    while(newRotation < -pi){
    //        newRotation+=pi;
    //    }
    //    while(newRotation>pi){
    //        newRotation-=pi;
    //    }

    return tan(newRotation);
}

void main() {

        float progress = (sin(abs(float(u_time*0.85)))+1.0)/2.0;
//    float progress = (sin(abs(float(u_time*0.05)))+1.0)/2.0;
    //            float progress = 1.0;
    //    float progress = u_progress;

    vec2 center = vec2(0.5, 0.8);
    //    vec2 center = u_center;

    float PI = radians(float(180));
    float rotation = 2*PI*progress/4;

//        float rotation = PI*1.51;
//        float rotation = 0;

    vec2 tc = v_texCoords;
    vec2 distToCenter= tc-center;

    float k = distToCenter.y/distToCenter.x;

    bool rotationRightOfCenter=tc.x>center.x;

    vec2 borderIntersection= getBorderIntersection(k, tc, rotationRightOfCenter);
    float strechedPercent=getNewDist(hypo(distToCenter), hypo(borderIntersection-center));

    float newRot=rotate(k, rotation);
    if ((newRot<0 && k>0)) {
        rotationRightOfCenter=!rotationRightOfCenter;
    }

    vec2 rotatedBorderToCenter = getBorderIntersection(newRot, tc, rotationRightOfCenter) - center;

        vec2 strechedPos = center+rotatedBorderToCenter*strechedPercent;
//    vec2 strechedPos = center+rotatedBorderToCenter*((hypo(distToCenter)/ hypo(borderIntersection-center)));

    vec2 diffOldNew=strechedPos-tc;
        vec4 result = texture2D(u_texture, tc+diffOldNew*progress);
//    vec4 result = texture2D(u_texture, strechedPos);

    gl_FragColor = result;

    //    if(borderIntersection.x>center.x && borderIntersection.y==1.0){
    //        gl_FragColor = vec4(1.0,0.0,0.0,1.0);
    //    }
    //    if(atan(tc.x*4*PI-2*PI,1.0)>0){
    //        gl_FragColor = vec4(0.0,atan(tc.x*4*PI-2*PI,1.0),0.0,1.0);
    //    }else{
    //        gl_FragColor = vec4(atan(-(tc.x*4*PI-2*PI),1.0),0.0,0.0,1.0);
    //    }
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
