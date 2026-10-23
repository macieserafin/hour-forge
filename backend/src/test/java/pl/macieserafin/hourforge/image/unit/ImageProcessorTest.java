package pl.macieserafin.hourforge.image.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.image.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    @Test
    void makesASquare256PixelJpegFromTheCentreOfAnyPicture() throws Exception {
        var wide = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
        var graphics = wide.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(200, 0, 600, 600);
        graphics.dispose();

        var result = ImageIO.read(new ByteArrayInputStream(ImageProcessor.squareJpeg(encode(wide, "png"))));

        assertThat(result.getWidth()).isEqualTo(256);
        assertThat(result.getHeight()).isEqualTo(256);
        // The centre square was red; the transparent sides were cut off.
        var centre = new Color(result.getRGB(128, 128));
        assertThat(centre.getRed()).isGreaterThan(200);
        assertThat(centre.getGreen()).isLessThan(60);
    }

    @Test
    void keepsNothingOfTheOriginalFileButThePixels() throws Exception {
        var jpeg = encode(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "jpeg");
        // An EXIF segment right after the start marker, and a script appended after the end of the image.
        var exif = "Exif\0\0GPS 52.2297N 21.0122E".getBytes(StandardCharsets.ISO_8859_1);
        var upload = new ByteArrayOutputStream();
        upload.write(jpeg, 0, 2);
        upload.write(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        upload.write(exif);
        upload.write(jpeg, 2, jpeg.length - 2);
        upload.write("<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII));

        var stored = new String(ImageProcessor.squareJpeg(upload.toByteArray()), StandardCharsets.ISO_8859_1);

        assertThat(stored).doesNotContain("GPS").doesNotContain("Exif").doesNotContain("<script>");
    }

    @Test
    void refusesWhatIsNotAJpegOrPngPicture() throws Exception {
        assertRefused(new byte[0]);
        assertRefused("not a picture".getBytes(StandardCharsets.US_ASCII));
        assertRefused("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.US_ASCII));
        assertRefused(encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "gif"));
        assertRefused(new byte[ImageProcessor.MAX_UPLOAD_BYTES + 1]);
    }

    @Test
    void refusesHugeDimensionsBeforeDecodingThePixels() throws Exception {
        // A small file that would need hundreds of megabytes once decoded.
        var huge = encode(new BufferedImage(5000, 5000, BufferedImage.TYPE_BYTE_BINARY), "png");
        assertThat(huge.length).isLessThan(ImageProcessor.MAX_UPLOAD_BYTES);
        assertRefused(huge);
    }

    private static void assertRefused(byte[] upload) {
        assertThatThrownBy(() -> ImageProcessor.squareJpeg(upload)).isInstanceOf(ImageProcessor.InvalidImageException.class);
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        var out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }
}
