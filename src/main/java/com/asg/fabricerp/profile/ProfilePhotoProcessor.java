package com.asg.fabricerp.profile;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded picture into a profile photo - never stores what was uploaded:
 * <ol>
 *   <li><b>checks what it is</b> by its first bytes, not by the name or type the browser claims:
 *       JPEG, PNG, GIF or BMP;</li>
 *   <li><b>refuses a decompression bomb</b> by reading the dimensions before any pixels;</li>
 *   <li><b>turns it upright</b> as the camera's EXIF orientation says (a phone photo is often stored
 *       sideways), and lays transparency on white;</li>
 *   <li><b>crops it square</b> on the centre and scales it down in halving steps (sharp, without the
 *       shimmer of one big jump) to 512 px, and to a 96 px thumbnail for the header;</li>
 *   <li><b>re-encodes both as JPEG</b> - which also drops everything the camera wrote into the file,
 *       location included.</li>
 * </ol>
 * A 5 MB phone photo comes out at a few tens of kilobytes.
 */
@Component
public class ProfilePhotoProcessor {

    /** The most that is accepted from the browser (it shrinks pictures itself before sending). */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    public static final int SIZE = 512;
    public static final int THUMB = 96;
    static final int MAX_SIDE = 10_000;
    static final long MAX_PIXELS = 40_000_000L;
    static final int MIN_SIDE = 64;

    public record Photo(byte[] image, byte[] thumbnail, int width, int height) { }

    public Photo process(byte[] upload) {
        if (upload == null || upload.length == 0) throw new IllegalArgumentException("Choose a picture to upload");
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The picture is %.1f MB; the most accepted is 5 MB".formatted(upload.length / 1048576.0));
        }
        String kind = sniff(upload);
        if (kind == null) {
            throw new IllegalArgumentException("That file is not a picture this can read: use a JPEG or PNG photo");
        }
        BufferedImage source = decode(upload);
        if (source.getWidth() < MIN_SIDE || source.getHeight() < MIN_SIDE) {
            throw new IllegalArgumentException("The picture is %d × %d; use one at least %d px on each side"
                .formatted(source.getWidth(), source.getHeight(), MIN_SIDE));
        }
        BufferedImage upright = orient(flatten(source), "jpeg".equals(kind) ? exifOrientation(upload) : 1);
        BufferedImage square = cropSquare(upright);
        BufferedImage large = scale(square, Math.min(SIZE, square.getWidth()));
        BufferedImage small = scale(large, THUMB);
        return new Photo(jpeg(large, 0.86f, true), jpeg(small, 0.82f, false), large.getWidth(), large.getHeight());
    }

    // ------------------------------------------------------------------------------ recognising

    /** What the bytes are, by their signature: jpeg, png, gif, bmp - or null. */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') return "bmp";
        return null;
    }

    /** Decodes the first image, after checking its size from the header alone. */
    static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IllegalArgumentException("That picture cannot be read");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
                    throw new IllegalArgumentException("The picture is %d × %d; use one no larger than %d px on a side"
                        .formatted(w, h, MAX_SIDE));
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("That picture cannot be read");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("That picture is damaged or incomplete; try saving it again");
        }
    }

    /**
     * The EXIF orientation (1-8) of a JPEG, or 1. Reads only the APP1 "Exif" segment's IFD0 - enough
     * for the one tag, without a metadata library.
     */
    static int exifOrientation(byte[] b) {
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marker = b[i + 1] & 0xFF;
            int length = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marker == 0xDA || length < 2) break;                       // start of scan: no more headers
            if (marker == 0xE1 && i + 10 <= b.length && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i' && b[i + 7] == 'f') {
                int tiff = i + 10;
                if (tiff + 8 > b.length) return 1;
                boolean little = b[tiff] == 'I';
                int ifd = tiff + read(b, tiff + 4, 4, little);
                if (ifd + 2 > b.length || ifd < tiff) return 1;
                int entries = read(b, ifd, 2, little);
                for (int e = 0; e < entries; e++) {
                    int entry = ifd + 2 + e * 12;
                    if (entry + 12 > b.length) return 1;
                    if (read(b, entry, 2, little) == 0x0112) {
                        int value = read(b, entry + 8, 2, little);
                        return value >= 1 && value <= 8 ? value : 1;
                    }
                }
                return 1;
            }
            i += 2 + length;
        }
        return 1;
    }

    private static int read(byte[] b, int at, int bytes, boolean little) {
        int v = 0;
        for (int k = 0; k < bytes; k++) {
            int part = b[at + k] & 0xFF;
            v = little ? v | (part << (8 * k)) : (v << 8) | part;
        }
        return v;
    }

    // ---------------------------------------------------------------------------- transforming

    /** An opaque RGB copy: transparent areas become white, not black. */
    static BufferedImage flatten(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Applies an EXIF orientation: 3 is upside down, 6 and 8 are a quarter turn, 2/4/5/7 mirrored. */
    static BufferedImage orient(BufferedImage img, int orientation) {
        if (orientation <= 1 || orientation > 8) return img;
        int w = img.getWidth(), h = img.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> { }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    /** The largest centred square. */
    static BufferedImage cropSquare(BufferedImage img) {
        int side = Math.min(img.getWidth(), img.getHeight());
        return img.getSubimage((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side);
    }

    /** Down to {@code target} px square, halving while more than twice too big, then one bicubic step. */
    static BufferedImage scale(BufferedImage img, int target) {
        BufferedImage current = img;
        int side = img.getWidth();
        while (side / 2 >= target && side > target) {
            side /= 2;
            current = draw(current, side);
        }
        return side == target ? copyIfShared(current, img) : draw(current, target);
    }

    private static BufferedImage copyIfShared(BufferedImage current, BufferedImage original) {
        return current == original ? draw(original, original.getWidth()) : current;
    }

    private static BufferedImage draw(BufferedImage src, int side) {
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, side, side, null);
        g.dispose();
        return out;
    }

    /** Baseline or progressive JPEG at {@code quality}, with no metadata at all. */
    static byte[] jpeg(BufferedImage img, float quality, boolean progressive) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            if (progressive) param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("The photo could not be encoded", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
