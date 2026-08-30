package calebxzau.rdi.bgrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundShaderSourceTest {
    @Test
    fun dedicatedWaterShaderOwnsWaterAnimation() {
        assertTrue(WATER_VERTEX_SHADER.contains("uniform float uTime;"))
        assertTrue(WATER_VERTEX_SHADER.contains("position.x * ${WATER_WAVE_X_FREQUENCY} + uTime * ${WATER_WAVE_X_TIME_SPEED}) * ${WATER_WAVE_X_AMPLITUDE}"))
        assertTrue(WATER_VERTEX_SHADER.contains("position.z * ${WATER_WAVE_Z_FREQUENCY} + uTime * ${WATER_WAVE_Z_TIME_SPEED}) * ${WATER_WAVE_Z_AMPLITUDE}"))
        assertTrue(WATER_VERTEX_SHADER.contains("0.8"))
        assertTrue(WATER_VERTEX_SHADER.contains("1.4"))
        assertTrue(WATER_VERTEX_SHADER.contains("0.07"))
        assertTrue(WATER_VERTEX_SHADER.contains("0.5"))
        assertTrue(WATER_VERTEX_SHADER.contains("1.0"))
        assertTrue(WATER_VERTEX_SHADER.contains("0.04"))
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
        listOf(
            "\${WATER_WAVE_X_FREQUENCY}",
            "\${WATER_WAVE_X_TIME_SPEED}",
            "\${WATER_WAVE_X_AMPLITUDE}",
            "\${WATER_WAVE_Z_FREQUENCY}",
            "\${WATER_WAVE_Z_TIME_SPEED}",
            "\${WATER_WAVE_Z_AMPLITUDE}"
        ).forEach { token -> assertTrue(rendererSource.contains(token)) }
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
        assertTrue(FRAGMENT_SHADER.contains("uniform bool uAlphaCutout;"))
        assertTrue(FRAGMENT_SHADER.contains("uniform float uTextureVScale;"))
        assertTrue(FRAGMENT_SHADER.contains("uniform bool uEmissive;"))
        assertTrue(FRAGMENT_SHADER.contains("texture(uTexture, uv)"))
        assertTrue(FRAGMENT_SHADER.contains("uv.y *= uTextureVScale"))
        assertTrue(!FRAGMENT_SHADER.contains("uMaterial"))
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
    fun textureEncodingsKeepColorAndDataTexturesDistinct() {
        assertEquals(org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8, TextureEncoding.SrgbColor.internalFormat)
        assertEquals(org.lwjgl.opengl.GL11.GL_RGBA8, TextureEncoding.LinearData.internalFormat)
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        assertTrue(rendererSource.contains("uploadClasspathTexture(path, TextureEncoding.SrgbColor)"))
        assertTrue(rendererSource.contains("\"assets/complementary/textures/cloud-water.png\",\n            TextureEncoding.LinearData"))
        assertTrue(rendererSource.contains("\"assets/complementary/textures/noise.png\",\n            TextureEncoding.LinearData"))
        assertTrue(rendererSource.contains("\"assets/minecraft/textures/environment/sun.png\",\n            TextureEncoding.SrgbColor"))
        assertTrue(rendererSource.contains("\"assets/minecraft/textures/environment/clouds.png\",\n            TextureEncoding.SrgbColor"))
        assertTrue(rendererSource.contains("uploadTexture(appearance.skin, TextureEncoding.SrgbColor)"))
        assertTrue(rendererSource.contains("uploadTexture(it, TextureEncoding.SrgbColor)"))
    }

    @Test
    fun staticShadowShaderExcludesDynamicGeometryAndKeepsCutouts() {
        assertTrue(SHADOW_VERTEX_SHADER.contains("layout(location = 3) in float aKind;"))
        assertTrue(SHADOW_VERTEX_SHADER.contains("layout(location = 4) in vec2 aUv;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("if (vKind != 0) discard;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("uniform bool uAlphaCutout;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("uniform float uTextureVScale;"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("uv.y *= uTextureVScale"))
        assertTrue(!SHADOW_FRAGMENT_SHADER.contains("uMaterial"))
        assertTrue(SHADOW_FRAGMENT_SHADER.contains("if (uAlphaCutout && texel.a < 0.1) discard;"))
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
        val draw = body.indexOf("glDrawElements")
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
        assertTrue(SKY_FRAGMENT_SHADER.contains("noonSkyDisplayColor(worldRay, normalize(uSunDirection)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("noonSkyDisplayColor(reflectedDirection"))
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
        assertTrue(HDR_COPY_FRAGMENT_SHADER.contains("color = vec4(max(raw, vec3(0.0)), 1.0)"))
        assertTrue(!HDR_COPY_FRAGMENT_SHADER.contains("2.2"))
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
    fun sceneUsesOneIndexedElementBufferForAllScenePasses() {
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        assertTrue(rendererSource.contains("indexBuffer = glGenBuffers()"))
        assertTrue(rendererSource.contains("glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer)"))
        assertTrue(rendererSource.contains("glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)"))
        assertTrue(rendererSource.contains("glDrawElements(GL_TRIANGLES, batch.indexCount"))
        assertTrue(rendererSource.contains("glDrawElements(GL_TRIANGLES, waterBatch.indexCount"))
        assertTrue(rendererSource.contains("glDeleteBuffers(indexBuffer)"))
    }

    @Test
    fun waterShaderUsesComplementaryNormalLayersFresnelNoiseAndSharedSun() {
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos = 0.032"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos + wind"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 4.0 - wind * 2.0"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 0.25 - wind * 0.5"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("waterPos * 0.05 - wind * 0.05"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("medium * 1.10 + small * 0.35 + big * 1.55 + extraBig * 0.85"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("0.27 * (1.0 - 0.60 * viewEdge)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("float waterDistance = length(vWorldPosition.xz - uEyePosition.xz);"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("float nearWater = 1.0 - smoothstep(18.0, 110.0, waterDistance);"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vec3(0.92, 0.98, 1.04)"))
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
        assertTrue(NOON_SKY_FUNCTIONS.contains("vec3 noonSkyDisplayColor"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vec3 noonSkyDisplayColor"))
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
        assertTrue(WATER_FRAGMENT_SHADER.contains("* vec3(1.0, 0.84, 0.62) * 0.08;"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("return min(highlight, vec3(1.8));"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("float reflectionLod = clamp(distanceLod + edgeLod, 0.0, 3.5);"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("textureLod(uReflectionColor, sampledReflectionUv, reflectionLod)"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("float reflectionStrength = clamp(0.06 + fresnel * 0.92, 0.0, 0.92);"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("vec3 result = mix(tintedWater, planarReflection, reflectionStrength);"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("fresnel * 0.78"))
        assertTrue(WATER_FRAGMENT_SHADER.contains("float solarFade = (1.0 - smoothstep(80.0, 180.0, waterDistance)) * (1.0 - 0.35 * viewEdge);"))
        assertTrue(!WATER_FRAGMENT_SHADER.contains("* vec3(1.0, 0.84, 0.62) * 0.32;"))
    }

    @Test
    fun reflectionUsesGeneratedMipmapsImmediatelyBeforeWaterSampling() {
        val rendererSource = java.io.File(
            "src/main/kotlin/calebxzau/rdi/bgrenderer/BackgroundRenderer.kt"
        ).readText()
        val reflectionBlock = rendererSource.substring(
            rendererSource.indexOf("glBindFramebuffer(GL_FRAMEBUFFER, reflectionFramebuffer)").also { check(it >= 0) },
            rendererSource.indexOf("fun bindForRender").also { check(it >= 0) }
        )
        assertTrue(reflectionBlock.contains("GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR"))
        assertTrue(reflectionBlock.contains("GL_TEXTURE_MAG_FILTER, GL_LINEAR"))
        assertTrue(reflectionBlock.contains("glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, reflectionColor, 0)"))
        assertTrue(rendererSource.contains("fun generateReflectionMipmaps()"))
        assertTrue(rendererSource.contains("val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)"))
        assertTrue(rendererSource.contains("val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)"))
        assertTrue(rendererSource.contains("glGenerateMipmap(GL_TEXTURE_2D)"))
        val reflection = rendererSource.indexOf("renderer.renderReflectionScene")
        val mainBind = rendererSource.indexOf("framebuffer.bindForRender()", reflection)
        val mipmap = rendererSource.indexOf("framebuffer.generateReflectionMipmaps()", mainBind)
        val water = rendererSource.indexOf("renderer.renderWater", mipmap)
        assertTrue(reflection >= 0 && mainBind > reflection && mipmap > mainBind && water > mipmap)
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
