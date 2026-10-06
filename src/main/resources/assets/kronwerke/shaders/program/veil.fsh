#version 150

// The veil of the torn sky over the whole picture: the edges darken, the colours cool and
// lose a little of their saturation, and a slow grain of light drifts through, so the world
// under the galaxy no longer looks like a sunny afternoon.

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    vec2 d = texCoord - 0.5;
    float vignette = smoothstep(0.95, 0.35, length(d) * 1.2);
    float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 cool = mix(c.rgb, vec3(lum), 0.35) * vec3(0.78, 0.84, 1.05);
    vec3 graded = mix(c.rgb, cool, Strength);
    graded *= mix(1.0, 0.45 + 0.55 * vignette, Strength);
    float grain = (hash(texCoord * 900.0 + Time) - 0.5) * 0.035 * Strength;
    fragColor = vec4(graded + grain, 1.0);
}
