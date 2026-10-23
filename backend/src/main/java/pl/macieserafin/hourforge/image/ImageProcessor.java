package pl.macieserafin.hourforge.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Turns an uploaded picture into the only form that is stored: a 256x256 JPEG drawn from the decoded pixels. Nothing
 * of the original file survives (no EXIF location, no comments, no trailing data), and the size is checked before
 * the pixels are decoded, so a small file that claims a huge image cannot exhaust memory.
 */
public final class ImageProcessor {
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    static final int MAX_SIDE = 4096;
    static final int SIZE = 256;
    private static final Set<String> FORMATS = Set.of("jpeg", "png");

    /** The upload is not a JPEG or PNG picture of an acceptable size. */
    public static final class InvalidImageException extends Exception {
        InvalidImageException(String message) {
            super(message);
        }
    }

    private ImageProcessor() {
    }

    public static byte[] squareJpeg(byte[] upload) throws InvalidImageException {
        if (upload == null || upload.length == 0) throw new InvalidImageException("No picture");
        if (upload.length > MAX_UPLOAD_BYTES) throw new InvalidImageException("The picture is larger than 2 MB");
        var source = decode(upload);
        int side = Math.min(source.getWidth(), source.getHeight());
        // The centre square; the browser already crops, this only guards other clients.
        var square = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        return encode(scale(square));
    }

    private static BufferedImage decode(byte[] upload) throws InvalidImageException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            var readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) throw new InvalidImageException("Not a picture");
            var reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw new InvalidImageException("Only JPEG and PNG");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) throw new InvalidImageException("At most 4096 x 4096 pixels");
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new InvalidImageException("The picture cannot be read");
        }
    }

    /** Halves the size while it is more than twice too big, then draws the final size: smooth without a library. */
    private static BufferedImage scale(BufferedImage square) {
        var current = square;
        while (current.getWidth() >= SIZE * 2) current = draw(current, current.getWidth() / 2);
        return draw(current, SIZE);
    }

    /** Draws onto an opaque white RGB canvas, which also removes transparency (JPEG has none). */
    private static BufferedImage draw(BufferedImage image, int size) {
        var target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var out = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException exception) {
            throw new IllegalStateException("JPEG encoding failed", exception);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
