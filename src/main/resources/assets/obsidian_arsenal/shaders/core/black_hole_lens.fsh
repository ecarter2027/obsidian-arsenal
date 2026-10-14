#version 150

// Black hole: gravitational lensing + lensed accretion disk (Interstellar / "Gargantua" look), purple.
//  Sampler0 = copy of the frame so far.  Tilt = sin(camera elevation above the disk plane), signed.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform float Tilt;

in vec2 texCoord0;
out vec4 fragColor;

const float HORIZON = 0.24;      // event horizon radius (disc units)
const float EINSTEIN = 0.40;     // Einstein radius: where the background is stretched into a ring
const vec3 HOT = vec3(1.0, 0.93, 1.0);
const vec3 GLOW = vec3(0.72, 0.30, 1.0);
const vec3 DEEP = vec3(0.35, 0.08, 0.75);

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
// glowing, streaky disk material as a function of radius along the disk and position around it
vec3 diskColor(float rad, float ang, float secs) {
    float t = clamp((rad - 0.30) / 0.62, 0.0, 1.0);                  // 0 inner edge -> 1 outer edge
    float streaks = 0.55 + 0.45 * noise(vec2(rad * 46.0, ang * 3.0 - secs * 1.6));
    streaks *= 0.7 + 0.3 * sin(rad * 120.0 - secs * 3.0);
    vec3 c = mix(HOT, GLOW, smoothstep(0.0, 0.45, t));
    c = mix(c, DEEP, smoothstep(0.45, 1.0, t));
    float bright = (1.6 - 1.3 * t) * streaks;
    return c * bright;
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r >= 1.0) discard;

    vec2 dpx = vec2(dFdx(p.x), dFdy(p.y));
    if (abs(dpx.x) < 1e-5) dpx.x = 1e-5;
    if (abs(dpx.y) < 1e-5) dpx.y = 1e-5;
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float secs = GameTime * 1200.0;

    // ---- background lensing: point-mass lens (beta = theta - thetaE^2/theta), faded out at the rim
    float fade = smoothstep(1.0, 0.55, r);
    float beta = r - fade * (EINSTEIN * EINSTEIN) / max(r, 0.05);
    vec2 dir = p / max(r, 1e-4);
    vec2 sp = dir * beta;                                            // beta < 0 -> mirrored image inside the ring
    vec2 suv = clamp(uv + ((sp - p) / dpx) / ScreenSize, vec2(0.001), vec2(0.999));
    vec3 col = texture(Sampler0, suv).rgb;

    // ---- accretion disk (horizontal plane), seen with the camera's elevation
    float tilt = clamp(abs(Tilt), 0.10, 1.0);
    float above = Tilt >= 0.0 ? 1.0 : -1.0;
    float ang = atan(p.y, p.x);
    // direct image of the disk: an ellipse squashed by the viewing angle
    vec2 q = vec2(p.x, p.y / tilt);
    float dr = length(q);
    float diskMask = smoothstep(0.30, 0.34, dr) * smoothstep(0.95, 0.80, dr);
    float frontHalf = (p.y * above < 0.0) ? 1.0 : 0.0;               // near side passes in front of the hole
    vec3 disk = diskColor(dr, atan(q.y, q.x), secs) * diskMask;

    // lensed far side: arches over the top of the horizon (under it when seen from below)
    float arcR = mix(0.27, 0.40, (1.0 - tilt));
    float arcBand = exp(-pow((r - arcR) / (0.065 + 0.075 * (1.0 - tilt)), 2.0));
    float overTop = smoothstep(-0.15, 0.25, p.y * above);
    vec3 arc = diskColor(0.32 + (r - arcR) * 2.0 + 0.1, ang, secs) * arcBand * overTop * (1.45 - 0.55 * tilt);
    // secondary image hugging the bottom of the horizon
    float lowBand = exp(-pow((r - 0.265) / 0.022, 2.0)) * smoothstep(0.1, -0.3, p.y * above);
    vec3 low = HOT * lowBand * 0.9;
    // photon ring
    float photon = exp(-pow((r - 0.252) / 0.012, 2.0));

    vec3 glowHalo = GLOW * exp(-pow((r - 0.3) / 0.22, 2.0)) * 0.35;

    // compose: background -> far-side arc -> horizon -> near side of the disk on top
    col += arc + low + HOT * photon * 1.4 + glowHalo;
    float hole = smoothstep(HORIZON, HORIZON - 0.02, r);
    col = mix(col, vec3(0.0), hole);
    // the disk occludes the horizon on its near side, sits behind it on the far side
    float diskVis = diskMask * (frontHalf + (1.0 - frontHalf) * (1.0 - hole));
    col += disk * diskVis;

    fragColor = vec4(col, 1.0);
}
