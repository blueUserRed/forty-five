/*
from: https://www.shadertoy.com/view/wt23Rt
*/
~~~section export

vec3 hsv2rgb(vec3 c) {
    vec4 k = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    return c.z * mix(k.xxx, clamp(abs(fract(c.x + k.xyz) * 6.0 - k.w) - k.x, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0),
    p = -mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g)),
    q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec4 hsv2rgb(vec4 c) {
    return vec4(hsv2rgb(c.rgb), c.a);
}

vec4 rgb2hsv(vec4 c) {
    return vec4(rgb2hsv(c.rgb), c.a);
}

vec3 rgb2hsl(vec3 c){
    float cMin=min(min(c.r,c.g),c.b),
    cMax=max(max(c.r,c.g),c.b),
    delta=cMax-cMin;
    vec3 hsl=vec3(0.,0.,(cMax+cMin)/2.);
    if(delta!=0.0){
        if(hsl.z<.5){
            hsl.y=delta/(cMax+cMin);
        }else{
            hsl.y=delta/(2.-cMax-cMin);
        }
        float deltaR=(((cMax-c.r)/6.)+(delta/2.))/delta,
        deltaG=(((cMax-c.g)/6.)+(delta/2.))/delta,
        deltaB=(((cMax-c.b)/6.)+(delta/2.))/delta;
        if(c.r==cMax){
            hsl.x=deltaB-deltaG;
        }else if(c.g==cMax){
            hsl.x=(1./3.)+deltaR-deltaB;
        }else{
            hsl.x=(2./3.)+deltaG-deltaR;
        }
        hsl.x=fract(hsl.x);
    }
    return hsl;
}

vec3 hue2rgb(float hue){
    hue = fract(hue);
    return clamp(vec3(
        abs(hue*6.-3.)-1.,
        2.-abs(hue*6.-2.),
        2.-abs(hue*6.-4.)
    ), 0.0, 1.0);
}

vec3 hsl2rgb(vec3 hsl){
    if(hsl.y==0.){
        return vec3(hsl.z);
    }else{
        float b;
        if(hsl.z<.5){
            b=hsl.z*(1.+hsl.y);
        }else{
            b=hsl.z+hsl.y-hsl.y*hsl.z;
        }
        float a=2.*hsl.z-b;
        return a+hue2rgb(hsl.x)*(b-a);
    }
}

vec4 hsl2rgb(vec4 c) {
    return vec4(hsl2rgb(c.rgb), c.a);
}

vec4 rgb2hsl(vec4 c) {
    return vec4(rgb2hsl(c.rgb), c.a);
}

vec3 brightenColor(vec3 color, float by) {
    vec3 hsl = rgb2hsl(color);
    hsl.b += by;
    return hsl2rgb(hsl);
}

vec4 brightenColor(vec4 color, float by) {
    return vec4(brightenColor(color.rgb, by), color.a);
}
