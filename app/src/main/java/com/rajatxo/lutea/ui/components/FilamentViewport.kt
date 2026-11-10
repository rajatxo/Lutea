package com.rajatxo.lutea.ui.components

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer

/**
 * Renders a 3D GLB model using Google's Filament PBR engine.
 */
@Composable
fun FilamentViewport(
    assetPath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val engine = remember { Engine.create() }
    val renderer = remember { engine.createRenderer() }
    val scene = remember { engine.createScene() }
    val view = remember { engine.createView() }
    val cameraEntity = remember { engine.entityManager.create() }
    val camera = remember { engine.createCamera(cameraEntity) }
    val swapChainRef = remember { arrayOfNulls<SwapChain>(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val surfaceView = SurfaceView(ctx)

            // UiHelper manages surface lifecycle
            val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: android.view.Surface) {
                    swapChainRef[0]?.let { engine.destroySwapChain(it) }
                    swapChainRef[0] = engine.createSwapChain(surface)
                    view.viewport = Viewport(0, 0, surfaceView.width, surfaceView.height)
                }

                override fun onDetachedFromSurface() {
                    swapChainRef[0]?.let {
                        engine.destroySwapChain(it)
                        swapChainRef[0] = null
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

            // Lighting — directional (key light)
            val keyLight = engine.entityManager.create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(50000.0f)
                .direction(0.3f, -0.6f, -0.8f)
                .castShadows(true)
                .build(engine, keyLight)
            scene.addEntity(keyLight)

            // Fill light
            val fillLight = engine.entityManager.create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.5f, 0.55f, 0.6f)
                .intensity(15000.0f)
                .direction(-0.5f, -0.3f, 0.4f)
                .build(engine, fillLight)
            scene.addEntity(fillLight)

            // Load GLB model
            try {
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                val buffer = ByteBuffer.wrap(bytes)

                val assetLoader = AssetLoader(engine, null)
                val asset = assetLoader.createAsset(buffer)
                if (asset != null) {
                    val resourceLoader = ResourceLoader(engine)
                    resourceLoader.loadResources(asset)
                    scene.addEntities(asset.entities)
                }
            } catch (e: Exception) {
                // Model load failed — ignore
            }

            // Camera position
            camera.setProjection(45.0, 1.0, 0.1, 100.0)
            camera.lookAt(0.0, 0.0, 3.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

            // Render loop
            val choreographer = Choreographer.getInstance()
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    swapChainRef[0]?.let { chain ->
                        renderer.beginFrame(chain, frameTimeNanos)
                        renderer.render(view)
                        renderer.endFrame()
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(frameCallback)

            surfaceView
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            // Filament cleanup
        }
    }
}
