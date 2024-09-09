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
uniform float u_blurDepth;
uniform vec4 u_color;
uniform float u_maxOpacity;

%uniform u_time
%uniform u_resolution

//%include shaders/includes/noise_utils.glsl
//#define M_E 2.7182818284590452353602874713527

float getGauss(int i, float sigma){
    return exp(-(float(i) * float(i) / (2 * sigma * sigma)));
}

float getSigma(int depth){
    return float(depth) / sqrt(log(float(depth)));//derived from (ℯ^(-((depth^(2))/(sigma^(2)*2))))^2 = 1/depth
}

void main() {
    //    resAlpha=v_texCoords.y*1000/u_resolution.y;ff
    //    vec4 resAlpha=texture(u_texture,v_texCoords*(1+u_multiplier*2)-u_multiplier);
    //    gl_FragColor = resAlpha;


    const int arraySize = 15;
    int depthPerDist = arraySize-1;
    float weights[arraySize];//replace the next 3 lines and put the values into weights with the preprocessor
    float sigma = getSigma(depthPerDist);
    for (int i = 0; i < depthPerDist; i++){
        weights[i] = getGauss(i, sigma);
    }


//    float distForCalc = u_blurDepth * 0.8;
    float start = u_blurDepth;
    float multi = 1.0 - (start * 2.0);


    float step = u_blurDepth / float(depthPerDist);
    float alpha = 0.0;
    float totalSum = 0.0; //this can be easily calculated at the start of everything
    int counter = 0;
//    for (int i = -depthPerDist, c = depthPerDist; i <= depthPerDist; i++){
//        for (int j = -depthPerDist, d = depthPerDist; j <= depthPerDist; j++){
            counter++;
    //            float curMulti = weights[c] * weights[d];
            float curMulti = weights[0] * weights[0];
            totalSum += curMulti;
//            vec2 curPos=vec2(float(i) * step, float(j) * step);
            vec2 curPos=vec2(0.0, 0.0);
            vec2 calcPos=v_texCoords.xy / multi - start;
            if(calcPos.x<=0 || calcPos.y<=0 || calcPos.x>1.0 || calcPos.y>1.0) alpha+=0;
            else alpha += texture2D(u_texture, calcPos).a * curMulti;
//            if (j < 0) d--;
//            else d++;
//        }
//        if (i < 0) c--;
//        else c++;
//    }

    float alphaNew = alpha / totalSum /** u_maxOpacity*/;
//    gl_FragColor = vec4(u_color.xyz, alphaNew);
    //    gl_FragColor = vec4(u_color.xyz,  texture2D(u_texture,v_texCoords).a);
    //    gl_FragColor = vec4(u_color.xyz, 1.0);

    if (alphaNew >= 1.0){
        gl_FragColor = vec4(u_color.xyz, 0.1);
    }else if (alphaNew >= 0.6){
        gl_FragColor = vec4(1.0,0.0,0.0, 1.0);
    }else if (alphaNew >= 0.2){
        gl_FragColor = vec4(1.0,1.0,0.0, 1.0);
    }else {
        gl_FragColor = vec4(1.0,0.0,1.0, 1.0);
    }

    //    float alpha=0.0;
    //    float totalSum=0.0;
    //    for (int i=-depthPerDist;i<=depthPerDist;i++){
    //        for (int j=-depthPerDist;j<=depthPerDist;j++){
    //            if(abs(float(i))+abs(float(j))>float(maxSum)) break;
    //            float curMulti=getGauss(float(i),float(j));
    //            totalSum+=curMulti;
    //            alpha += texture2D(u_texture, vec2(v_texCoords.x+float(i)*stepDist,v_texCoords.y+float(j)*stepDist)*(1.0+u_multiplier*2.0)-u_multiplier).a * curMulti;
    //        }
    //    }
    //    float alphaNew=alpha/totalSum*2.0; //this was for testing or other functions higher
    //    if (alphaNew >= 1.0){
    //        alphaNew = 1.0;
    //    }
    //    gl_FragColor = vec4(u_color.xyz, max(alphaNew * u_maxOpacity - 0.1, 0.0));
    ////    gl_FragColor = vec4(alphaNew*0.5, 0.0, 0.0, 1.0);
    //
    //    if(alphaNew < 0.1){
    //        gl_FragColor = vec4(u_color.xyz, 1.0);
    //    }


    //    if (totalSum<0.2){awas
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