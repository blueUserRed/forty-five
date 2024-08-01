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

%uniform u_time
%uniform u_resolution

//%include shaders/includes/noise_utils.glsl

float getGauss(int i, int j){
    return 1.0;
//    return pow(2.7182,-float(i*i+j*j)/4.5); //gauss but already simplified with sigma=1.5
}

void main() {
    //    resAlpha=v_texCoords.y*1000/u_resolution.y;
    //    vec4 resAlpha=texture(u_texture,v_texCoords*(1+u_multiplier*2)-u_multiplier);
    //    gl_FragColor = resAlpha;

    int depthPerDist=30;
    int maxSum=int(depthPerDist*1.6);

    float distancePerDirection = u_multiplier/(u_multiplier*2.0+1.0);
    float stepDist=distancePerDirection/depthPerDist;

//    float depthSq=(depthPerDist*2.0)*(depthPerDist*2.0)+1.0;
//
    float alpha=0.0;
    float totalSum=0.0;
    for (int i=-depthPerDist;i<=depthPerDist;i++){
        for (int j=-depthPerDist;j<=depthPerDist;j++){
            if(abs(float(i))+abs(float(j))>maxSum) break;
            float curMulti=getGauss(i,j);
            totalSum+=curMulti;
            alpha += texture(u_texture, vec2(v_texCoords.x+i*stepDist,v_texCoords.y+j*stepDist)*(1.0+u_multiplier*2.0)-u_multiplier).a * curMulti;
        }
    }
    float alphaNew=alpha/totalSum*3;
    if (alphaNew>=1.0){
        alphaNew=1.0;
    }
    gl_FragColor = vec4(1.0, 0.0, 0.0, alphaNew*0.8);
//    gl_FragColor = vec4(alphaNew*0.5, 0.0, 0.0, 1.0);

    /*else{
        gl_FragColor = vec4(alpha/totalSum*10, 0.0, 0.0, 1.0);

    }*/

//    if (totalSum<0.2){
//        gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);
//    }

//    vec4 finalVec = vec4(0.0);
//    if (v_texCoords.y<distanceOneDir){
//        finalVec += vec4(1.0, 0.0, 0.0, 1.0);
//    }
//    if (v_texCoords.x<distanceOneDir){
//        finalVec += vec4(0.0, 1.0, 0.0, 1.0);
//    }
//    gl_FragColor=finalVec;
    //    else {
    //        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    //    }
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