package com.faforever.neroxis.generator.prop;

import com.faforever.neroxis.biomes.Biome;
import com.faforever.neroxis.generator.ParameterConstraints;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.*;
import com.faforever.neroxis.util.Pipeline;
import com.faforever.neroxis.util.Util;

public class RockFieldPropGenerator extends BasicPropGenerator {

    protected BooleanMask largeRockFieldMask;

    public RockFieldPropGenerator() {
        parameterConstraints = ParameterConstraints.builder()
                .reclaimDensity(.25f, 1f)
                .build();
    }

    @Override
    public void initialize(SCMap map, long seed, MapParameters mapParameters, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, mapParameters, terrainGenerator);
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        largeRockFieldMask = new BooleanMask(1, random.nextLong(), symmetrySettings, "largeRockFieldMask", true);
    }

    @Override
    public void setupPipeline() {
        super.setupPipeline();
        setupRockFieldPipeline();
    }

    protected void setupRockFieldPipeline() {
        int mapSize = map.getSize();
        float reclaimDensity = mapParameters.getReclaimDensity();
        largeRockFieldMask.setSize(mapSize / 4);

        largeRockFieldMask.randomize((reclaimDensity * .75f + random.nextFloat() * .25f) * .00075f).fillEdge(32, false).dilute(.5f, SymmetryType.SPAWN, 8).setSize(mapSize + 1);
        largeRockFieldMask.intersect(passableLand);
    }

    @Override
    public void placePropsWithExclusion() {
        Pipeline.await(treeMask, cliffRockMask, largeRockFieldMask, fieldStoneMask);
        Util.timedRun("com.faforever.neroxis.generator", "placeProps", () -> {
            Biome biome = mapParameters.getBiome();
            propPlacer.placeProps(treeMask.getFinalMask().minus(noProps), biome.getPropMaterials().getTreeGroups(), 3f, 7f);
            propPlacer.placeProps(cliffRockMask.getFinalMask().minus(noProps), biome.getPropMaterials().getRocks(), .5f, 3f);
            propPlacer.placeProps(largeRockFieldMask.getFinalMask().minus(noProps), biome.getPropMaterials().getRocks(), .5f, 3.5f);
            propPlacer.placeProps(fieldStoneMask.getFinalMask().minus(noProps), biome.getPropMaterials().getBoulders(), 20f);
        });
    }
}