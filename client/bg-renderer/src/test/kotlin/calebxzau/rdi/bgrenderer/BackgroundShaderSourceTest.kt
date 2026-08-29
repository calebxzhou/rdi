package calebxzau.rdi.bgrenderer

import kotlin.test.Test
import kotlin.test.assertTrue

class BackgroundShaderSourceTest {
    @Test
    fun dedicatedWaterShaderOwnsWaterAnimation() {
        assertTrue(WATER_VERTEX_SHADER.contains("uniform float uTime;"))
        assertTrue(WATER_VERTEX_SHADER.contains("uTime * 1.4"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uWaterNormalOffset"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 4.0"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("uniform float uTime;"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("uWaterFrame"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("uWaterAlbedo"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("albedoTexel"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("animatedUv"))
        assertTrue(!WATER_VERTEX_SHADER.contains("vUv"))
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        assertTrue(!rendererSource.contains("waterFrameLocation"))
        assertTrue(!rendererSource.contains("waterAlbedoLocation"))
        assertTrue(!rendererSource.contains("\"uWaterFrame\""))
        assertTrue(!rendererSource.contains("\"uWaterAlbedo\""))
        assertTrue(!rendererSource.contains("materialTextures[BlockMaterial.WATER.ordinal]"))
    }

    @Test
    fun texturedSceneUsesOneSamplerPerMaterialBatch() {
        assertTrue(VERTEX_SHADER.contains("in vec2 aUv;"))
        assertTrue(VERTEX_SHADER.contains("vUv = aUv;"))
        assertTrue(FRAGMENT_SHADER.contains("uniform sampler2D uTexture;"))
        assertTrue(FRAGMENT_SHADER.contains("uniform int uMaterial;"))
        assertTrue(FRAGMENT_SHADER.contains("texture(uTexture, uv)"))
        assertTrue(FRAGMENT_SHADER.contains("uv.y / 3.0"))
        assertTrue(FRAGMENT_SHADER.contains("uniform sampler2DShadow uShadowMap;"))
        assertTrue(FRAGMENT_SHADER.contains("vec3 ambientLight = vec3(0.46, 0.48, 0.52) * mix(1.0, vAmbientOcclusion, 0.55);"))
        assertTrue(FRAGMENT_SHADER.contains("float directShadow = mix(0.22, 1.0, shadowFactor);"))
        assertTrue(FRAGMENT_SHADER.contains("vec3 directLight = vec3(1.10, 0.90, 0.68) * light * directShadow;"))
        assertTrue(FRAGMENT_SHADER.contains("shadowFactor += texture(uShadowMap"))
        assertTrue(FRAGMENT_SHADER.contains("shadowFactor /= 9.0;"))
        assertTrue(FRAGMENT_SHADER.contains("float emissionMask = smoothstep(0.25, 0.75, lanternBrightness);"))
        assertTrue(FRAGMENT_SHADER.contains("vec3(1.8, 0.72, 0.16)"))
        assertTrue(VERTEX_SHADER.contains("layout(location = 5) in float aAmbientOcclusion;"))
        assertTrue(VERTEX_SHADER.contains("uniform mat4 uShadowViewProjection;"))
        assertTrue(VERTEX_SHADER.contains("vShadowClipPosition = uShadowViewProjection * worldPosition;"))
        assertTrue(!FRAGMENT_SHADER.contains("vec3 ambientLight = vec3(0.42, 0.46, 0.52);"))
        assertTrue(!FRAGMENT_SHADER.contains("vec3 directLight = vec3(1.00, 0.82, 0.58) * light;"))
        assertTrue(!FRAGMENT_SHADER.contains("float brightness = 0.48 + light * 0.62;"))
        assertTrue(!FRAGMENT_SHADER.contains("uTextures["))
        assertTrue(!FRAGMENT_SHADER.contains("texture(uTextures[vMaterial]"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform sampler2D uSceneColor;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("texture(uSceneColor, transmissionUv)"))
    }

    @Test
    fun staticShadowShaderExcludesDynamicGeometryAndKeepsCutouts() {
        assertTrue(SHADOW_VERTEX_SHADER.contains("layout(location = 3) in float aKind;"))
        assertTrue(SHADOW_VERTEX_SHADER.contains("layout(location = 4) in vec2 aUv;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("if (vKind != 0) discard;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("if (uMaterial == 7) uv = vec2(uv.x, uv.y / 3.0);"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("if (cutout && texel.a < 0.1) discard;"))
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        assertTrue(rendererSource.contains("glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_R_TO_TEXTURE)"))
        assertTrue(rendererSource.contains("glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL)"))
        assertTrue(rendererSource.contains("glDrawBuffer(GL_NONE)"))
        assertTrue(rendererSource.contains("renderStaticShadowMap()"))
        assertTrue(rendererSource.contains("glActiveTexture(GL_TEXTURE5)"))
    }

    @Test
    fun staticShadowBakeRestoresAllMutatedGlState() {
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        val body = rendererSource.substring(
            rendererSource.indexOf("private fun renderStaticShadowMap").also { check(it >= 0) },
            rendererSource.indexOf("\n    fun renderSceneBeforeWater").also { check(it >= 0) }
        )
        val draw = body.indexOf("glDrawArrays")
        val finallyBlock = body.indexOf("} finally {")
        listOf(
            "val previousDepthFunc = glGetInteger(GL_DEPTH_FUNC)",
            "val previousCullMode = glGetInteger(GL_CULL_FACE_MODE)",
            "val previousPolygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR)",
            "val previousPolygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS)",
            "val previousClearDepth = glGetFloat(GL_DEPTH_CLEAR_VALUE)",
            "val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)",
            "val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)",
            "val previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)",
            "val previousTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D)"
        ).forEach { token ->
            assertTrue(body.indexOf(token) >= 0)
            assertTrue(body.indexOf(token) < draw)
        }
        assertTrue(finallyBlock > draw)
        listOf(
            "glBindTexture(GL_TEXTURE_2D, previousTexture0)",
            "glActiveTexture(previousActiveTexture)",
            "glBindVertexArray(previousVertexArray)",
            "glUseProgram(previousProgram)",
            "glDepthFunc(previousDepthFunc)",
            "glCullFace(previousCullMode)",
            "glPolygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits)",
            "glClearDepth(previousClearDepth.toDouble())"
        ).forEach { token -> assertTrue(body.indexOf(token, finallyBlock) >= 0) }
    }

    @Test
    fun waterHorizonFogUsesWorldPositionAndOnlyRunsForWater() {
        assertTrue(WATER_VERTEX_SHADER.contains("vWorldPosition = position;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform vec3 uEyePosition;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform vec3 uHorizonColor;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("smoothstep(uHorizonFogStart, uHorizonFogEnd"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("length(vWorldPosition.xz - uEyePosition.xz)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("color = vec4(result, 1.0)"))
    }

    @Test
    fun celestialShaderSamplesBundledSunTexture() {
        assertTrue(SUN_VERTEX_SHADER.contains("out vec2 vUv;"))
        assertTrue(SUN_VERTEX_SHADER.contains("vUv = aPosition / (2.0 * ${SUN_BILLBOARD_HALF_SIZE}) + 0.5;"))
        assertTrue(!SUN_VERTEX_SHADER.contains("vUv = aPosition * 0.5 + 0.5;"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("uniform sampler2D uSunTexture;"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("texture(uSunTexture, discUv)"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("vec2 discUv = centered * 1.8 + 0.5;"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("float squareDistance = max(abs(centered.x), abs(centered.y));"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("float discCoverage = 1.0 - smoothstep(0.20, 0.27, squareDistance);"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("float discDetail = max(disc.r, max(disc.g, disc.b));"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("vec3 discColor = vec3(1.0, 0.88, 0.68) + disc.rgb * 0.35 + vec3(discDetail * 0.15);"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("float halo = pow(radial, 3.0) * 0.65;"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("vec3 hdrSun = discColor * discCoverage * 2.0 + vec3(1.0, 0.72, 0.42) * halo;"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("float alpha = max(discCoverage, halo);"))
        assertTrue(!SUN_FRAGMENT_SHADER.contains("max(disc.a, halo)"))
        assertTrue(!SUN_FRAGMENT_SHADER.contains("disc.a"))
        assertTrue(SUN_FRAGMENT_SHADER.contains("if (alpha < 0.01) discard;"))
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        val celestialBody = rendererSource.substring(
            rendererSource.indexOf("private fun renderCelestial").also { check(it >= 0) },
            rendererSource.indexOf("\n    private fun renderClouds").also { check(it >= 0) }
        )
        val premultipliedBlend = celestialBody.indexOf(
            "glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)"
        )
        val sunDraw = celestialBody.indexOf("glDrawArrays(GL_TRIANGLES, 0, sunVertices.size / 2)")
        val defaultBlendRestore = celestialBody.indexOf(
            "glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)"
        )
        assertTrue(premultipliedBlend >= 0)
        assertTrue(sunDraw >= 0)
        assertTrue(defaultBlendRestore >= 0)
        assertTrue(premultipliedBlend < sunDraw)
        assertTrue(defaultBlendRestore > sunDraw)
    }

    @Test
    fun skyShaderReconstructsWorldRayFromNdcAndInverseMatrices() {
        assertTrue(SKY_VERTEX_SHADER.contains("out vec2 vNdc;"))
        assertTrue(SKY_VERTEX_SHADER.contains("vNdc = aPosition;"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("uniform mat4 uInverseProjection;"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("uniform mat4 uInverseView;"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("uniform vec3 uSunDirection;"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("uInverseProjection * vec4(vNdc, 1.0, 1.0)"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("mat3(uInverseView)"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("noonSkyColor(worldRay, normalize(uSunDirection)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("noonSkyColor(reflectedDirection"))
    }

    @Test
    fun skyShaderUsesFixedNoonScatteringGlareDitherWithoutLdrClamp() {
        assertTrue(SKY_FRAGMENT_SHADER.contains("noonUpSkyColor"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("noonMiddleSkyColor"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("noonDownSkyColor"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("scatteredGroundMixer"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("glareScatter"))
        assertTrue(SKY_FRAGMENT_SHADER.contains("Bayer8"))
        assertTrue(!SKY_FRAGMENT_SHADER.contains("clamp(finalSky, 0.0, 1.0)"))
        assertTrue(!SKY_FRAGMENT_SHADER.contains("gl_FragCoord.y /"))
    }

    @Test
    fun hdrAndPostProcessSourcesPreserveTheFixedBloomAndTonemapPipeline() {
        assertTrue(HDR_COLOR_FORMAT == org.lwjgl.opengl.GL30.GL_R11F_G11F_B10F)
        assertTrue(FINAL_COLOR_FORMAT == org.lwjgl.opengl.GL11.GL_RGBA8)
        assertTrue(BackgroundPostProcessor::class.java.declaredFields.any { it.name == "linearTexture" })
        assertTrue(LINEARIZE_FRAGMENT_SHADER.contains("pow(max(raw, vec3(0.0)), vec3(2.2))"))
        assertTrue(BLOOM_ATLAS_FRAGMENT_SHADER.contains("textureLod(uLinearHdr, bloomCoord, lod)"))
        assertTrue(BLOOM_ATLAS_FRAGMENT_SHADER.contains("textureSize(uLinearHdr, 0)"))
        assertTrue(!BLOOM_ATLAS_FRAGMENT_SHADER.contains("vec2(900.0, 540.0)"))
        assertTrue(BLOOM_ATLAS_FRAGMENT_SHADER.contains("4096.0"))
        assertTrue(BLOOM_ATLAS_FRAGMENT_SHADER.contains("vec2(0.1784375, 0.3325)"))
        assertTrue(BLOOM_ATLAS_FRAGMENT_SHADER.contains("* 0.4"))
        val mainBody = FINAL_TONEMAP_FRAGMENT_SHADER.substringAfter("void main()")
        assertTrue(mainBody.indexOf("DoBloom(") < mainBody.indexOf("DoCompTonemap("))
        assertTrue(mainBody.indexOf("DoCompTonemap(") < mainBody.indexOf("DoBSLColorSaturation("))
        assertTrue(mainBody.indexOf("DoBSLColorSaturation(") < mainBody.indexOf("color = vec4"))
        assertTrue(FINAL_TONEMAP_FRAGMENT_SHADER.contains("TM_EXPOSURE = 1.00"))
        assertTrue(FINAL_TONEMAP_FRAGMENT_SHADER.contains("TM_CONTRAST = 1.05"))
        assertTrue(FINAL_TONEMAP_FRAGMENT_SHADER.contains("0.0031308"))
        assertTrue(FINAL_TONEMAP_FRAGMENT_SHADER.contains("color = vec4(clamp(colorValue, 0.0, 1.0), 1.0)"))
    }

    @Test
    fun postProcessKotlinKeepsTheFixedPassOrderAndFinalReadTarget() {
        val source = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundPostProcessor.kt"
        ).readText()
        val processBody = source.substring(
            source.indexOf("fun process(rawHdrTexture: Int)").also { check(it >= 0) },
            source.indexOf("\n    private fun drawFullscreen").also { check(it >= 0) }
        )
        val linearBind = processBody.indexOf("glBindFramebuffer(GL_DRAW_FRAMEBUFFER, linearFramebuffer)")
        val mipmap = processBody.indexOf("glGenerateMipmap(GL_TEXTURE_2D)")
        val bloomBind = processBody.indexOf("glBindFramebuffer(GL_DRAW_FRAMEBUFFER, bloomFramebuffer)")
        val finalBind = processBody.indexOf("glBindFramebuffer(GL_FRAMEBUFFER, finalFramebuffer)")
        val finalReadBind = processBody.indexOf("glBindFramebuffer(GL_READ_FRAMEBUFFER, finalFramebuffer)")
        val linearDraw = processBody.indexOf("drawFullscreen()", linearBind)
        val bloomDraw = processBody.indexOf("drawFullscreen()", linearDraw + 1)
        val finalDraw = processBody.indexOf("drawFullscreen()", bloomDraw + 1)
        assertTrue(linearBind >= 0)
        assertTrue(linearDraw > linearBind)
        assertTrue(linearDraw < mipmap)
        assertTrue(mipmap > linearBind)
        assertTrue(bloomBind > mipmap)
        assertTrue(bloomDraw > bloomBind)
        assertTrue(bloomDraw < finalBind)
        assertTrue(finalBind > bloomBind)
        assertTrue(finalDraw > finalBind)
        assertTrue(finalDraw < finalReadBind)
        assertTrue(finalReadBind > finalBind)
    }

    @Test
    fun waterShaderUsesComplementaryNormalLayersFresnelNoiseAndSharedSun() {
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos = 0.032"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 4.0"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 0.25"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 0.05"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uWaterNoise"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("fresnel = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("const float BASE_WATER_OPACITY = 0.65;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vec3 baseWater = vColor;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("mix(BASE_WATER_OPACITY, 1.0, fresnel)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform vec3 uSkyColor;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("exp(-vec3(0.22, 0.10, 0.035) * thickness)"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("vec3(0.30, 0.62, 0.89)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform vec3 uSunDirection;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("ggxSunHighlight"))
        assertTrue(WATER_FRAGMENT_SHADER.indexOf("ggxSunHighlight") < WATER_FRAGMENT_SHADER.indexOf("smoothstep(uHorizonFogStart"))
        assertTrue(NOON_SKY_FUNCTIONS.contains("vec3 noonSkyColor"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vec3 noonSkyColor"))
    }

    @Test
    fun waterShaderUsesDepthDrivenTransmissionFoamAndPlanarReflection() {
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform sampler2D uSceneDepth;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform sampler2D uReflectionColor;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("uniform mat4 uInverseViewProjection;"))
        assertTrue(WATER_VERTEX_SHADER.contains("uniform mat4 uReflectionViewProjection"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("reconstructWorldPosition"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("candidateDepth > surfaceDepth"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("candidateWorld.y <= vWorldPosition.y + 0.15"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("transmissionUv = (candidateSky || candidateBehind)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("exp(-vec3(0.22, 0.10, 0.035) * thickness)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("smoothstep(0.12, 1.35, thickness)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("smoothstep(0.38, 0.68, waterNoise)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vReflectionClipPosition.w > 0.0"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("reflectionInBounds"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("roughness = 0.12"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("* vec3(1.0, 0.84, 0.62) * 0.10;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("return min(highlight, vec3(2.0));"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("* vec3(1.0, 0.84, 0.62) * 0.32;"))
    }

    @Test
    fun sceneAndPlayerShadersClipFinalWorldHeight() {
        assertTrue(VERTEX_SHADER.contains("uniform float uClipHeight;"))
        assertTrue(VERTEX_SHADER.contains("gl_ClipDistance[0] = worldPosition.y - uClipHeight;"))
        assertTrue(PLAYER_VERTEX_SHADER.contains("uniform float uClipHeight;"))
        assertTrue(PLAYER_VERTEX_SHADER.contains("gl_ClipDistance[0] = worldPosition.y - uClipHeight;"))
        assertTrue(WATER_VERTEX_SHADER.contains("vReflectionClipPosition = uReflectionViewProjection * worldPosition;"))
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        assertTrue(rendererSource.contains(".scale(PLAYER_VISUAL_SCALE)"))
        assertTrue(!rendererSource.contains(".scale(0.065f)"))
    }

    @Test
    fun cloudsUseVanillaPositionTexColorNormalAndAlphaCutout() {
        assertTrue(CLOUD_VERTEX_SHADER.contains("layout(location = 1) in vec2 aUv;"))
        assertTrue(CLOUD_VERTEX_SHADER.contains("layout(location = 2) in vec4 aColor;"))
        assertTrue(CLOUD_VERTEX_SHADER.contains("layout(location = 3) in vec3 aNormal;"))
        assertTrue(CLOUD_VERTEX_SHADER.contains("out vec3 vNormal;"))
        assertTrue(CLOUD_VERTEX_SHADER.contains("vNormal = aNormal;"))
        assertTrue(CLOUD_VERTEX_SHADER.contains("* 8.0"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("uniform sampler2D uCloudTexture;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("texture(uCloudTexture, vUv)"))
        assertTrue(!CLOUD_FRAGMENT_SHADER.contains("textureOffset"))
        assertTrue(!CLOUD_FRAGMENT_SHADER.contains("texel = max"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("in vec3 vNormal;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("uniform vec3 uSunDirection;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("vec3 lightDirection = normalize(-uSunDirection);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("float ndotl = dot(normal, lightDirection);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("float direct = 0.50 * max(ndotl, 0.0);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("float wrapped = clamp((ndotl + 0.55) / 1.55, 0.0, 1.0);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("float transmission = max(dot(-normal, lightDirection), 0.0) * 0.18;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("vec3 coolAmbient = vec3(0.82, 0.86, 0.92);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("vec3 warmSun = vec3(1.0, 0.84, 0.66);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("float ambient = 0.94 + 0.12 * wrapped;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("vec3 lighting = coolAmbient * ambient"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("+ warmSun * (direct + transmission);"))
        assertTrue(!CLOUD_FRAGMENT_SHADER.contains("vec3 coolAmbient = vec3(0.78, 0.86, 1.0);"))
        assertTrue(!CLOUD_FRAGMENT_SHADER.contains("0.72 + 0.20 * wrapped"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("vec3 litRgb = cloudColor.rgb * lighting;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("if (texel.a < 0.1) discard;"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("color = vec4(mix(litRgb, uCloudFogColor, fogValue), cloudColor.a);"))
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        val cloudBody = rendererSource.substring(
            rendererSource.indexOf("private fun renderClouds").also { check(it >= 0) },
            rendererSource.indexOf("\n    private fun uploadMatrix").also { check(it >= 0) }
        )
        assertTrue(cloudBody.contains("sun: SunState"))
        val sunUpload = cloudBody.indexOf("glUniform3f(cloudSunDirectionLocation")
        val firstCloudDraw = cloudBody.indexOf("glDrawArrays")
        assertTrue(sunUpload >= 0)
        assertTrue(firstCloudDraw >= 0)
        assertTrue(sunUpload < firstCloudDraw)
        assertTrue(rendererSource.contains("renderClouds(state.cloudTimeSeconds, passView, reflection, sun)"))
        assertTrue(cloudBody.contains("CLOUD_FOG_START"))
        assertTrue(cloudBody.contains("CLOUD_FOG_END"))
        assertTrue(!cloudBody.contains("WATER_HORIZON_FOG_START"))
        assertTrue(!cloudBody.contains("WATER_HORIZON_FOG_END"))
    }

    @Test
    fun cloudsUseMinecraftNearestTextureStateAndLinearFog() {
        assertTrue(!CLOUD_TEXTURE_LINEAR_FILTER)
        assertTrue(CLOUD_VERTEX_SHADER.contains("vViewDistance = length(viewPosition.xyz);"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("uCloudFogColor"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("uCloudFogStart"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("uCloudFogEnd"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("smoothstep(uCloudFogStart, uCloudFogEnd, vViewDistance)"))
        assertTrue(CLOUD_FRAGMENT_SHADER.contains("mix(litRgb, uCloudFogColor, fogValue)"))
    }
}
