package me.devsaki.hentoid.gles_renderer.filter

import android.opengl.GLES20

abstract class GPUImageTwoPassTextureSamplingFilter(
    firstVertexShader: String, firstFragmentShader: String,
    secondVertexShader: String, secondFragmentShader: String
) : GPUImageTwoPassFilter(
    firstVertexShader,
    firstFragmentShader,
    secondVertexShader,
    secondFragmentShader
) {

    override fun onInit() {
        super.onInit()
        initTexelOffsets()
    }

    protected open fun initTexelOffsets() {
        var ratio = getHorizontalTexelOffsetRatio()
        var filter = getFilters()[0]
        var texelWidthOffsetLocation =
            GLES20.glGetUniformLocation(filter.getProgram(), "texelWidthOffset")
        var texelHeightOffsetLocation =
            GLES20.glGetUniformLocation(filter.getProgram(), "texelHeightOffset")
        filter.setFloat(texelWidthOffsetLocation, ratio / getOutputWidth())
        filter.setFloat(texelHeightOffsetLocation, 0f)
        ratio = getVerticalTexelOffsetRatio()
        filter = getFilters()[1]
        texelWidthOffsetLocation =
            GLES20.glGetUniformLocation(filter.getProgram(), "texelWidthOffset")
        texelHeightOffsetLocation =
            GLES20.glGetUniformLocation(filter.getProgram(), "texelHeightOffset")
        filter.setFloat(texelWidthOffsetLocation, 0f)
        filter.setFloat(texelHeightOffsetLocation, ratio / getOutputHeight())
    }

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        initTexelOffsets()
    }

    open fun getVerticalTexelOffsetRatio(): Float {
        return 1f
    }

    open fun getHorizontalTexelOffsetRatio(): Float {
        return 1f
    }
}