package com.faforever.neroxis.generator.texture;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.mask.IntegerMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.ImageUtil;
import com.faforever.neroxis.util.Pipeline;

import java.util.List;

public abstract class PbrTextureGenerator extends TextureGenerator {
    protected BooleanMask realLand;
    protected BooleanMask realPlateaus;
    protected FloatMask groundTexture;
    protected FloatMask groundAccentTexture;
    protected FloatMask debrisTexture;
    protected FloatMask plateauTexture;
    protected FloatMask slopesTexture;
    protected FloatMask underWaterTexture;
    protected FloatMask waterBeachTexture;
    protected FloatMask cliffAccentTexture;
    protected FloatMask roughnessModifierTexture;
    protected IntegerMask terrainType;
    protected FloatMask waterSurfaceShadows;

    @Override
    protected void setupTexturePipeline() {
        BooleanMask debris = slope.copyAsBooleanMask(.2f);
        BooleanMask cliff = slope.copyAsBooleanMask(.75f);
        BooleanMask extendedCliff = cliff.copy().inflate(2f);
        BooleanMask realWater = realLand.copy().invert();
        FloatMask waterBeach = realWater.copy().inflate(11)
                                        .subtract(realPlateaus)
                                        .subtract(slope.copyAsBooleanMask(.05f).invert())
                                        .add(realWater)
                                        .copyAsFloatMask(0, 1)
                                        .blur(4);
        FloatMask cliffMask = cliff.copyAsFloatMask(0, 1).blur(4).add(cliff, 1f).blur(2).add(cliff, 0.5f);

        int textureSize = generatorParameters.mapSize() + 1;
        int mapSize = generatorParameters.mapSize();

        BooleanMask realWaterMapSized = realWater.copy().setSize(mapSize);
        BooleanMask shadowsInWater = shadowsMask.copy().multiply(realWaterMapSized);
        shadows.setToValue(shadowsInWater, 1f);
        shadowsInWater.add(realLand.copy().setSize(mapSize), shadowsInWater.copy().inflate(6));
        shadows.subtract(realWaterMapSized,
                         shadowsInWater.copyAsFloatMask(0, 1).blur(6))
               .blur(1);
        waterSurfaceShadows = heightmap.copy()
                                       .clampMin(biome.waterSettings().elevation())
                                       .copyAsShadowMask(biome.lightingSettings().sunDirection())
                                       .inflate(0.5f)
                                       .resample(map.getSize() / 2)
                                       .copyAsFloatMask(1, 0)
                                       .blur(1);


        waterBeachTexture.setSize(textureSize)
                         .add(waterBeach)
                         .subtract(cliffMask);
        cliffAccentTexture.setSize(textureSize)
                          .add(0.2f)
                          .addPerlinNoise(mapSize / 32, 0.6f)
                          .clampMax(1f)
                          .setToValue(extendedCliff.copy().invert(), 0f)
                          .blur(2)
                          .addGaussianNoise(.05f);
        groundTexture.setSize(textureSize)
                     .add(1f)
                     .subtract(waterBeach)
                     .subtract(cliffMask)
                     .clampMin(0f);
        groundAccentTexture.setSize(textureSize)
                           .add(0.1f)
                           .addPerlinNoise(mapSize / 16, 0.5f)
                           .addPerlinNoise(mapSize / 6, 0.2f)
                           .addGaussianNoise(.05f)
                           .clampMax(1f)
                           .multiply(groundTexture)
                           .clampMin(0f);
        slopesTexture.init(slope)
                     .subtract(0.05f)
                     .multiply(4f)
                     .addPerlinNoise(mapSize / 40, .2f)
                     .addPerlinNoise(mapSize / 8, .1f)
                     .addGaussianNoise(.05f)
                     .subtract(0.2f)
                     .clampMax(1f)
                     .subtract(waterBeach)
                     .subtract(cliffMask.copy().subtract(0.2f).clampMin(0f));
        debrisTexture.init(cliff.copy().inflate(8), 0f, 1f)
                     .subtract(0.25f)
                     .addPerlinNoise(mapSize / 8, .2f)
                     .addGaussianNoise(.05f)
                     .setToValue(debris.copy().invert(), 0f)
                     .setToValue(realPlateaus, 0f)
                     .subtract(realWater.copyAsFloatMask(0, 1).blur(4))
                     .subtract(cliff, 0.9f)
                     .clampMin(0f)
                     .multiply(0.7f)
                     .blur(1);
        plateauTexture.setSize(textureSize)
                      .add(0.5f)
                      .addPerlinNoise(mapSize / 40, .4f)
                      .multiply(
                              heightmap.copy()
                                       .subtract(biome.waterSettings().elevation() + 3f)
                                       .multiply(0.5f)
                                       .clampMax(1f)
                                       .clampMin(0f))
                      .multiply(slope.copyAsBooleanMask(0.01f).deflate(4),
                                slope.copy().multiply(-2f).add(1f).clampMin(0f))
                      .clampMin(0f)
                      .blur(2)
                      .setToValue(cliff, 0f)
                      .blur(1)
                      .multiply(0.8f);
        underWaterTexture.init(realWater.deflate(1), 0f, .7f)
                         .add(scaledWaterDepth.copy().multiply(.3f))
                         .clampMax(1f)
                         .blur(1);
        roughnessModifierTexture.setSize(textureSize)
                                .add(0.5f)
                                .addPerlinNoise(mapSize / 70, .1f);

        // due to the heightmapsplatting we effectively don't see a difference in very high or low values,
        // so we compress the range a bit
        slopesTexture.multiply(0.6f).add(slopesTexture.copyAsBooleanMask(0.01f), 0.3f);

        texturesLowMask.setComponents(waterBeachTexture, cliffAccentTexture, groundTexture, groundAccentTexture);
        texturesHighMask.setComponents(slopesTexture, debrisTexture, plateauTexture, roughnessModifierTexture);

        setupTerrainType(mapSize);
    }

    protected void setupTerrainType(int mapSize) {
        terrainType.setSize(mapSize);

        List<Integer> terrainTypes = map.getBiome().terrainMaterials().terrainTypes();
        terrainType.add(terrainTypes.getFirst())
                   .setToValue(waterBeachTexture.setSize(mapSize).copyAsBooleanMask(.55f), terrainTypes.get(1))
                   .setToValue(cliffAccentTexture.setSize(mapSize).copyAsBooleanMask(.35f), terrainTypes.get(2))
                   .setToValue(groundTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(3))
                   .setToValue(groundAccentTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(4))
                   .setToValue(slopesTexture.setSize(mapSize).copyAsBooleanMask(.3f), terrainTypes.get(5))
                   .setToValue(debrisTexture.setSize(mapSize).copyAsBooleanMask(.3f), terrainTypes.get(6))
                   .setToValue(plateauTexture.setSize(mapSize).copyAsBooleanMask(.5f), terrainTypes.get(7))
                   .setToValue(underWaterTexture.setSize(mapSize).copyAsBooleanMask(.7f), terrainTypes.get(8))
                   .setToValue(underWaterTexture.setSize(mapSize).copyAsBooleanMask(.8f), terrainTypes.get(9));
    }

    @Override
    public void initialize(SCMap map, long seed, GeneratorParameters generatorParameters,
                           SymmetrySettings symmetrySettings, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, generatorParameters, symmetrySettings, terrainGenerator);
        this.map.setTerrainShaderPath(SCMap.PBR_SHADER_NAME);

        realLand = heightmap.copyAsBooleanMask(biome.waterSettings().elevation());
        realPlateaus = heightmap.copyAsBooleanMask(biome.waterSettings().elevation() + 5f);
        waterBeachTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "waterBeachTexture", true);
        cliffAccentTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "cliffAccentTexture", true);
        groundTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "groundTexture", true);
        groundAccentTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "groundAccentTexture", true);
        slopesTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "slopesTexture", true);
        debrisTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "debrisTexture", true);
        plateauTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "plateauTexture", true);
        underWaterTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "underWaterTexture", true);
        roughnessModifierTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "roughnessModifierTexture",
                                                 true);
        terrainType = new IntegerMask(1, random.nextLong(), symmetrySettings, "terrainType", true);
    }

    @Override
    public void setTextures() {
        Pipeline.await(texturesLowMask, texturesHighMask, terrainType,
                       normals, scaledWaterDepth, shadows, waterSurfaceShadows);
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "generateTextures", () -> {
            map.setTextureMasksScaled(map.getTextureMasksLow(), texturesLowMask.getFinalMask());
            map.setTextureMasksScaled(map.getTextureMasksHigh(), texturesHighMask.getFinalMask());
            map.setTerrainType(map.getTerrainType(), terrainType.getFinalMask());
            map.setMapwideTexture(
                    ImageUtil.getMapwideTexture(normals.getFinalMask(), scaledWaterDepth.getFinalMask(),
                                                shadows.getFinalMask()));
            map.setWaterShadowMap(map.getWaterShadowMap(), waterSurfaceShadows.getFinalMask());
        });
    }
}
