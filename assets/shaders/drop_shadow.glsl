~~~section vertex

%include shaders/includes/default_vertex.glsl

~~~section fragment

in vec4 v_color;
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
uniform float u_brighten;


%include shaders/includes/color_utils.glsl

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
    float brighten = u_brighten;

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

    vec4 mixed = originalColor * blurred + (1.0 - originalColor) * inShadowColor;
    mixed = brightenColor(mixed, brighten);

    vec2 coords = v_texCoords;
    // really should be pythagoras but doesn't matter much in this case
    float distToEdge = min(coords.x, min(1.0 - coords.x, min(coords.y, 1.0 - coords.y)));
    float dropOff = distToEdge + 0.89;
    dropOff = pow(dropOff, 10.0);
    dropOff = clamp(dropOff, 0.0, 1.0);
    mixed.a *= dropOff;

    outColor = mixed;
}
