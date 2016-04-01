package me.devsaki.hentoid.enums;

/**
 * Created by DevSaki on 21/06/2014.
 * Image Quality enumerator
 */
public enum ImageQuality {
    LOW(50, 70), MEDIUM(100, 140), HIGH(200, 280);

    final int width;
    final int height;

    ImageQuality(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}