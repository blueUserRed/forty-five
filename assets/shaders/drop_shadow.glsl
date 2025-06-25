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
uniform sampler2D u_texture;
out vec4 outColor;

uniform vec2 u_resolution;
uniform float u_multiplier;
uniform vec4 u_shadowColor;
uniform float u_originalColor;
uniform float u_maxRadius;
uniform float u_radiusStep;
uniform float u_pointsOnCircle;


//%include shaders/includes/noise_utils.glsl

//float getGauss(int i, int j){
//    return 1.0;
//    //    return pow(2.7182,-float(i*i+j*j)/4.5); //gauss but already simplified with sigma=1.5
//}

vec4 adjustedSample(vec2 at) {
    float multiplier = 1.0 + u_multiplier;
    float invMultiplier = 1.0 / multiplier;
    float offset = (1.0 - invMultiplier) / 2.0;
    float newX = (at.x - offset) * multiplier;
    float newY = (at.y - offset) * multiplier;
    vec2 coords = vec2(newX, newY);
    vec4 color = texture2D(u_texture, coords);
    float mask = step(0.0, newX) * step(newX, 1.0) *
                 step(0.0, newY) * step(newY, 1.0);
    return color * mask;
}

void main() {

    float maxRadius = u_maxRadius; //100.0;
    float radiusStep = u_radiusStep; //1.0;
    float pointsOnCircle = u_pointsOnCircle; //100.0;
    float originalColor = u_originalColor;
    vec4 shadowColor = u_shadowColor;

    vec4 middle = adjustedSample(v_texCoords);

    vec3 colorAcc = middle.rgb;
    float colorContrib = 1.0;

    float alphaAcc = middle.a;
    float alphaContrib = 1.0;

    float cirlceStep = TWO_PI / pointsOnCircle;
    for (float r = radiusStep; r < maxRadius; r += radiusStep) {
        float contrib = 1.0 - (r / (maxRadius + radiusStep));
        for (float a = 0.0; a < TWO_PI; a += cirlceStep) {
            vec2 coords = vec2(
                gl_FragCoord.x + sin(a) * r,
                gl_FragCoord.y + cos(a) * r
            );
            coords /= u_resolution;
            vec4 color = adjustedSample(coords);
            float contribWithAlpha = contrib * color.a;
            colorAcc += color.rgb * contribWithAlpha;
            colorContrib += contribWithAlpha;
            alphaAcc += color.a * contrib;
            alphaContrib += contrib;
        }
    }

    colorAcc /= colorContrib;
    alphaAcc /= alphaContrib;
    vec4 blurred = vec4(colorAcc, alphaAcc);

    vec4 inShadowColor = ((blurred.r + blurred.g + blurred.b) / 3) * shadowColor;
    inShadowColor.a = blurred.a;

    outColor = originalColor * blurred + (1.0 - originalColor) * inShadowColor;
}


//void main() {
//    int depthPerDist = 50;
//    int maxSum = int(float(depthPerDist) * 1.6);
//
//    float distancePerDirection = u_multiplier/(u_multiplier * 2.0 + 1.0);
//    float stepDist = distancePerDirection/float(depthPerDist);
//
//    float alpha = 0.0;
//
//    float multi = (1.0 + u_multiplier * 2.0);
//    vec2 startPos = (v_texCoords.xy + vec2(float(-depthPerDist) * stepDist, float(-depthPerDist) * stepDist)) * multi  - u_multiplier;
//    vec2 endPos = (v_texCoords.xy + vec2(float(depthPerDist) * stepDist, float(depthPerDist) * stepDist)) * multi  - u_multiplier;
//    vec2 transformedStep = (endPos - startPos) / (depthPerDist * 2.0);
//    startPos = max(startPos, vec2(0.0));
//    endPos = min(endPos, vec2(1.0));
//    for (float i = startPos.x; i <= endPos.x; i += transformedStep.x){
//        for (float j = startPos.y; j <= endPos.y; j += transformedStep.y){
//            alpha += texture2D(u_texture, vec2(i, j)).a;
//        }
//    }
//    //    for (int i=-depthPerDist;i<=depthPerDist;i++){ //THIS IS THE OLD VERSION
//    //        for (int j=-depthPerDist;j<=depthPerDist;j++){
//    //            vec2 calcPos = (v_texCoords.xy + vec2(float(i) * stepDist, float(j) * stepDist)) * multi  - u_multiplier;
//    //            if (calcPos.x >= 0.0 && calcPos.y >= 0.0 && calcPos.x <= 1.0 && calcPos.y <= 1.0) alpha += texture2D(u_texture, calcPos).a;
//    //        }
//    //    }
//    float totalSum = float((depthPerDist * 2) * (depthPerDist * 2));
//    float alphaNew = alpha / totalSum * 2.0  * u_maxOpacity;
//    if (alphaNew >= 1.0){
//        alphaNew = 1.0;
//    }
//    outColor = vec4(u_color.xyz, alphaNew);
//}


//--------------------OLD CODE FOR TESTING AND WASN'T SO BAD AS A BACKUP
//    float distX = (v_texCoords.x-0.5)*(v_texCoords.x-0.5);
//    float distY = (v_texCoords.y-0.5)*(v_texCoords.y-0.5);
////    float noise = snoise(v_texCoords*1000);
//    float newAlpha=0.0;
//
//    if (distY<distX) newAlpha=(0.2 - distX);
//    else newAlpha=(0.2 - distY);
//    vec2 original=v_texCoords*1.4-0.2;
//    float originalImageAlpha1 = texture(u_texture, vec2(original.x,v_texCoords.y)).a;
//    float originalImageAlpha2 = texture(u_texture, vec2(v_texCoords.x,original.y)).a;
//    if (originalImageAlpha1>originalImageAlpha2) newAlpha=originalImageAlpha1;
//    else newAlpha=originalImageAlpha2;
////    newAlpha=originalImageAlpha;
//    gl_FragColor = vec4(1.0, 0.0, 0.0, newAlpha);