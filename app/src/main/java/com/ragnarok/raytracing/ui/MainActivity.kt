package com.ragnarok.raytracing.ui

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ragnarok.raytracing.R
import com.ragnarok.raytracing.renderer.RayTracingRenderer

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: GLSurfaceView

    private val renderer = RayTracingRenderer()

    private var renderLoopStart = false
    private val handler = Handler(Looper.getMainLooper()) {
        handleRenderMsg(it)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        actionBar?.hide()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        surfaceView = findViewById(R.id.surfaceview)

        surfaceView.setEGLContextClientVersion(3)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun handleRenderMsg(msg: Message) {
        if (msg.what == 1000 && renderLoopStart) {
            surfaceView.requestRender()
            handler.sendEmptyMessageDelayed(1000, 50)
        }
    }

    override fun onResume() {
        super.onResume()
        renderLoopStart = true
        handler.sendEmptyMessage(1000)
    }


    override fun onPause() {
        super.onPause()
        renderLoopStart = false
        handler.removeCallbacksAndMessages(null)
    }
}
