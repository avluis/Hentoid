package me.devsaki.hentoid.gles_renderer.filter

import android.opengl.GLES20

class GPUImageResizeFilter(targetX: Int, targetY: Int) :
    GPUImageFilter(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER) {

    init {
        setTargetDimensions(floatArrayOf(targetX.toFloat(), targetY.toFloat()))
    }

    companion object {
        const val RESIZE_VERTEX_SHADER = """void main()
        {
            gl_TexCoord[0] = gl_MultiTexCoord0;         //center
            gl_Position = ftransform();
        }
"""

        const val RESIZE_FRAGMENT_SHADER = """#define FIX(c) max(abs(c), 1e-5);

        uniform sampler2D inputImageTexture;
        varying highp vec2 textureCoordinate;
        uniform mediump vec2 textureTargetSize;

        const mediump float PI = 3.1415926535897932384626433832795;

        mediump vec3 weight3(mediump float x)
        {
            const mediump float radius = 3.0;
            mediump vec3 sample = FIX(2.0 * PI * vec3(x - 1.5, x - 0.5, x + 0.5));

            // Lanczos3. Note: we normalize outside this function, so no point in multiplying by radius.
            return /*radius **/ sin(sample) * sin(sample / radius) / (sample * sample);
        }

        mediump vec3 pixel(mediump float xpos, mediump float ypos)
        {
            return texture2D(inputImageTexture, vec2(xpos, ypos)).rgb;
        }

        mediump vec3 line(mediump float ypos, mediump vec3 xpos1, mediump vec3 xpos2, mediump vec3 linetaps1, mediump vec3 linetaps2)
        {
            return
                pixel(xpos1.r, ypos) * linetaps1.r +
                pixel(xpos1.g, ypos) * linetaps2.r +
                pixel(xpos1.b, ypos) * linetaps1.g +
                pixel(xpos2.r, ypos) * linetaps2.g +
                pixel(xpos2.g, ypos) * linetaps1.b +
                pixel(xpos2.b, ypos) * linetaps2.b;
        }

        void main()
        {
            mediump vec2 stepxy = 1.0 / textureTargetSize.xy;
            mediump vec2 pos = textureCoordinate.xy + stepxy * 0.5;
            mediump vec2 f = fract(pos / stepxy);

            mediump vec3 linetaps1   = weight3(0.5 - f.x * 0.5);
            mediump vec3 linetaps2   = weight3(1.0 - f.x * 0.5);
            mediump vec3 columntaps1 = weight3(0.5 - f.y * 0.5);
            mediump vec3 columntaps2 = weight3(1.0 - f.y * 0.5);

            // make sure all taps added together is exactly 1.0, otherwise some
            // (very small) distortion can occur
            mediump float suml = dot(linetaps1, vec3(1.0)) + dot(linetaps2, vec3(1.0));
            mediump float sumc = dot(columntaps1, vec3(1.0)) + dot(columntaps2, vec3(1.0));
            linetaps1 /= suml;
            linetaps2 /= suml;
            columntaps1 /= sumc;
            columntaps2 /= sumc;

            mediump vec2 xystart = (-2.5 - f) * stepxy + pos;
            mediump vec3 xpos1 = vec3(xystart.x, xystart.x + stepxy.x, xystart.x + stepxy.x * 2.0);
            mediump vec3 xpos2 = vec3(xystart.x + stepxy.x * 3.0, xystart.x + stepxy.x * 4.0, xystart.x + stepxy.x * 5.0);

            gl_FragColor = vec4(
                line(xystart.y                 , xpos1, xpos2, linetaps1, linetaps2) * columntaps1.r +
                line(xystart.y + stepxy.y      , xpos1, xpos2, linetaps1, linetaps2) * columntaps2.r +
                line(xystart.y + stepxy.y * 2.0, xpos1, xpos2, linetaps1, linetaps2) * columntaps1.g +
                line(xystart.y + stepxy.y * 3.0, xpos1, xpos2, linetaps1, linetaps2) * columntaps2.g +
                line(xystart.y + stepxy.y * 4.0, xpos1, xpos2, linetaps1, linetaps2) * columntaps1.b +
                line(xystart.y + stepxy.y * 5.0, xpos1, xpos2, linetaps1, linetaps2) * columntaps2.b,
                1.0);
        }
"""
    }

    private var targetSizeLocation = 0
    private lateinit var targetSizeXY: FloatArray

    override fun onInit() {
        super.onInit()
        targetSizeLocation = GLES20.glGetUniformLocation(getProgram(), "textureTargetSize")
    }

    override fun onInitialized() {
        super.onInitialized()
        setTargetDimensions(targetSizeXY)
    }

    private fun setTargetDimensions(targetSizeXY: FloatArray) {
        this.targetSizeXY = targetSizeXY
        this.outputDimensions = Pair(targetSizeXY[0].toInt(), targetSizeXY[1].toInt())
        if (targetSizeLocation > 0) setFloatVec2(targetSizeLocation, targetSizeXY)
    }
}