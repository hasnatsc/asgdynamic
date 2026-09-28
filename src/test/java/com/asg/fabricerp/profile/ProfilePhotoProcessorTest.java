package com.asg.fabricerp.profile;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilePhotoProcessorTest {

    private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor();

    @Test
    void aLargePhotoBecomesA512SquareAndA96Thumbnail_bothJpeg_andMuchSmaller() throws IOException {
        byte[] upload = encode(noisy(2400, 1600), "jpeg");

        ProfilePhotoProcessor.Photo photo = processor.process(upload);

        BufferedImage large = decode(photo.image());
        BufferedImage thumb = decode(photo.thumbnail());
        assertThat(large.getWidth()).isEqualTo(512);
        assertThat(large.getHeight()).isEqualTo(512);
        assertThat(thumb.getWidth()).isEqualTo(96);
        assertThat(thumb.getHeight()).isEqualTo(96);
        assertThat(ProfilePhotoProcessor.sniff(photo.image())).isEqualTo("jpeg");
        assertThat(ProfilePhotoProcessor.sniff(photo.thumbnail())).isEqualTo("jpeg");
        assertThat(photo.image().length).isLessThan(upload.length / 4);
        assertThat(photo.thumbnail().length).isLessThan(photo.image().length);
    }

    @Test
    void aSmallPhotoIsCroppedSquareButNotEnlarged() throws IOException {
        ProfilePhotoProcessor.Photo photo = processor.process(encode(solid(300, 200, Color.BLUE), "jpeg"));

        assertThat(photo.width()).isEqualTo(200);
        assertThat(photo.height()).isEqualTo(200);
        assertThat(decode(photo.thumbnail()).getWidth()).isEqualTo(96);
    }

    @Test
    void theCropIsTheCentre() throws IOException {
        // Red bands left and right, green in the middle: a centred square is all green.
        BufferedImage img = solid(300, 100, Color.RED);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(100, 0, 100, 100);
        g.dispose();

        BufferedImage out = decode(processor.process(encode(img, "png")).image());

        Color centre = new Color(out.getRGB(50, 50));
        Color edge = new Color(out.getRGB(2, 50));
        assertThat(centre.getGreen()).isGreaterThan(200);
        assertThat(edge.getGreen()).isGreaterThan(200);
        assertThat(edge.getRed()).isLessThan(60);
    }

    @Test
    void transparencyBecomesWhite_notBlack() throws IOException {
        BufferedImage clear = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);   // fully transparent

        BufferedImage out = decode(processor.process(encode(clear, "png")).image());

        Color c = new Color(out.getRGB(60, 60));
        assertThat(c.getRed()).isGreaterThan(240);
        assertThat(c.getGreen()).isGreaterThan(240);
        assertThat(c.getBlue()).isGreaterThan(240);
    }

    @Test
    void refusesWhatIsNotAPicture_whateverItIsCalled() {
        assertThatThrownBy(() -> processor.process("<svg onload=alert(1)></svg>".getBytes()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a picture");
        assertThatThrownBy(() -> processor.process(new byte[0]))
            .isInstanceOf(IllegalArgumentException.class);
        // A JPEG signature on garbage is caught when it is decoded.
        assertThatThrownBy(() -> processor.process(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesATinyPicture_andAnOversizedUpload() throws IOException {
        assertThatThrownBy(() -> processor.process(encode(solid(40, 40, Color.GRAY), "png")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 64");

        byte[] huge = new byte[ProfilePhotoProcessor.MAX_UPLOAD_BYTES + 1];
        huge[0] = (byte) 0xFF; huge[1] = (byte) 0xD8; huge[2] = (byte) 0xFF;
        assertThatThrownBy(() -> processor.process(huge))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 MB");
    }

    @Test
    void refusesADecompressionBomb_fromItsHeaderAlone() throws IOException {
        // A PNG header claiming 20000 × 20000: rejected before any pixel is allocated.
        byte[] png = encode(solid(100, 100, Color.GRAY), "png");
        // IHDR width/height live at bytes 16..23.
        writeInt(png, 16, 20_000);
        writeInt(png, 20, 20_000);

        assertThatThrownBy(() -> processor.process(png))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no larger than");
    }

    @Test
    void readsTheExifOrientation_andTurnsThePictureUpright() {
        byte[] exif = jpegWithOrientation(6, false);
        assertThat(ProfilePhotoProcessor.exifOrientation(exif)).isEqualTo(6);
        assertThat(ProfilePhotoProcessor.exifOrientation(jpegWithOrientation(3, true))).isEqualTo(3);
        assertThat(ProfilePhotoProcessor.exifOrientation(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEqualTo(1);

        // 6 = rotate a quarter turn clockwise: a wide picture comes out tall, its left edge on top.
        BufferedImage wide = solid(200, 100, Color.WHITE);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 20, 100);                        // black stripe down the left edge
        g.dispose();

        BufferedImage upright = ProfilePhotoProcessor.orient(wide, 6);

        assertThat(upright.getWidth()).isEqualTo(100);
        assertThat(upright.getHeight()).isEqualTo(200);
        assertThat(new Color(upright.getRGB(50, 5)).getRed()).isLessThan(40);         // stripe now across the top
        assertThat(new Color(upright.getRGB(50, 190)).getRed()).isGreaterThan(200);
    }

    // ------------------------------------------------------------------------------ fixtures

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** Detail a photo has, so the size comparison means something. */
    private static BufferedImage noisy(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = (x * 255 / w) << 16 | (y * 255 / h) << 8 | 128;
                img.setRGB(x, y, base ^ random.nextInt(0x101010));
            }
        }
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static void writeInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24); b[at + 1] = (byte) (v >>> 16); b[at + 2] = (byte) (v >>> 8); b[at + 3] = (byte) v;
    }

    /** SOI, then an APP1 Exif segment whose IFD0 holds just the orientation tag. */
    private static byte[] jpegWithOrientation(int orientation, boolean littleEndian) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (littleEndian) {
            tiff.writeBytes(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0});        // header, IFD0 at 8
            tiff.writeBytes(new byte[]{1, 0});                               // one entry
            tiff.writeBytes(new byte[]{0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0});
        } else {
            tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
            tiff.writeBytes(new byte[]{0, 1});
            tiff.writeBytes(new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
        }
        tiff.writeBytes(new byte[]{0, 0, 0, 0});                             // no next IFD
        byte[] body = tiff.toByteArray();
        int length = 2 + 6 + body.length;
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        jpeg.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        jpeg.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        jpeg.writeBytes(body);
        jpeg.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD9});
        return jpeg.toByteArray();
    }
}
