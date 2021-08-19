package com.faforever.neroxis.mask;

import com.faforever.neroxis.map.SymmetrySettings;

@SuppressWarnings({"unchecked", "UnusedReturnValue", "unused"})
public strictfp abstract class PrimitiveMask<T extends Comparable<T>, U extends ComparableMask<T, U>> extends ComparableMask<T, U> {

    public PrimitiveMask(int size, Long seed, SymmetrySettings symmetrySettings, String name, boolean parallel) {
        super(size, seed, symmetrySettings, name, parallel);
    }

    public PrimitiveMask(U other, String name) {
        super(other, name);
    }

    protected abstract int[][] getInnerCount();

    protected abstract T transformAverage(float value);

    public U blur(int radius) {
        return enqueue(() -> {
            int[][] innerCount = getInnerCount();
            set((x, y) -> transformAverage(calculateAreaAverage(radius, x, y, innerCount)));
        });
    }

    public U blur(int radius, BooleanMask other) {
        assertCompatibleMask(other);
        return enqueue(dependencies -> {
            BooleanMask limiter = (BooleanMask) dependencies.get(0);
            int[][] innerCount = getInnerCount();
            set((x, y) -> limiter.get(x, y) ? transformAverage(calculateAreaAverage(radius, x, y, innerCount)) : get(x, y));
        }, other);
    }

    protected void calculateInnerValue(int[][] innerCount, int x, int y, int val) {
        calculateScalarInnerValue(innerCount, x, y, val);
    }

    protected float calculateAreaAverage(int radius, int x, int y, int[][] innerCount) {
        int size = getSize();
        int xLeft = StrictMath.max(0, x - radius);
        int xRight = StrictMath.min(size - 1, x + radius);
        int yUp = StrictMath.max(0, y - radius);
        int yDown = StrictMath.min(size - 1, y + radius);
        int countA = xLeft > 0 && yUp > 0 ? innerCount[xLeft - 1][yUp - 1] : 0;
        int countB = yUp > 0 ? innerCount[xRight][yUp - 1] : 0;
        int countC = xLeft > 0 ? innerCount[xLeft - 1][yDown] : 0;
        int countD = innerCount[xRight][yDown];
        int count = countD + countA - countB - countC;
        int area = (xRight - xLeft + 1) * (yDown - yUp + 1);
        return (float) count / area;
    }

}