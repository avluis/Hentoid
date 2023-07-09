package me.devsaki.hentoid.gles_renderer.filter

open class GPUImageTwoPassFilter(
    firstVertexShader: String, firstFragmentShader: String,
    secondVertexShader: String, secondFragmentShader: String
) : GPUImageFilterGroup(ArrayList()) {
    init {
        super.addFilter(GPUImageFilter(firstVertexShader, firstFragmentShader))
        super.addFilter(GPUImageFilter(secondVertexShader, secondFragmentShader))
    }
}