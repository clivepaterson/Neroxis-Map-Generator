package com.faforever.neroxis.toolsuite;

import com.faforever.neroxis.cli.OutputFolderMixin;
import com.faforever.neroxis.cli.VersionProvider;
import com.faforever.neroxis.map.Symmetry;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.mask.Vector4Mask;
import com.faforever.neroxis.util.ImageUtil;
import picocli.CommandLine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(name = "generate-pbr", mixinStandardHelpOptions = true, 
        description = "Generate the pbr texture from individual height and roughness textures", 
        versionProvider = VersionProvider.class, usageHelpAutoWidth = true)
public class PbrTextureGenerator implements Callable<Integer> {
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;
    @CommandLine.Mixin
    private OutputFolderMixin outputFolderMixin;
    private Integer textureImageSize;

    @CommandLine.Option(names = "--size", defaultValue = "1024", description = "Size of the input textures in pixels. Defaults to 1024.")
    public void setTextureImageSize(int size) {
        if (!isPowerOfTwo(size)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Texture size must be a power of two!");
        }
        textureImageSize = size;
    }

    @Override
    public Integer call() throws Exception {
        generatePbrTexture();
        return 0;
    }
    
    SymmetrySettings noSymmetry = new SymmetrySettings(Symmetry.NONE);

    public boolean isPowerOfTwo(int number) {
        if(number <=0){
            return false;
        }
        if ((number & -number) == number) {
            return true;
        }
        return false;
    }
    
    public void generatePbrTexture() throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputFolderMixin.getOutputPath())) {
            int pbrTextureSize = textureImageSize * 4;
            int offset = pbrTextureSize / 2;
            Vector4Mask pbrMask = new Vector4Mask(pbrTextureSize, 0L, noSymmetry);
            BufferedImage pbrTexture = new BufferedImage(pbrTextureSize, pbrTextureSize, BufferedImage.TYPE_INT_ARGB);
            int filesProcessed = 0;
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    Pattern pattern = Pattern.compile("\\d+");
                    Matcher matcher = pattern.matcher(path.toFile().getName());
                    if (matcher.find()) {
                        BufferedImage image = ImageIO.read(path.toFile());
                        String numberStr = matcher.group();
                        int layer = Integer.parseInt(numberStr);
                        if (path.getFileName().toString().toLowerCase().startsWith("roughness")) {
                            System.out.printf("Writing roughness texture %s\n", path.getFileName());
                            FloatMask roughness = createOffsetMaskFromImage(image);
                            if (image.getType() == BufferedImage.TYPE_USHORT_GRAY) {
                                roughness.divide(256f);
                            } else if (image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                                throw new RuntimeException("Unsupported image type! " +
                                        "The image must be a single channel grayscale texture. " +
                                        "No rgb or alpha channel allowed.");
                            }
                            int component = (layer >= 4) ? 3 : 1;
                            int xOffset = (layer % 2 == 1) ? offset : 0;
                            int yOffset = (layer % 4 >= 2) ? offset : 0;
                            pbrMask.setComponentWithOffset(roughness, component, xOffset, yOffset, false, false);
                            filesProcessed++;
                        } else if (path.getFileName().toString().toLowerCase().startsWith("height")) {
                            System.out.printf("Writing height texture %s\n", path.getFileName());
                            FloatMask height = createOffsetMaskFromImage(image);
                            if (image.getType() == BufferedImage.TYPE_USHORT_GRAY) {
                                height.divide(256f);
                            }
                            int component = (layer >= 4) ? 2 : 0;
                            int xOffset = (layer % 2 == 1) ? offset : 0;
                            int yOffset = (layer % 4 >= 2) ? offset : 0;
                            pbrMask.setComponentWithOffset(height, component, xOffset, yOffset, false, false);
                            filesProcessed++;
                        }
                    }
                }
            }
            if (filesProcessed == 0) {
                throw new RuntimeException("No files found to write into the pbr texture. " +
                        "The files need to be named 'RoughnessX' or 'HeightX' where X is the number " +
                        "that specifies the texture layer.");
            }
            pbrMask.writeToImage(pbrTexture);
            Path textureDirectory = outputFolderMixin.getOutputPath();
            Path filePath = textureDirectory.resolve("heightRoughness.dds");
            System.out.printf("Processed %d files.\n", filesProcessed);
            System.out.print("Compressing dds texture. This might take a while...\n");
            ImageUtil.writeCompressedDDS(pbrTexture, filePath);
            System.out.print("Successfully wrote dds output\n");
        }
    }

    private FloatMask createOffsetMaskFromImage(BufferedImage image) {
        // We need to write the texture with padding. We can achieve that by offsetting it and writing it in a 2x2 grid
        FloatMask mask = new FloatMask(image, 0L, noSymmetry);
        FloatMask roughness = new FloatMask(mask.getSize() * 2, 0L, noSymmetry);
        roughness.setWithOffset(mask, (int) (mask.getSize() * 0.5), (int) (mask.getSize() * 0.5), false, true);
        roughness.setWithOffset(mask, (int) (mask.getSize() * 1.5), (int) (mask.getSize() * 0.5), false, true);
        roughness.setWithOffset(mask, (int) (mask.getSize() * 0.5), (int) (mask.getSize() * 1.5), false, true);
        roughness.setWithOffset(mask, (int) (mask.getSize() * 1.5), (int) (mask.getSize() * 1.5), false, true);
        return roughness;
    }
}
