package me.devsaki.hentoid.gpu_render.filter

abstract class GPUImageTwoPassFilter(
    firstVertexShader: String, firstFragmentShader: String,
    secondVertexShader: String, secondFragmentShader: String
) : GPUImageFilterGroup(ArrayList()) {
    init {
        super.addFilter(GPUImageFilter(firstVertexShader, firstFragmentShader))
        super.addFilter(GPUImageFilter(secondVertexShader, secondFragmentShader))
    }
}