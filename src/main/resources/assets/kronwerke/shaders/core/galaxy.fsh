#version 150

// The sky torn open: a spiral galaxy seen from inside its halo, with a field of stars, drifting
// nebulae and a few planets, lit by a sun that sits in the core. Everything is procedural;
// dir is the direction from the eye, Tear how far the opening has spread down from the zenith
// (0 nothing, 1 the whole sky), Fade the overall strength, Tier the stage that was reached.

uniform float Time;
uniform float Fade;
uniform float Tear;
uniform float Tier;

in vec3 dir;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i), n100 = hash(i + vec3(1, 0, 0)), n010 = hash(i + vec3(0, 1, 0)), n110 = hash(i + vec3(1, 1, 0));
    float n001 = hash(i + vec3(0, 0, 1)), n101 = hash(i + vec3(1, 0, 1)), n011 = hash(i + vec3(0, 1, 1)), n111 = hash(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// a star field: small bright points in cells of the sky
vec3 stars(vec3 d, float scale, float density, float size) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 col = vec3(0.0);
    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
        vec3 c = cell + vec3(x, y, z);
        float h = hash(c);
        if (h > density) continue;
        vec3 star = c + 0.5 + 0.4 * (vec3(hash(c + 1.1), hash(c + 2.2), hash(c + 3.3)) - 0.5);
        float dist = length(p - star);
        float twinkle = 0.75 + 0.25 * sin(Time * (2.0 + 4.0 * hash(c + 4.4)) + h * 50.0);
        float b = smoothstep(size, 0.0, dist) * twinkle;
        vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.65), hash(c + 5.5));
        col += b * tint * (0.5 + h);
    }
    return col;
}

// a lit sphere at direction centre with angular radius r, seen from the eye
vec4 planet(vec3 d, vec3 centre, float r, vec3 colA, vec3 colB, vec3 sun, float bands) {
    float cosang = dot(d, centre);
    float edge = cos(r);
    if (cosang < edge - 0.002) return vec4(0.0);
    vec3 off = d - centre * cosang;
    float s = min(1.0, length(off) / sin(r));
    vec3 side = s > 0.0001 ? normalize(off) * s : vec3(0.0);
    float depth = sqrt(max(0.0, 1.0 - s * s));
    vec3 normal = normalize(side - centre * depth);
    vec3 lightDir = normalize(sun - centre * 0.6);
    float light = max(0.0, dot(normal, lightDir)) * 0.7 + 0.3;
    float band = 0.5 + 0.5 * sin(side.y * bands + fbm(normal * 3.0 + Time * 0.02) * 2.0);
    vec3 col = mix(colA, colB, band) * light;
    float rim = pow(1.0 - depth, 3.0);
    col += rim * 0.25 * colB * light;
    float limb = smoothstep(edge - 0.002, edge + 0.004, cosang);
    return vec4(col, limb);
}

void main() {
    vec3 d = normalize(dir);

    // the tear: it opens at the zenith and spreads down, with a ragged edge that shimmers
    float down = acos(clamp(d.y, -1.0, 1.0)) / 3.14159;
    float ragged = 0.06 * noise(d * 6.0 + Time * 0.3);
    float opening = Tear * 1.15;
    float mask = smoothstep(opening, opening - 0.12, down + ragged);
    if (mask <= 0.001 || Fade <= 0.001) discard;

    // a spiral galaxy seen face on, hanging over the zenith and turning slowly
    vec3 c = normalize(vec3(0.25, 0.9, 0.15));
    vec3 t1 = normalize(cross(c, vec3(0.0, 0.0, 1.0)));
    vec3 t2 = cross(c, t1);
    float spin = Time * 0.012;
    float u = dot(d, t1), v = dot(d, t2);
    float ca = cos(spin), sa = sin(spin);
    vec2 uv = mat2(ca, -sa, sa, ca) * vec2(u, v);
    float facing = smoothstep(-0.15, 0.15, dot(d, c));
    float radial = length(uv);
    float ang = atan(uv.y, uv.x);
    float arms = 0.5 + 0.5 * cos(2.0 * ang - 7.0 * log(radial + 0.15) + Time * 0.03);
    float armsFine = 0.5 + 0.5 * cos(4.0 * ang - 9.0 * log(radial + 0.12) - Time * 0.02);
    float disc = smoothstep(0.98, 0.12, radial) * (0.2 + 0.6 * arms + 0.2 * armsFine) * facing;
    float core = exp(-radial * radial * 30.0) * facing;
    vec3 g = vec3(uv * 4.0, dot(d, c) * 2.0);
    float dust = fbm(g * 1.6 + Time * 0.01);
    vec3 nebula = mix(vec3(0.18, 0.1, 0.4), vec3(0.65, 0.35, 0.85), dust) * disc * 2.4;
    nebula += vec3(0.4, 0.75, 0.9) * disc * smoothstep(0.7, 0.3, dust) * 0.6;
    nebula += vec3(1.0, 0.8, 0.5) * core * 3.0;
    // tier colours: later stages burn warmer
    nebula = mix(nebula, nebula * vec3(1.2, 0.9, 0.7), clamp((Tier - 2.0) / 3.0, 0.0, 1.0));
    // cold dust lanes along the arms
    float lanes = smoothstep(0.5, 0.75, fbm(g * 3.0 - Time * 0.008)) * disc;
    nebula *= 1.0 - 0.55 * lanes;

    vec3 col = vec3(0.012, 0.01, 0.03);
    col += nebula;
    col += stars(d, 40.0, 0.1, 0.1) * 0.9;
    col += stars(d, 90.0, 0.06, 0.1) * 0.5;
    col += stars(vec3(uv, dot(d, c)), 30.0, 0.14, 0.14) * disc * 2.5;

    // the sun of this sky sits in the core, planets hang around the viewer
    vec3 sun = normalize(vec3(0.3, 0.75, -0.5));
    vec4 p1 = planet(d, normalize(vec3(0.55, 0.35, 0.6)), 0.09, vec3(0.85, 0.55, 0.3), vec3(0.95, 0.8, 0.6), sun, 14.0);
    vec4 p2 = planet(d, normalize(vec3(-0.7, 0.5, 0.2)), 0.05, vec3(0.2, 0.35, 0.7), vec3(0.6, 0.8, 0.95), sun, 6.0);
    vec4 p3 = planet(d, normalize(vec3(0.1, 0.6, -0.75)), 0.16, vec3(0.55, 0.35, 0.55), vec3(0.8, 0.6, 0.9), sun, 9.0);
    col = mix(col, p3.rgb, p3.a);
    col = mix(col, p1.rgb, p1.a);
    col = mix(col, p2.rgb, p2.a);
    // a ring around the biggest planet
    vec3 pc = normalize(vec3(0.1, 0.6, -0.75));
    vec3 ringN = normalize(vec3(0.3, 1.0, 0.2));
    float toPlane = dot(d - pc * dot(d, pc), ringN);
    float ringR = length(d - pc * dot(d, pc));
    float ring = smoothstep(0.012, 0.0, abs(toPlane)) * smoothstep(0.19, 0.2, ringR) * smoothstep(0.3, 0.26, ringR) * step(0.0, dot(d, pc) - cos(0.32));
    col = mix(col, vec3(0.9, 0.8, 0.65), ring * 0.8 * (1.0 - p3.a));

    // the edge of the tear glows brass
    float edgeGlow = smoothstep(opening - 0.12, opening - 0.02, down + ragged) * mask;
    col += edgeGlow * vec3(1.0, 0.7, 0.3) * 1.5;

    fragColor = vec4(col, Fade * mask * 0.97);
}
