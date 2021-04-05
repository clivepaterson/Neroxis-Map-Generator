package neroxis.generator.prop;

import neroxis.generator.ParameterConstraints;
import neroxis.generator.UnitPlacer;
import neroxis.generator.terrain.TerrainGenerator;
import neroxis.map.*;
import neroxis.util.Pipeline;
import neroxis.util.Util;

import java.util.ArrayList;

public class SmallBattlePropGenerator extends ReducedNaturalPropGenerator {

    protected ConcurrentBinaryMask landWreckMask;
    protected BinaryMask noWrecks;

    public SmallBattlePropGenerator() {
        parameterConstraints = ParameterConstraints.builder()
                .landDensity(.25f, 1f)
                .reclaimDensity(.5f, 1f)
                .build();
    }

    @Override
    public void initialize(SCMap map, long seed, MapParameters mapParameters, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, mapParameters, terrainGenerator);
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        landWreckMask = new ConcurrentBinaryMask(1, random.nextLong(), symmetrySettings, "landWreckMask");
        noWrecks = new BinaryMask(1, random.nextLong(), symmetrySettings);
    }

    @Override
    public void setupPipeline() {
        super.setupPipeline();
        setupWreckPipeline();
    }

    protected void setupWreckPipeline() {
        int mapSize = map.getSize();
        float reclaimDensity = mapParameters.getReclaimDensity();
        landWreckMask.setSize(mapSize / 8);

        landWreckMask.randomize((reclaimDensity * .8f + random.nextFloat() * .2f) * .005f).setSize(mapSize + 1);
        landWreckMask.inflate(6f).intersect(passableLand).fillEdge(32, false);
    }

    protected void generateUnitExclusionMasks() {
        noWrecks.init(unbuildable.getFinalMask());
        generateExclusionZones(noWrecks, 128, 8, 32);
    }

    @Override
    public void placeUnits() {
        if (!mapParameters.isUnexplored()) {
            generateUnitExclusionMasks();
            Pipeline.await(landWreckMask);
            Util.timedRun("neroxis.generator", "placeUnits", () -> {
                Army army17 = new Army("ARMY_17", new ArrayList<>());
                Group army17Wreckage = new Group("WRECKAGE", new ArrayList<>());
                army17.addGroup(army17Wreckage);
                map.addArmy(army17);
                BinaryMask placementMask = landWreckMask.getFinalMask().minus(noWrecks);
                unitPlacer.placeUnits(placementMask, UnitPlacer.T1_Land, army17, army17Wreckage, 3f, 4f);
                unitPlacer.placeUnits(placementMask, UnitPlacer.T2_Land, army17, army17Wreckage, 5f, 8f);
            });
        }
    }
}