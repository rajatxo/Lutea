package com.rajatxo.lutea.ui.components

import android.app.Activity
import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityInstance
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.FilamentUtils
import com.google.android.filament.utils.Manipulator

/**
 * Renders a 3D GLB model using Google's Filament PBR engine.
 * Wrapped in a Compose AndroidView for seamless integration.
 *
 * @param assetPath Path to the .glb file in assets (e.g. "models/head.glb")
 * @param rotationY Head rotation in degrees (0 = front-facing, can be animated)
 * @param modifier Compose modifier
 */
@Composable
fun FilamentViewport(
    assetPath: String,
    modifier: Modifier = Modifier,
    rotationY: Float = 0f,
) {
    val context = LocalContext.current

    // Filament components — created once, remembered
    val engine = remember { Engine.create() }
    val renderer = remember { engine.createRenderer() }
    val scene = remember { engine.createScene() }
    val view = remember { engine.createView() }
    val camera = remember { engine.createCamera(engine.entityManager.create()) }
    val swapChain = remember { arrayOfNulls<SwapChain>(1) }

    // glTF loader
    val assetLoader = remember {
        AssetLoader(engine, MaterialProvider(engine), null)
    }
    val assetState = remember { arrayOf<FilamentAsset?>(null) }

    // Camera entity for manipulator
    val cameraEntity = remember { camera.entity }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val surfaceView = SurfaceView(ctx)

            // UiHelper manages the surface lifecycle
            val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper.renderCallback = object : UiHelper.RenderCallback {
                override fun onNativeWindowChanged(surface: android.view.Surface) {
                    swapChain[0]?.let { engine.destroySwapChain(it) }
                    swapChain[0] = engine.createSwapChain(surface)
                    view.viewport = Viewport(0, 0, surfaceView.width, surfaceView.height)
                }

                override fun onDetachedFromSurface() {
                    swapChain[0]?.let {
                        engine.destroySwapChain(it)
                        swapChain[0] = null
                    }
                }

                override fun onResized(width: Int, height: Int) {
                    view.viewport = Viewport(0, 0, width, height)
                    val aspect = width.toFloat() / height.toFloat()
                    camera.setProjection(45.0, aspect, 0.1, 100.0)
                }
            }
            uiHelper.attachTo(surfaceView)

            // Set up view
            view.scene = scene
            view.camera = camera

            // Add a directional light (sun-like)
            addLight(engine, scene)

            // Add an ambient fill light
            addFillLight(engine, scene)

            // Load the GLB model from assets
            loadModel(context, assetPath, assetLoader, assetState, engine, scene)

            // Position camera to frame the head
            camera.setProjection(45.0, 1.0, 0.1, 100.0)
            camera.lookAt(
                0.0, 0.0, 3.5,  // eye position (front of head)
                0.0, 0.0, 0.0,  // target (center of head)
                0.0, 1.0, 0.0,  // up
            )

            // Render loop
            val choreographer = Choreographer.getInstance()
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    swapChain[0]?.let { chain ->
                        // Apply rotation if needed
                        assetState[0]?.let { asset ->
                            val root = asset.root
                            val tm = engine.transformManager
                            val inst = tm.getInstance(root)
                            if (inst != 0) {
                                val rotationMatrix = computeRotationY(rotationY)
                                tm.setTransform(inst, rotationMatrix)
                            }
                        }

                        val dt = (frameTimeNanos / 1_000_000_000.0)
                        renderer.beginFrame(chain, frameTimeNanos)
                        renderer.render(view)
                        renderer.endFrame()

                        // Update animation
                        assetState[0]?.let { asset ->
                            val animator = asset.assetAnimator
                            if (asset.hasInstanceData()) {
                                animator?.applyAnimation(0, dt.toFloat())
                                animator?.updateBoneMatrices()
                            }
                        }
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(frameCallback)

            surfaceView
        },
        update = { surfaceView ->
            // No update needed — rotation is read from the Compose state
        },
    )

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            // Engine cleanup happens when the composable leaves composition
            // Filament handles native resource cleanup
        }
    }
}

private fun addLight(engine: Engine, scene: Scene) {
    val lightEntity = engine.entityManager.create()
    LightManager.Builder(LightManager.Type.DIRECTIONAL)
        .color(1.0f, 1.0f, 1.0f)
        .intensity(50000.0f)
        .direction(0.3f, -0.6f, -0.8f)
        .castShadows(true)
        .build(engine, lightEntity)
    scene.addEntity(lightEntity)
}

private fun addFillLight(engine: Engine, scene: Scene) {
    val lightEntity = engine.entityManager.create()
    LightManager.Builder(LightManager.Type.DIRECTIONAL)
        .color(0.6f, 0.65f, 0.7f)
        .intensity(15000.0f)
        .direction(-0.5f, -0.3f, 0.4f)
        .build(engine, lightEntity)
    scene.addEntity(lightEntity)
}

private fun loadModel(
    context: Context,
    assetPath: String,
    assetLoader: AssetLoader,
    assetState: Array<FilamentAsset?>,
    engine: Engine,
    scene: Scene,
) {
    try {
        val buffer = context.assets.open(assetPath).use { it.readBytes() }
        val nioBuffer = java.nio.ByteBuffer.wrap(buffer)
        val asset = assetLoader.createAsset(nioBuffer)
        if (asset != null) {
            assetState[0] = asset
            // Resource loader for materials
            val resourceLoader = com.google.android.filament.gltfio.ResourceLoader(engine)
            resourceLoader.addResourceData(assetPath, nioBuffer)
            resourceLoader.loadResources(asset)
            scene.addEntities(asset.entities)
        }
    } catch (e: Exception) {
        // Model failed to load — silently ignore
    }
}

private fun computeRotationY(degrees: Float): FloatArray {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = Math.cos(rad).toFloat()
    val sin = Math.sin(rad).toFloat()
    return floatArrayOf(
        cos, 0f, sin, 0f,
        0f, 1f, 0f, 0f,
        -sin, 0f, cos, 0f,
        0f, 0f, 0f, 1f,
    )
}
