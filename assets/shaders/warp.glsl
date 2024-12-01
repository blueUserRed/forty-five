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
uniform float u_rotation;// rotation in radians

// "streches" a value, fe. oldDistance  0.1=>0.23;  0.9=>0.96   (examplevalues with depth=2)
float getNewDist(float oldDist, float depthMulti){
    float depth=15.0*depthMulti;
    if (u_depth>1.0){
        depth=u_depth*depthMulti;
    }
    float a = pow(2.0, depth);
    a=a/(a-1.0);
    return (a-a/pow(oldDist+1.0, depth));//from 0-1 returns 0-1 but "warped"
}

float hypo(vec2 oldDist){
    return sqrt(oldDist.x*oldDist.x+oldDist.y*oldDist.y);
    //    return abs(oldDist.x)+abs(oldDist.y);
}

//the position where the extended line between the center and the pixel hits the (0|0),(0|1),(1|1),(1|0) square
vec2 getBorderIntersection(float k, vec2 pointOnLine, bool rightOfCenter){

    float d = pointOnLine.y - pointOnLine.x * k;
    vec2 borderIntersection;
    if (rightOfCenter) borderIntersection= vec2(1.0, k+d);
    else borderIntersection=vec2(0.0, d);

    if (borderIntersection.y>1.0) borderIntersection=vec2((1.0-d) / k, 1.0);
    else if (borderIntersection.y<0.0) borderIntersection=vec2(-d/k, 0.0);
    return borderIntersection;
}

// rotates the slope, and the isRightOfCenter says if the direction is (oldSlope,1) if true or (-oldslope,-1) if false
vec2 rotate(float oldSlope, float rotation, bool isRightOfCenter){
    float PI=radians(180.0);
    float oldRotation=atan(oldSlope, 1.0);
    if(!isRightOfCenter) oldRotation+=PI;
    float newRotation=oldRotation+rotation;

    if(cos(newRotation)>0.0) return vec2(tan(newRotation),1.0);
    else return vec2(tan(newRotation),0.0);
}

// This programm takes the following steps:
// 1. calculate the line between the current position and the center
// 2. get the point on the line where it hits the min/max box (Rectangle with coordiantes: (0|0),(0|1),(1|1),(1|0))
// 3. calculate the distance from in percent between the center and that point for my position
// 4. strech this position, so that the closer you are to the center, the stronger it streches and goes away from it
// 5. then it rotates the line from 1
// 6. repeat step 2 with the new line
// 7. takes the percentages from point 3 and puts them on the line from point 6

void main() {

//    float progress = (sin(abs(float(u_time*0.85)))+1.0)/2.0;
    float progress = u_progress;

    vec2 center = u_center;
    if(u_center.x==0.0 && u_center.y==0.0) center=vec2(0.5, 0.8);

//    float PI = radians(180.0);
//    float rotation = getNewDist(progress, -0.1) * PI/2.0*16.0;
    float rotation = getNewDist(progress, -0.1) * u_rotation;


    vec2 tc = v_texCoords;
    vec2 distToCenter= tc-center;

    // 1.
    float k = distToCenter.y/distToCenter.x; // slope of line from

    bool pointRightOfCenter=tc.x>center.x;

    // 2.
    vec2 borderIntersection= getBorderIntersection(k, center, pointRightOfCenter);


    // 3.
    float maxDist=hypo(borderIntersection-center);


    float maxDistToFurthestCorner=max(max(hypo(center),hypo(center-vec2(0.0,1.0))), max(hypo(center-vec2(1.0)),hypo(center-vec2(1.0,0.0))));
    float outSideMultiplier=maxDist/maxDistToFurthestCorner; //this makes it from a rectangle to a ellipse
    float oldPercent=hypo(distToCenter)/maxDist;

    // 4.
    float strechedPercent=getNewDist(oldPercent, outSideMultiplier);


    //5.
    vec2 newRot = rotate(k, rotation*(1.0-strechedPercent), pointRightOfCenter); //newRot[0]= new slope, newRot[1] == 1 if new point is right of center


    //6
    vec2 rotatedBorderToCenter = getBorderIntersection(newRot.x, center, newRot.y==1.0) - center;


    //7
    vec2 rotatedPos = center+rotatedBorderToCenter*oldPercent;
    vec4 result = texture2D(u_texture, rotatedPos + (strechedPercent-oldPercent) * rotatedBorderToCenter * progress);

    gl_FragColor = result;
}