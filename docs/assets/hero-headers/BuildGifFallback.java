import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class BuildGifFallback {
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 400;
    private static final int DELAY_CENTISECONDS = 800;
    private static final String[] FRAME_NAMES = {
        "ubs-hero-overview.png",
        "ubs-hero-bankops.png",
        "ubs-hero-mobile.png",
        "ubs-hero-shop.png",
        "ubs-hero-cash.png",
        "ubs-hero-claims.png",
        "ubs-hero-heist.png"
    };

    private BuildGifFallback() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Path output = directory.resolve("ubs-hero-carousel-fallback.gif");

        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No GIF ImageIO writer is available");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(Files.newOutputStream(output))) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < FRAME_NAMES.length; index++) {
                BufferedImage source = ImageIO.read(directory.resolve(FRAME_NAMES[index]).toFile());
                if (source == null) {
                    throw new IOException("Unable to read " + FRAME_NAMES[index]);
                }
                BufferedImage frame = resize(source);
                ImageWriteParam params = writer.getDefaultWriteParam();
                IIOMetadata metadata = metadata(writer, params, index == 0);
                writer.writeToSequence(new IIOImage(frame, null, metadata), params);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        System.out.println("Built " + output.getFileName() + " with " + FRAME_NAMES.length
            + " frames at " + (DELAY_CENTISECONDS / 100.0) + "s per frame");
    }

    private static BufferedImage resize(BufferedImage source) {
        BufferedImage target = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static IIOMetadata metadata(ImageWriter writer, ImageWriteParam params, boolean firstFrame)
            throws Exception {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(DELAY_CENTISECONDS));
        control.setAttribute("transparentColorIndex", "0");

        if (firstFrame) {
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
            extension.setAttribute("applicationID", "NETSCAPE");
            extension.setAttribute("authenticationCode", "2.0");
            extension.setUserObject(new byte[] {1, 0, 0});
            extensions.appendChild(extension);
        }

        metadata.setFromTree(format, root);
        return metadata;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int index = 0; index < root.getLength(); index++) {
            if (name.equals(root.item(index).getNodeName())) {
                return (IIOMetadataNode) root.item(index);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
