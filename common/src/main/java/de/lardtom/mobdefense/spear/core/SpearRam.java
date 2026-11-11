package de.lardtom.mobdefense.spear.core;

public final class SpearRam {
    public static final double FRONT_COSINE = 0.5;
    private static final double EPSILON = 1.0E-6;

    private SpearRam() {
    }

    public static double cosine(double cx, double cy, double cz, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                double nx, double ny, double nz) {
        double noseLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (noseLength < EPSILON) {
            return -1.0;
        }
        double px = clamp(cx, minX, maxX) - cx;
        double py = clamp(cy, minY, maxY) - cy;
        double pz = clamp(cz, minZ, maxZ) - cz;
        double length = Math.sqrt(px * px + py * py + pz * pz);
        if (length < EPSILON) {
            px = (minX + maxX) * 0.5 - cx;
            py = (minY + maxY) * 0.5 - cy;
            pz = (minZ + maxZ) * 0.5 - cz;
            length = Math.sqrt(px * px + py * py + pz * pz);
            if (length < EPSILON) {
                return 1.0;
            }
        }
        return (px * nx + py * ny + pz * nz) / (length * noseLength);
    }

    public static boolean ahead(double cx, double cy, double cz, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                double nx, double ny, double nz) {
        return cosine(cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ, nx, ny, nz) >= FRONT_COSINE;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
