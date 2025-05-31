package me.devsaki.hentoid.gles_renderer.filter

/**
 * A more generalized 9x9 Gaussian blur filter
 * blurSize value ranging from 0.0 on up, with a default of 1.0
 */
private const val VERTEX_SHADER = """attribute vec4 position;
attribute vec4 inputTextureCoordinate;

const int GAUSSIAN_SAMPLES = 9;

uniform mediump float texelWidthOffset;
uniform mediump float texelHeightOffset;

varying mediump vec2 textureCoordinate;
varying mediump vec2 blurCoordinates[GAUSSIAN_SAMPLES];

void main()
{
	gl_Position = position;
	textureCoordinate = inputTextureCoordinate.xy;
	
	// Calculate the positions for the blur
	int multiplier = 0;
	mediump vec2 blurStep;
    mediump vec2 singleStepOffset = vec2(texelHeightOffset, texelWidthOffset);
    
	for (int i = 0; i < GAUSSIAN_SAMPLES; i++)
   {
		multiplier = (i - ((GAUSSIAN_SAMPLES - 1) / 2));
       // Blur in x (horizontal)
       blurStep = float(multiplier) * singleStepOffset;
	   blurCoordinates[i] = inputTextureCoordinate.xy + blurStep;
	}
}
"""

private const val FRAGMENT_SHADER = """uniform sampler2D inputImageTexture;

const lowp int GAUSSIAN_SAMPLES = 9;

varying highp vec2 textureCoordinate;
varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];

void main()
{
	lowp vec3 sum = vec3(0.0);
   lowp vec4 fragColor=texture2D(inputImageTexture,textureCoordinate);
	
    sum += texture2D(inputImageTexture, blurCoordinates[0]).rgb * 0.05;
    sum += texture2D(inputImageTexture, blurCoordinates[1]).rgb * 0.09;
    sum += texture2D(inputImageTexture, blurCoordinates[2]).rgb * 0.12;
    sum += texture2D(inputImageTexture, blurCoordinates[3]).rgb * 0.15;
    sum += texture2D(inputImageTexture, blurCoordinates[4]).rgb * 0.18;
    sum += texture2D(inputImageTexture, blurCoordinates[5]).rgb * 0.15;
    sum += texture2D(inputImageTexture, blurCoordinates[6]).rgb * 0.12;
    sum += texture2D(inputImageTexture, blurCoordinates[7]).rgb * 0.09;
    sum += texture2D(inputImageTexture, blurCoordinates[8]).rgb * 0.05;

	gl_FragColor = vec4(sum,fragColor.a);
}"""

class GPUImageGaussianBlurFilter(private var blurSize: Float) :
    GPUImageTwoPassTextureSamplingFilter(
        VERTEX_SHADER,
        FRAGMENT_SHADER,
        VERTEX_SHADER,
        FRAGMENT_SHADER
    ) {

    override fun onInitialized() {
        super.onInitialized()
        setBlurSize(blurSize)
    }

    override fun getVerticalTexelOffsetRatio(): Float {
        return blurSize
    }

    override fun getHorizontalTexelOffsetRatio(): Float {
        return blurSize
    }

    /**
     * A multiplier for the blur size, ranging from 0.0 on up, with a default of 1.0
     *
     * @param radius from 0.0 on up, default 1.0
     */
    fun setBlurSize(radius: Float) {
        // NB : default radius for this implementation is 5 (see https://github.com/BradLarson/GPUImage/issues/983)
        this.blurSize = radius / 5f
        runOnDraw(::initTexelOffsets)
    }
}