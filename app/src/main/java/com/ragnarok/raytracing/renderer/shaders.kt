package com.ragnarok.raytracing.renderer

import org.intellij.lang.annotations.Language

const val eps = 0.0001
const val bounces = 5.0
const val infinity = 10000.0

object PassConstants {
    var eachPassOutputWidth = 512.0
    var eachPassOutputHeight = 512.0
}

@Language("glsl")
val outputVs = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoords;

    out vec2 TexCoords;
    void main() {
        TexCoords = aTexCoords;
        gl_Position = vec4(aPos, 1.0);
    }
""".trimIndent()

@Language("glsl")
val outputFs = """
    #version 300 es
    precision highp float;
    
    out vec4 FragColor;
    in vec2 TexCoords;
    uniform sampler2D texture;

    void main() {
        FragColor = texture(texture, TexCoords);
//        FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    }
""".trimIndent()

@Language("glsl")
val tracerVs = """
    #version 300 es
    precision highp float;
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoords;
    
    uniform mat4 model;
    uniform mat4 view;
    uniform mat4 projection;
    
    uniform vec3 eye;
    uniform vec3 ray00;
    uniform vec3 ray01;
    uniform vec3 ray10;
    uniform vec3 ray11;
    
    out vec3 WorldPos;
    out vec3 traceRay;
    out vec3 eyePos;
    
    void main()
    {
        vec2 percent = aPos.xy * 0.5 + 0.5; // [-1, 1] to [0, 1]
        vec3 dir = mix(mix(ray00, ray01, percent.y), mix(ray10, ray11, percent.y), percent.x);
        WorldPos = vec3(model * vec4(aPos, 1.0));
        eyePos = eye;
        traceRay = dir;
        gl_Position =  vec4(aPos, 1.0);
    }
""".trimIndent()

//// rays pdf

// simple pseudorandom-looking function in glsl from
// https://stackoverflow.com/questions/4200224/random-noise-functions-for-glsl
@Language("glsl")
val randomFunc = """
    float random(vec2 co, float bias){
        return fract(sin(dot(co.xy, vec2(12.9898,78.233))) * 43758.5453 + bias);
    }
""".trimIndent()


const val piVal = "3.141592654"

@Language("glsl")
const val randomVec1a = "vec2(gl_FragCoord.x + time, gl_FragCoord.y + time)"

@Language("glsl")
const val randomVec2b = "vec2(gl_FragCoord.y + time, gl_FragCoord.x + time)"

@Language("glsl")
const val randomVec3a = "vec3(12.9898, 78.233, 151.7182)"

@Language("glsl")
const val randomVec3b = "vec3(63.7264, 10.873, 623.6736)"

// check https://raytracing.github.io/books/RayTracingTheRestOfYourLife.html#generatingrandomdirections
@Language("glsl")
val uniformRandomDirection = """
    vec3 uniformRandomDirection() {
        float r1 = random($randomVec1a, time);
        float r2 = random($randomVec2b, time);
        
        float z = 1.0 - 2.0 * r2;
        float phi = 2.0 * $piVal * r1;
        float x = cos(phi) * sqrt(r2);
        float y = sin(phi) * sqrt(r2);
        return vec3(x, y, z);
    }
""".trimIndent()

@Language("glsl")
val cosineWeightDirection = """
    vec3 cosineWeightDirection(vec3 normal, float bias) {
        float r1 = random($randomVec1a, time + bias);
        float r2 = random($randomVec2b, time + bias);
        float r = sqrt(r1);
        float theta = 2.0 * $piVal * r2;
        float x = r * cos(theta);
        float y = r * sin(theta);
        float z = sqrt(1.0 - x * x - y * y); // unit sphere
        // calc new ortho normal basic
        vec3 s,t;
        if (abs(normal.x) < 0.5) {
            s = cross(normal, vec3(1, 0, 0));
        } else {
            s = cross(normal, vec3(0, 1, 0));
        }
        t = cross(normal, s);
        return x * s + y * t + z * normal;
    }
""".trimIndent()

val randomRayFunc = """
$randomFunc
$uniformRandomDirection
$cosineWeightDirection
""".trimIndent()

//// cornell box scene
@Language("glsl")
val intersectCubeFunc = """
    vec2 intersectCube(vec3 origin, vec3 ray, vec3 cubeMin, vec3 cubeMax) {
        vec3 tMin = (cubeMin - origin) / ray;
        vec3 tMax = (cubeMax - origin) / ray;
        vec3 t1 = min(tMin, tMax);
        vec3 t2 = max(tMin, tMax);
        float tNear = max(max(t1.x, t1.y), t1.z);
        float tFar = min(min(t2.x, t2.y), t2.z);
        return vec2(tNear, tFar);
    }
""".trimIndent()

@Language("glsl")
val cubeNormalFs = """
    vec3 normalForCube(vec3 hit, vec3 cubeMin, vec3 cubeMax) {
        if (hit.x < cubeMin.x + $eps) return vec3(-1.0, 0.0, 0.0);
        else if (hit.x > cubeMax.x - $eps) return vec3(1.0, 0.0, 0.0);
        else if (hit.y < cubeMin.y + $eps) return vec3(0.0, -1.0, 0.0);
        else if (hit.y > cubeMax.y - $eps) return vec3(0.0, 1.0, 0.0);
        else if (hit.z < cubeMin.z + $eps) return vec3(0.0, 0.0, -1.0);
        else return vec3(0.0, 0.0, 1.0);
    }
""".trimIndent()

@Language("glsl")
val roomCubeDefine = """
    vec3 roomCubeMin = vec3(-1.0, -1.0, -1.0);
    vec3 roomCubeMax = vec3(1.0, 1.0, 1.0);
""".trimIndent()

val cornellBoxFunc = """
$intersectCubeFunc
$cubeNormalFs
$roomCubeDefine
"""

@Language("glsl")
const val backgroundColor = "vec3(0.75)"

@Language("glsl")
const val lightColor = "vec3(0.5)"

@Language("glsl")
const val lightPos = "vec3(0.0, 0.8, -0.5)"

@Language("glsl")
val calcColorFs = """
    vec3 calcColor(vec3 origin, vec3 ray, vec3 light) {
        vec3 colorMask = vec3(1.0);
        vec3 finalColor = vec3(0.0);
        
        for (float pass = 0.0; pass < $bounces; pass++) {
            vec2 tRoom = intersectCube(origin, ray, roomCubeMin, roomCubeMax);
            
            float t = $infinity;
            if (tRoom.x < tRoom.y) {
                t = tRoom.y;
            }
            
            vec3 hit = origin + ray * t;
            vec3 normal = vec3(0);
            vec3 surfaceColor = $backgroundColor;
            if (t == tRoom.y) {
                normal = -normalForCube(hit, roomCubeMin, roomCubeMax);
                
                float delta = 0.9999;
                if (hit.x < -1.0 * delta) {
                    surfaceColor = vec3(1.0, 0.3, 0.1);
                } else if (hit.x > delta) {
                    surfaceColor = vec3(0.3, 1.0, 0.1);
                }
                // create a new diffuse ray
                ray = cosineWeightDirection(normal, pass);
            } else if (t == $infinity) {
                break;
            } else {
            }
            
            vec3 lightDir = light - hit;
            float NdotL = max(0.0, dot(normalize(lightDir), normal));
            
            colorMask *= surfaceColor;
            finalColor += colorMask * ($lightColor * NdotL);
            
            origin = hit;
        }
        
        return finalColor;
    }
""".trimIndent()


@Language("glsl")
val tracerFs = """
    #version 300 es
    precision highp float;
    
    out vec4 FragColor;
    
    in vec3 WorldPos;
    in vec3 traceRay;
    in vec3 eyePos;
    
    uniform float weight; // current render output weight mix with last pass output
    uniform float time; // tick to create diffuse/glossy ray
    
    uniform sampler2D previous; // last pass output
    
    $randomRayFunc
        
    $cornellBoxFunc
    
    $calcColorFs

    
    void main() {
        vec3 lightRay = $lightPos + uniformRandomDirection() * 0.1;
        vec3 color = calcColor(eyePos, traceRay, lightRay);
        vec2 coord = vec2(gl_FragCoord.x / ${PassConstants.eachPassOutputWidth}, gl_FragCoord.y / ${PassConstants.eachPassOutputHeight});
        vec3 previousColor = texture(previous, coord).rgb;
        FragColor = vec4(mix(color, previousColor, weight), 1.0);
    }
""".trimIndent()
