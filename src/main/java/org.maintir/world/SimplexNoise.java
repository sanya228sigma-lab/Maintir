package org.maintir.world;

import java.util.Random;

public class SimplexNoise {
    private static final int[] p = new int[512];
    private static final int[] permutation = new int[256];

    static {
        Random rand = new Random(1337); // Seed мира
        for (int i = 0; i < 256; i++) permutation[i] = i;
        for (int i = 0; i < 256; i++) {
            int j = rand.nextInt(256);
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }
        for (int i = 0; i < 512; i++) p[i] = permutation[i & 255];
    }

    public static double noise(double xin, double zin) {
        double s = (xin + zin) * 0.5 * (Math.sqrt(3.0) - 1.0);
        int i = fastfloor(xin + s);
        int j = fastfloor(zin + s);
        double t = (i + j) * (3.0 - Math.sqrt(3.0)) / 6.0;
        double X0 = i - t;
        double Z0 = j - t;
        double x0 = xin - X0;
        double z0 = zin - Z0;

        int i1, j1;
        if (x0 > z0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + (3.0 - Math.sqrt(3.0)) / 6.0;
        double z1 = z0 - j1 + (3.0 - Math.sqrt(3.0)) / 6.0;
        double x2 = x0 - 1.0 + 2.0 * (3.0 - Math.sqrt(3.0)) / 6.0;
        double z2 = z0 - 1.0 + 2.0 * (3.0 - Math.sqrt(3.0)) / 6.0;

        int ii = i & 255;
        int jj = j & 255;

        double t0 = 0.5 - x0 * x0 - z0 * z0;
        double n0 = t0 < 0 ? 0.0 : Math.pow(t0, 4) * grad(p[ii + p[jj]], x0, z0);

        double t1 = 0.5 - x1 * x1 - z1 * z1;
        double n1 = t1 < 0 ? 0.0 : Math.pow(t1, 4) * grad(p[ii + i1 + p[jj + j1]], x1, z1);

        double t2 = 0.5 - x2 * x2 - z2 * z2;
        double n2 = t2 < 0 ? 0.0 : Math.pow(t2, 4) * grad(p[ii + 1 + p[jj + 1]], x2, z2);

        return 70.0 * (n0 + n1 + n2);
    }

    private static int fastfloor(double x) {
        int ix = (int) x;
        return x < ix ? ix - 1 : ix;
    }

    private static double grad(int hash, double x, double z) {
        int h = hash & 7;
        double u = h < 4 ? x : z;
        double v = h < 4 ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}