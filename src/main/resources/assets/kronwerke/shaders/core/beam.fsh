#version 150

// The obelisk's beam: a white-hot core inside a soft glow of the beam's colour, energy that
// streams upward in threads, a slow spiral, and rings of light that travel up the column.
// Drawn on a ribbon that always faces the camera; beam.x runs across it, beam.y along it.

uniform float Time;  // seconds, real time, so the flow stays smooth in slow motion
uniform float Flow;  // how fast and how strongly the energy streams, 0 to 2
uniform float Rings; // how bright the travelling rings are, 0 to 1

in vec2 beam;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float a = abs(beam.x);
    if (a >= 1.0) discard;
    float v = beam.y;
    float t = Time * (0.6 + 0.7 * Flow);

    float core = exp(-a * a * 70.0);
    float glow = exp(-a * a * 5.0);
    float halo = (1.0 - a) * (1.0 - a) * 0.25;

    // threads of energy streaming upward, a little wavy
    float threads = noise(vec2(beam.x * 5.0 + sin(v * 0.05 + t) * 0.6, v * 0.12 - t * 3.0));
    threads = pow(threads, 3.0) * 2.2;
    // a fine shimmer
    float shimmer = noise(vec2(beam.x * 14.0, v * 0.6 - t * 9.0)) * 0.35;
    // a slow spiral wound around the column
    float spiral = pow(0.5 + 0.5 * sin(v * 0.45 - t * 4.0 + beam.x * 3.2), 10.0) * 0.8;
    // rings that travel up
    float rings = pow(0.5 + 0.5 * sin(v * 0.07 - t * 2.4), 40.0) * Rings * 1.6;

    float energy = glow * (0.55 + Flow * (threads + shimmer) * 0.6) + spiral * glow * Flow + rings * (1.0 - a) + halo;
    vec3 col = tint.rgb * energy + vec3(1.0) * core * (1.1 + 0.4 * threads);
    fragColor = vec4(col * tint.a, 1.0);
}
