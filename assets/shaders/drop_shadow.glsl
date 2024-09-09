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
uniform float u_multiplier;
uniform vec4 u_color;
uniform float u_maxOpacity;

%uniform u_time
%uniform u_resolution

//%include shaders/includes/noise_utils.glsl

//float getGauss(int i, int j){
//    return 1.0;
//    //    return pow(2.7182,-float(i*i+j*j)/4.5); //gauss but already simplified with sigma=1.5
//}

void main() {
    int depthPerDist = 30;
    int maxSum = int(float(depthPerDist)*1.6);

    float distancePerDirection = u_multiplier/(u_multiplier * 2.0 + 1.0);
    float stepDist=distancePerDirection/float(depthPerDist);

    float alpha = 0.0;

    float multi = (1.0 + u_multiplier * 2.0);
    for (int i=-depthPerDist;i<=depthPerDist;i++){
        for (int j=-depthPerDist;j<=depthPerDist;j++){
            vec2 calcPos = (v_texCoords.xy + vec2(float(i) * stepDist, float(j) * stepDist)) * multi  - u_multiplier;
            if (calcPos.x >= 0.0 && calcPos.y >= 0.0 && calcPos.x <= 1.0 && calcPos.y <= 1.0) alpha += texture2D(u_texture, calcPos).a;
        }
    }
    float totalSum = float((depthPerDist * 2) * (depthPerDist * 2));
    float alphaNew = alpha / totalSum * 2.0  * u_maxOpacity;
    if (alphaNew >= 1.0){
        alphaNew = 1.0;
    }
    gl_FragColor = vec4(u_color.xyz, alphaNew);
}


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