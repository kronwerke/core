#version 150

// The air around the crystal bends through the pull: the picture is drawn in towards the
// crystal and turned around it, most near the middle and fading out at the radius, the
// colours part at the edge of the bent region, and a thin bright ring marks where light
// is caught. Strength runs from 0 to about 1.6, Radius is in screen heights.

uniform sampler2D DiffuseSampler;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Aspect;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

vec2 bend(vec2 uv, float k) {
    vec2 d = uv - Center;
    d.x *= Aspect;
    float r = length(d);
    if (r >= Radius || r < 0.00001) return uv;
    float f = 1.0 - r / Radius;
    float fall = f * f;
    // drawn inwards: the source is sampled further out, so the middle looks magnified and pulled in
    float pull = 1.0 + fall * 0.55 * k;
    float turn = fall * 1.4 * k + sin(Time * 1.7 + r * 30.0) * 0.03 * k * fall;
    float c = cos(turn), s = sin(turn);
    vec2 e = mat2(c, -s, s, c) * (d / pull);
    e.x /= Aspect;
    return Center + e;
}

void main() {
    float k = Strength;
    vec2 base = bend(texCoord, k);
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float r = length(d) / max(Radius, 0.0001);
    // the colours part where the bending is strongest, towards the edge
    float split = smoothstep(0.2, 0.9, r) * (1.0 - smoothstep(0.9, 1.0, r)) * 0.012 * k;
    vec2 dir = length(d) > 0.0001 ? normalize(d) : vec2(0.0);
    dir.x /= Aspect;
    vec4 col;
    col.r = texture(DiffuseSampler, clamp(bend(texCoord + dir * split, k), 0.001, 0.999)).r;
    col.g = texture(DiffuseSampler, clamp(base, 0.001, 0.999)).g;
    col.b = texture(DiffuseSampler, clamp(bend(texCoord - dir * split, k), 0.001, 0.999)).b;
    col.a = 1.0;
    // a thin bright ring where the light is caught, and a darkening just inside it
    float ring = exp(-pow((r - 0.86) / 0.035, 2.0)) * 0.35 * k;
    float shade = smoothstep(0.5, 0.82, r) * (1.0 - smoothstep(0.82, 0.9, r)) * 0.25 * k;
    col.rgb = col.rgb * (1.0 - shade) + ring * vec3(0.85, 0.9, 1.0);
    fragColor = col;
}
