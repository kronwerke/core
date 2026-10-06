#version 150

// A ring of pressure that runs out from the obelisk over the whole picture: pixels on the
// ring are pushed outward, the colours split a little, and the ring itself glows brass.

uniform sampler2D DiffuseSampler;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Aspect;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float dist = length(d);
    float width = 0.05 + 0.05 * Radius;
    float x = (dist - Radius) / width;
    float ring = exp(-x * x);
    vec2 dir = dist > 0.0001 ? d / dist : vec2(0.0);
    dir.x /= Aspect;
    float push = ring * Strength * 0.045 * (x < 0.0 ? -1.0 : 1.0);
    vec2 uv = clamp(texCoord - dir * push, 0.001, 0.999);
    vec4 c = texture(DiffuseSampler, uv);
    float split = ring * Strength * 0.008;
    c.r = texture(DiffuseSampler, clamp(uv + dir * split, 0.001, 0.999)).r;
    c.b = texture(DiffuseSampler, clamp(uv - dir * split, 0.001, 0.999)).b;
    c.rgb += ring * Strength * 0.22 * vec3(1.0, 0.85, 0.5);
    fragColor = vec4(c.rgb, 1.0);
}
