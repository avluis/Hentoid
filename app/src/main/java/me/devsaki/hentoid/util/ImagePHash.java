package me.devsaki.hentoid.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;


/*
 * pHash-like image hash.
 * Author: Elliot Shepherd (elliot@jarofworms.com
 * https://gist.github.com/kuFEAR/6e20342198d4040e0bb5
 * Based On: http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 */
public class ImagePHash {
    private int size = 32;
    private int smallerSize = 8;

    public ImagePHash() {
        initCoefficients();
    }

    public ImagePHash(int size, int smallerSize) {
        this.size = size;
        this.smallerSize = smallerSize;

        initCoefficients();
    }

    public static int distance(long hash1, long hash2) {

        long similarityMask = ~((hash1 | hash2) & (~(hash1 & hash2)));

        return Long.SIZE - Long.bitCount(similarityMask);
    }

    public static float similarity(long hash1, long hash2) {
        long similarityMask = ~((hash1 | hash2) & (~(hash1 & hash2)));

        return Long.bitCount(similarityMask) * 1f / Long.SIZE;
    }


    public long calcPHash(Bitmap img) {

        // Initializing coefficients
        initCoefficients();

        /* 1. Reduce size.
         * Like Average Hash, pHash starts with a small image.
         * However, the image is larger than 8x8; 32x32 is a good size.
         * This is really done to simplify the DCT computation and not
         * because it is needed to reduce the high frequencies.
         */
        img = resize(img, size, size);

        /* 2. Reduce color.
         * The image is reduced to a grayscale just to further simplify
         * the number of computations.
         */
        long hash = 0;

        if (img != null) {
            img = grayscale(img);

            double[][] vals = new double[size][size];

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    vals[x][y] = getBlue(img, x, y);
                }
            }

            /* 3. Compute the DCT.
             * The DCT separates the image into a collection of frequencies
             * and scalars. While JPEG uses an 8x8 DCT, this algorithm uses
             * a 32x32 DCT.
             */
            double[][] dctVals = applyDCT(vals);

            /* 4. Reduce the DCT.
             * This is the magic step. While the DCT is 32x32, just keep the
             * top-left 8x8. Those represent the lowest frequencies in the
             * picture.
             */
            /* 5. Compute the average value.
             * Like the Average Hash, compute the mean DCT value (using only
             * the 8x8 DCT low-frequency values and excluding the first term
             * since the DC coefficient can be significantly different from
             * the other values and will throw off the average).
             */
            double total = 0;

            for (int x = 0; x < smallerSize; x++) {
                for (int y = 0; y < smallerSize; y++) {
                    total += dctVals[x][y];
                }
            }
            total -= dctVals[0][0];

            double avg = total / (double) ((smallerSize * smallerSize) - 1);

            /* 6. Further reduce the DCT.
             * This is the magic step. Set the 64 hash bits to 0 or 1
             * depending on whether each of the 64 DCT values is above or
             * below the average value. The result doesn't tell us the
             * actual low frequencies; it just tells us the very-rough
             * relative scale of the frequencies to the mean. The result
             * will not vary as long as the overall structure of the image
             * remains the same; this can survive gamma and color histogram
             * adjustments without a problem.
             */


            for (int x = 0; x < smallerSize; x++) {
                for (int y = 0; y < smallerSize; y++) {
                    if (x != 0 && y != 0) {

                        hash *= 2;

                        if (dctVals[x][y] > avg)
                            hash++;

                    }
                }
            }

        } else {
            return 0;
        }

        return hash;
    }

    // TODO optimize by using renderscript
    public Bitmap resize(Bitmap bm, int newHeight, int newWidth) {
        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = null;
        try {
            resizedBitmap = Bitmap.createScaledBitmap(bm, newWidth, newHeight, false);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return resizedBitmap;
    }

    // TODO optimize by using renderscript
    // see https://stackoverflow.com/questions/31905350/renderscript-greyscale-not-quite-working, http://www.johndcook.com/blog/2009/08/24/algorithms-convert-color-grayscale/ and https://gist.github.com/imminent/cf4ab750104aa286fa08
    private Bitmap grayscale(Bitmap orginalBitmap) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);

        ColorMatrixColorFilter colorMatrixFilter = new ColorMatrixColorFilter(colorMatrix);

        Bitmap blackAndWhiteBitmap = orginalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        Paint paint = new Paint();
        paint.setColorFilter(colorMatrixFilter);

        Canvas canvas = new Canvas(blackAndWhiteBitmap);
        canvas.drawBitmap(blackAndWhiteBitmap, 0, 0, paint);

        return blackAndWhiteBitmap;
    }

    // TODO optimize by using renderscript
    private static int getBlue(Bitmap img, int x, int y) {
        return (img.getPixel(x, y)) & 0xff;
    }

    // DCT function stolen from http://stackoverflow.com/questions/4240490/problems-with-dct-and-idct-algorithm-in-java

    private double[] c;

    private void initCoefficients() {
        c = new double[size];

        for (int i = 1; i < size; i++) {
            c[i] = 1;
        }
        c[0] = 1 / Math.sqrt(2.0);
    }

    private double[][] applyDCT(double[][] f) {
        int N = size;

        double[][] F = new double[N][N];
        for (int u = 0; u < N; u++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        sum += Math.cos(((2 * i + 1) / (2.0 * N)) * u * Math.PI) * Math.cos(((2 * j + 1) / (2.0 * N)) * v * Math.PI) * (f[i][j]);
                    }
                }
                sum *= ((c[u] * c[v]) / 4.0);
                F[u][v] = sum;
            }
        }
        return F;
    }
}
