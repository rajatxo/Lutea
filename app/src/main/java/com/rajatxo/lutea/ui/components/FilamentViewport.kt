package com.rajatxo.lutea.ui.components

import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private const val TAG = "FilamentViewport"

/**
 * Renders a GLB model from `assets/` using Filament.
 *
 * Lifecycle contract:
 *  - One [Engine] per composable instance. Created lazily in the AndroidView factory,
 *    destroyed in [DisposableEffect] when the composable leaves composition.
 *  - All Filament resources (renderer, scene, view, camera, lights, swapchain,
 *    asset loader, material provider, asset, choreographer callback) are tracked
 *    in [FilamentResources] and torn down in reverse order on dispose.
 *  - The GLB is read off the UI thread, then Filament asset construction happens
 *    on the engine's owning thread (main), which is what Filament requires.
 *
 * If you don't see the model, check logcat for `FilamentViewport` — errors are
 * logged, never silently swallowed.
 */
@Composable
fun FilamentViewport(
    assetPath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val res = remember { FilamentResources() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // 1. Initialise Filament native libs BEFORE Engine.create().
            //    Utils.init() is idempotent.
            if (!res.utilsInitialized) {
                try {
                    Utils.init()
                    res.utilsInitialized = true
                } catch (t: Throwable) {
                    Log.e(TAG, "Utils.init() failed", t)
                }
            }

            // 2. Create the engine + render pipeline.
            //    All of these are null on first creation and reused if the
            //    AndroidView is re-instantiated while the composable is alive.
            val engine = res.engine ?: Engine.create().also { res.engine = it }
            val renderer = res.renderer ?: engine.createRenderer().also { res.renderer = it }
            val scene = res.scene ?: engine.createScene().also { res.scene = it }
            val view = res.view ?: engine.createView().also { res.view = it }
            val cameraEntity = res.cameraEntity.takeIf { it != 0 }
                ?: engine.entityManager.create().also { res.cameraEntity = it }
            val camera = res.camera ?: engine.createCamera(cameraEntity).also { res.camera = it }

            view.scene = scene
            view.camera = camera

            // 3. Lights — key + fill. Entities are tracked for cleanup.
            if (res.lightEntities.isEmpty()) {
                buildDirectionalLight(
                    engine, scene,
                    color = floatArrayOf(1.0f, 1.0f, 1.0f),
                    intensity = 50000.0f,
                    direction = floatArrayOf(0.3f, -0.6f, -0.8f),
                    castShadows = true,
                ).also { res.lightEntities.add(it) }
                buildDirectionalLight(
                    engine, scene,
                    color = floatArrayOf(0.5f, 0.55f, 0.6f),
                    intensity = 15000.0f,
                    direction = floatArrayOf(-0.5f, -0.3f, 0.4f),
                    castShadows = false,
                ).also { res.lightEntities.add(it) }
            }

            // 4. Default camera (overridden in surfaceChanged with the real aspect).
            camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
            camera.lookAt(0.0, 0.0, 3.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

            // 5. SurfaceView + holder callback.
            val surfaceView = SurfaceView(ctx).also { res.surfaceView = it }
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Drop any previous swapchain (e.g. surface re-created after config change).
                    res.swapChain?.let { engine.destroySwapChain(it) }
                    res.swapChain = engine.createSwapChain(holder.surface)
                    // Defer viewport sizing to surfaceChanged — width is often 0 here.
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    if (width <= 0 || height <= 0) return
                    view.viewport = Viewport(0, 0, width, height)
                    val aspect = width.toDouble() / height.toDouble()
                    camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    res.swapChain?.let { engine.destroySwapChain(it) }
                    res.swapChain = null
                }
            })

            // 6. Load GLB off the UI thread.
            //    Only the byte read happens on Dispatchers.IO — everything that
            //    touches Filament runs back on the engine's thread (main).
            if (res.asset == null && !res.loadStarted) {
                res.loadStarted = true
                scope.launch {
                    val buffer = withContext(Dispatchers.IO) {
                        try {
                            val bytes = context.assets.open(assetPath).use { it.readBytes() }
                            ByteBuffer.wrap(bytes)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to read GLB at $assetPath", t)
                            null
                        }
                    } ?: return@launch

                    // Filament operations — must happen on the engine's owning thread.
                    try {
                        val materialProvider = UbershaderProvider(engine)
                            .also { res.materialProvider = it }
                        val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
                            .also { res.assetLoader = it }
                        val asset = assetLoader.createAsset(buffer)
                        if (asset == null) {
                            Log.e(TAG, "AssetLoader.createAsset returned null for $assetPath")
                            return@launch
                        }
                        val resourceLoader = ResourceLoader(engine)
                        resourceLoader.loadResources(asset)
                        resourceLoader.destroy()
                        asset.releaseSourceData()
                        res.asset = asset
                        scene.addEntities(asset.entities)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to construct Filament asset for $assetPath", t)
                    }
                }
            }

            // 7. Render loop — Choreographer. Stops itself once [res.disposed] is set.
            val choreographer = Choreographer.getInstance()
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (res.disposed) return // stop the self-reposting loop
                    val sc = res.swapChain
                    val v = res.view
                    val r = res.renderer
                    if (sc != null && v != null && r != null) {
                        try {
                            r.beginFrame(sc, frameTimeNanos)
                            r.render(v)
                            r.endFrame()
                        } catch (t: Throwable) {
                            // Don't spam logcat every frame — log once then stop rendering.
                            Log.e(TAG, "Render frame failed, stopping loop", t)
                            res.disposed = true
                            return
                        }
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            res.choreographerCallback = callback
            choreographer.postFrameCallback(callback)

            surfaceView
        },
    )

    // 8. Full teardown when the composable leaves composition.
    //    Order matters: stop the loop → drop swapchain → drop asset → drop
    //    lights/camera → drop view/scene/renderer → drop engine.
    DisposableEffect(Unit) {
        onDispose {
            res.disposed = true
            res.choreographerCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
            res.choreographerCallback = null

            val engine = res.engine ?: return@onDispose

            res.swapChain?.let { engine.destroySwapChain(it) }
            res.swapChain = null

            res.asset?.let { asset ->
                res.assetLoader?.destroyAsset(asset)
            }
            res.asset = null

            res.assetLoader?.destroy()
            res.assetLoader = null

            res.materialProvider?.destroy()
            res.materialProvider = null

            res.lightEntities.forEach { e ->
                engine.destroyEntity(e)
                EntityManager.get().destroy(e)
            }
            res.lightEntities.clear()

            if (res.cameraEntity != 0) {
                engine.destroyCameraComponent(res.cameraEntity)
                EntityManager.get().destroy(res.cameraEntity)
                res.cameraEntity = 0
            }
            res.camera = null

            res.view?.let { engine.destroyView(it) }
            res.view = null

            res.scene?.let { engine.destroyScene(it) }
            res.scene = null

            res.renderer?.let { engine.destroyRenderer(it) }
            res.renderer = null

            try {
                engine.destroy()
            } catch (t: Throwable) {
                Log.e(TAG, "Engine.destroy() failed", t)
            }
            res.engine = null
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Builds a directional light, attaches it to the scene, returns its entity. */
private fun buildDirectionalLight(
    engine: Engine,
    scene: Scene,
    color: FloatArray,
    intensity: Float,
    direction: FloatArray,
    castShadows: Boolean,
): Int {
    val entity = engine.entityManager.create()
    LightManager.Builder(LightManager.Type.DIRECTIONAL)
        .color(color[0], color[1], color[2])
        .intensity(intensity)
        .direction(direction[0], direction[1], direction[2])
        .castShadows(castShadows)
        .build(engine, entity)
    scene.addEntity(entity)
    return entity
}

/** Holds every Filament resource created for one composable instance. */
private class FilamentResources {
    var utilsInitialized: Boolean = false
    var engine: Engine? = null
    var renderer: Renderer? = null
    var scene: Scene? = null
    var view: View? = null
    var camera: Camera? = null
    var cameraEntity: Int = 0
    var swapChain: SwapChain? = null
    var materialProvider: UbershaderProvider? = null
    var assetLoader: AssetLoader? = null
    var asset: FilamentAsset? = null
    val lightEntities: MutableList<Int> = mutableListOf()
    var choreographerCallback: Choreographer.FrameCallback? = null
    var surfaceView: SurfaceView? = null
    var loadStarted: Boolean = false
    var disposed: Boolean = false
}
