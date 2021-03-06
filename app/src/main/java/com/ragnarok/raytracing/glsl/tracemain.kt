package com.ragnarok.raytracing.glsl

import com.ragnarok.raytracing.glsl.PassVariable.infinity
import com.ragnarok.raytracing.glsl.PassVariable.pi
import org.intellij.lang.annotations.Language

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
    precision highp int;
    
    out vec4 FragColor;
    in vec2 TexCoords;
    uniform sampler2D texture;
    
    uniform int toneMapping;
    
    vec3 toneMap(vec3 src) {
    	vec3 color = src / (1.0 + src);
    	color = pow(color,vec3(1.0/2.2,1.0/2.2,1.0/2.2));
    	return color;
    }

    void main() {
        vec3 color = texture(texture, TexCoords).rgb;
        if (toneMapping == 1) {
            color = toneMap(color);
        }
        FragColor = vec4(color, 1.0);
    }
""".trimIndent()

@Language("glsl")
val tracerVs = """
    #version 300 es

    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoords;
    
    uniform mat4 model;
    uniform mat4 view;
    uniform mat4 projection;
    
    uniform vec3 eye;
//    uniform vec3 ray00;
//    uniform vec3 ray01;
//    uniform vec3 ray10;
//    uniform vec3 ray11;

    out vec3 traceRay;
    out vec3 eyePos;
    out vec2 vPos;
    
    void main()
    {
        vec2 percent = aPos.xy * 0.5 + 0.5; // [-1, 1] to [1, 1]
        vPos = aPos.xy;
//        vec3 dir = mix(mix(ray00, ray01, percent.y), mix(ray10, ray11, percent.y), percent.x);
        eyePos = eye;
//        traceRay = dir;
        gl_Position =  vec4(aPos, 1.0);
    }
""".trimIndent()

// main path tracing loop
@Language("glsl")
val traceLoop = """
    vec3 calcColor(Ray ray) {
        vec3 radiance = vec3(0.0);
        vec3 throughput = vec3(1.0);
        
//        vec3 directionDir = directionLightDir(directionLight);
        vec3 pointDir = pointLightDir(pointLight);
        
        Material material;
        Intersection lastIntersect;
        Ray lastRay;
        float pdf = 1.0;
        bool specularBounce = false;
        vec3 ambient = vec3(0.0);
        for (int pass = 0; pass < ${PassVariable.bounces}; pass++) {
            Intersection lightIntersect = intersectPointLight(ray, pointLight);
            Intersection intersect = intersectScene(ray);
            
            
            if (intersect.t == $infinity) {
                ambient = getSkyboxColorByRay(ray);
                if (lightIntersect.nearFar.x > 0.0 && lightIntersect.nearFar.x < intersect.t) {
                    float t = lightIntersect.nearFar.x;
                    float lightPdf = (t * t) / (4.0 * $pi * pointLight.radius * pointLight.radius);
                    vec3 color = samplePointLight(pass, specularBounce, pdf, lightPdf, pointLight.color);
                    radiance += color * throughput * ambient;
                    intersect = lightIntersect;
                } else {       
                    radiance += ambient * throughput;
                }
                break;
            }
            
            lastIntersect = intersect;
            vec3 pointLightDir = intersect.hit - pointDir; // point light to intersection

//            vec3 directionLightDir = -directionDir;
            
            float shadow = 1.0;
            float specular = 0.0;
            bool isDiffuseRay = false;
            bool isGlassRay = false;
            material = intersect.material;
            vec3 color = material.color;
            
            Ray newRay = materialRay(ray, intersect, -pointLightDir, pass, specular, isDiffuseRay);
            
            shadow = getShadow(intersect, -pointLightDir);
            
            vec3 pointLightColor = pointLight.color * pointLightAttenuation(pointLight, intersect.hit) * pointLight.intensity;
//            vec3 directionLightColor = directionLight.color;

            ray.pbrBRDF = true;
            newRay.pbrBRDF = true;
            newRay.pbrDiffuseRay = isDiffuseRay;
            vec3 viewDir = normalize(lastIntersect.hit - intersect.hit);
            // point light and direction light color
            pointLightColor = brdfLightColor(intersect.normal, -pointLightDir, viewDir, pointLightColor, intersect.material) * throughput;
//            radiance += brdfLightColor(intersect.normal, directionLightDir, viewDir, directionLightColor, intersect.material);
            
            radiance += throughput * intersect.material.emissive;
            // material diffuse and specular color
            if (intersect.material.glass == false) {
                throughput *= brdfMaterialColor(intersect.normal, -ray.direction, ray.origin, intersect.material, isDiffuseRay);
                pdf = brdfMaterialPdf(intersect.normal, -ray.direction, ray.origin, intersect.material, isDiffuseRay);
                specularBounce = !isDiffuseRay;
            } else {
                throughput *= intersect.material.color;
                pdf = 1.0;
                specularBounce = true;
            }
            radiance += throughput * pointLightColor * shadow;
            
            lastRay = ray;
            newRay.origin = intersect.hit + newRay.direction * ${PassVariable.eps};
            ray = newRay;
            ray.time = mix(cameraShutterOpenTime, cameraShutterCloseTime, randSeed());
            
            // russian roulette
            float p = max(throughput.r, max(throughput.g, throughput.b));
            if (random(pass) > p) {
                break;
            }
            throughput *= 1.0 / p;
        }
        return max(radiance,vec3(0.0));
    }
""".trimIndent()

val commonDataFunc = """
    $random
    $primitives
    $lights
    $intersections
    $skybox
""".trimIndent()

//TODO: need to refactor to create static scene
val tracerFs = { scene: String ->
    @Language("glsl")
    val shader = """
    #version 300 es
    precision highp float;
    precision highp int;
    
    out vec4 FragColor;
    
    in vec3 traceRay;
    in vec3 eyePos;
    in vec2 vPos;
    
    // camera data
    uniform mat4 cameraWorldMatrix;
    uniform float cameraAspect;
    uniform float cameraFov;
    uniform float cameraAperture;
    uniform float cameraFocusLength;
    uniform float cameraShutterOpenTime;
    uniform float cameraShutterCloseTime;
    
    uniform float weight; // current render output weight mix with last pass output
    uniform float time; // tick to create diffuse/glossy ray
    uniform int frame;
    
    uniform sampler2D previous; // last pass output
    
    uniform sampler2D skybox; // background skybox
    
    $commonDataFunc
    
    $scene
    
    $shadow

    $traceLoop
    
    Ray getInitRay() {
        vec2 jitter = vec2((ran.x - 0.5) / ${PassVariable.eachPassOutputWidth}, (ran.y - 0.5) / ${PassVariable.eachPassOutputHeight}) * 0.5;
        vec2 vPosJitter = vPos + jitter;
        vec3 direction = vec3(vPosJitter, -1.0) * vec3(cameraAspect, 1.0, cameraFov);
        direction = normalize(direction);
        vec3 origin = cameraWorldMatrix[3].xyz;
        direction = mat3(cameraWorldMatrix) * direction;
        return createRay(origin, direction);
    }
    
    // http://www.pbr-book.org/3ed-2018/Camera_Models/Projective_Camera_Models.html#TheThinLensModelandDepthofField
    Ray getInitRayWithDepthOfField() {
        vec2 jitter = vec2((ran.x - 0.5) / ${PassVariable.eachPassOutputWidth}, (ran.y - 0.5) / ${PassVariable.eachPassOutputHeight}) * 0.5;
        vec2 vPosJitter = vPos + jitter;
        vec3 direction = vec3(vPosJitter, -1.0) * vec3(cameraAspect, 1.0, cameraFov);
        direction = normalize(direction);
        
        vec2 lensPoints = cameraAperture * sampleCircle(vec2(randSeed(), randSeed()));
        // intersect ray with focus plane
        float t = cameraFocusLength / direction.z; // intersect ray distance t
        // calculate the intersect point on focus plane
        // note the inverse direction since the origin direction of the ray is point outward of the lens
        vec3 focusPoint = -direction * t; 
        vec3 origin = vec3(lensPoints, 0.0);
        direction = normalize(focusPoint - origin);
        
        origin = vec3(cameraWorldMatrix * vec4(origin, 1.0));
        direction = mat3(cameraWorldMatrix) * direction;

        return createRay(origin, direction);
    }

    void main() {
        Ray ray;
        if (cameraAperture > 0.0 && cameraFocusLength > 0.0) {
            ray = getInitRayWithDepthOfField();
        } else {
            ray = getInitRay();
        }
        if (cameraShutterOpenTime >= 0.0 && cameraShutterCloseTime > 0.0 && cameraShutterCloseTime > cameraShutterOpenTime) {
            ray.time = mix(cameraShutterOpenTime, cameraShutterCloseTime, randSeed());
        }
        vec3 color = calcColor(ray);
        color = max(vec3(0.0), color);
        vec2 coord = vec2(gl_FragCoord.x / ${PassVariable.eachPassOutputWidth}, gl_FragCoord.y / ${PassVariable.eachPassOutputHeight});
        vec3 previousColor = texture(previous, coord).rgb;
        FragColor = vec4(mix(color, previousColor, weight), 1.0);
    }
    
    """
    shader
}