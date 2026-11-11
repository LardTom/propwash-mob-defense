package de.lardtom.mobdefense.dev.dispenser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UnderFrame {
    public static final double RECESS_SHARE = 0.9;
    public static final double MAX_GAP_MM = 1.0;
    public static final double MAX_OVERLAP_MM = 2.0;
    public static final double MIN_COVER = 0.25;
    public static final double COVER_REACH_MM = 10.0;
    static final double MM_PER_UNIT = 8.0;
    static final double MM_PER_BLOCK = 128.0;
    static final double STEP_MM = 1.0;
    static final double COVER_STEP_MM = 2.0;
    static final double RAY_STEP_MM = 0.5;
    static final double NEAR_MM = 20.0;
    static final double EPS = 1.0E-6;
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};

    private UnderFrame() {
    }

    public record Anchor(double x, double y, double z, double tiltDeg, double lengthMm) {
        public static Anchor of(JsonObject anchor) {
            JsonArray pos = anchor.getAsJsonArray("pos");
            return new Anchor(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble(),
                    anchor.has("tilt_deg") ? anchor.get("tilt_deg").getAsDouble() : 0.0, anchor.has("length_mm") ? anchor.get("length_mm").getAsDouble() : 0.0);
        }

        double[] toFrame(double lx, double ly, double lz) {
            double tilt = Math.toRadians(this.tiltDeg);
            double cos = Math.cos(tilt);
            double sin = Math.sin(tilt);
            return new double[]{this.x + lx, this.y + ly * cos - lz * sin, this.z + ly * sin + lz * cos};
        }

        double[] toLocal(double[] p) {
            double tilt = Math.toRadians(this.tiltDeg);
            double cos = Math.cos(tilt);
            double sin = Math.sin(tilt);
            double dy = p[1] - this.y;
            double dz = p[2] - this.z;
            return new double[]{p[0] - this.x, dy * cos + dz * sin, -dy * sin + dz * cos};
        }

        double[] up() {
            double[] origin = this.toFrame(0.0, 0.0, 0.0);
            double[] up = this.toFrame(0.0, 1.0, 0.0);
            return new double[]{up[0] - origin[0], up[1] - origin[1], up[2] - origin[2]};
        }
    }

    public record Box(double[] lo, double[] hi) {
        public static Box of(double width, double height, double depth, double recess, double drop, double stretch) {
            double half = width / 2.0 * stretch;
            return new Box(new double[]{-half, -height - drop, -depth + recess}, new double[]{half, -drop, recess});
        }

        public static Box bottom(double width, double height, double depth, double stretch) {
            double half = width / 2.0 * stretch;
            return new Box(new double[]{-half, -height, -depth / 2.0}, new double[]{half, 0.0, depth / 2.0});
        }

        double depthInside(double[] local, double rear) {
            double depth = Math.min(Math.min(local[0] - this.lo[0], this.hi[0] - local[0]), Math.min(local[1] - this.lo[1], this.hi[1] - local[1]));
            return Math.min(depth, Math.min(local[2] - this.lo[2], rear - local[2]));
        }

        double[][] corners(Anchor anchor) {
            double[][] out = new double[8][];
            for (int c = 0; c < 8; c++) {
                out[c] = anchor.toFrame((c & 1) == 0 ? this.lo[0] : this.hi[0], (c & 2) == 0 ? this.lo[1] : this.hi[1], (c & 4) == 0 ? this.lo[2] : this.hi[2]);
            }
            return out;
        }
    }

    public static final class Part {
        private final double[] lo;
        private final double[] hi;
        private final double[][] matrix;
        private final double[] origin;
        private final double[] rescale;
        private final double[] scale;
        private final double[] offset;
        private final double[][] corners;

        Part(double[] from, double[] to, double[][] matrix, double[] origin, double[] rescale, double[] scale, double[] offset) {
            this.lo = new double[]{Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2])};
            this.hi = new double[]{Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2])};
            this.matrix = matrix;
            this.origin = origin;
            this.rescale = rescale;
            this.scale = scale;
            this.offset = offset;
            this.corners = new double[8][];
            for (int c = 0; c < 8; c++) {
                this.corners[c] = this.toMm(new double[]{(c & 1) == 0 ? this.lo[0] : this.hi[0], (c & 2) == 0 ? this.lo[1] : this.hi[1],
                        (c & 4) == 0 ? this.lo[2] : this.hi[2]});
            }
        }

        private double[] toMm(double[] unit) {
            double[] c = unit.clone();
            if (this.matrix != null) {
                double[] v = {c[0] - this.origin[0], c[1] - this.origin[1], c[2] - this.origin[2]};
                double[] r = new double[3];
                for (int i = 0; i < 3; i++) {
                    r[i] = this.matrix[i][0] * v[0] + this.matrix[i][1] * v[1] + this.matrix[i][2] * v[2];
                }
                for (int i = 0; i < 3; i++) {
                    c[i] = r[i] * this.rescale[i] + this.origin[i];
                }
            }
            return new double[]{c[0] * MM_PER_UNIT * this.scale[0] + this.offset[0], c[1] * MM_PER_UNIT * this.scale[1] + this.offset[1],
                    c[2] * MM_PER_UNIT * this.scale[2] + this.offset[2]};
        }

        private double[] toUnit(double[] mm) {
            double[] c = new double[3];
            for (int i = 0; i < 3; i++) {
                c[i] = (mm[i] - this.offset[i]) / (MM_PER_UNIT * this.scale[i]);
            }
            if (this.matrix != null) {
                double[] v = new double[3];
                for (int i = 0; i < 3; i++) {
                    v[i] = (c[i] - this.origin[i]) / this.rescale[i];
                }
                for (int i = 0; i < 3; i++) {
                    c[i] = this.matrix[0][i] * v[0] + this.matrix[1][i] * v[1] + this.matrix[2][i] * v[2] + this.origin[i];
                }
            }
            return c;
        }

        public double[][] corners() {
            return this.corners;
        }

        public double distance(double[] mm) {
            double[] c = this.toUnit(mm);
            double sum = 0.0;
            for (int i = 0; i < 3; i++) {
                double d = c[i] < this.lo[i] ? this.lo[i] - c[i] : c[i] > this.hi[i] ? c[i] - this.hi[i] : 0.0;
                d *= MM_PER_UNIT * this.scale[i];
                sum += d * d;
            }
            return Math.sqrt(sum);
        }

        public boolean contains(double[] mm) {
            double[] c = this.toUnit(mm);
            for (int i = 0; i < 3; i++) {
                if (c[i] < this.lo[i] - EPS || c[i] > this.hi[i] + EPS) {
                    return false;
                }
            }
            return true;
        }

        Part shifted(double[] by) {
            double[] moved = {this.offset[0] + by[0], this.offset[1] + by[1], this.offset[2] + by[2]};
            return new Part(this.lo, this.hi, this.matrix, this.origin, this.rescale, this.scale, moved);
        }

        double[] min() {
            double[] out = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            for (double[] corner : this.corners) {
                for (int i = 0; i < 3; i++) {
                    out[i] = Math.min(out[i], corner[i]);
                }
            }
            return out;
        }

        double[] max() {
            double[] out = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (double[] corner : this.corners) {
                for (int i = 0; i < 3; i++) {
                    out[i] = Math.max(out[i], corner[i]);
                }
            }
            return out;
        }
    }

    public record Fit(double overhangMm, double gapMm, double boxGapMm, double aheadOverlapMm, double wantedOverlapMm, double cover, double topMm) {
        public boolean underFrame(double depthMm) {
            return this.overhangMm <= (1.0 - RECESS_SHARE) * depthMm + EPS && this.gapMm <= MAX_GAP_MM + EPS && this.aheadOverlapMm <= MAX_OVERLAP_MM + EPS
                    && this.cover >= MIN_COVER && this.topMm <= EPS;
        }

        public String describe() {
            return String.format(Locale.ROOT, "vor dem Anker %.1f mm, Oberkante %.1f mm, Abstand %.2f mm (Quader %.2f mm), Überschneidung vorn %.1f mm, "
                    + "gewollt eingerückt %.1f mm, vom Rahmen bedeckt %.0f %%", this.overhangMm, this.topMm + 0.0, this.gapMm, this.boxGapMm, this.aheadOverlapMm,
                    this.wantedOverlapMm, this.cover * 100.0);
        }
    }

    public static double[] transform(JsonObject itemModel) {
        double[] out = {1.0, 1.0, 1.0, 0.0, 0.0, 0.0};
        if (itemModel.get("transformation") instanceof JsonObject transformation) {
            if (transformation.get("scale") instanceof JsonArray scale) {
                for (int i = 0; i < 3; i++) {
                    out[i] = scale.get(i).getAsDouble();
                }
            }
            if (transformation.get("translation") instanceof JsonArray translation) {
                for (int i = 0; i < 3; i++) {
                    out[3 + i] = translation.get(i).getAsDouble();
                }
            }
        }
        return out;
    }

    public static List<Part> parts(JsonObject model, double[] transform) {
        double[] scale = {transform[0], transform[1], transform[2]};
        double[] offset = new double[3];
        for (int i = 0; i < 3; i++) {
            offset[i] = (transform[3 + i] - 0.5) * MM_PER_BLOCK;
        }
        List<Part> out = new ArrayList<>();
        if (!(model.get("elements") instanceof JsonArray elements)) {
            return out;
        }
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            double[] from = triple(element.getAsJsonArray("from"));
            double[] to = triple(element.getAsJsonArray("to"));
            JsonObject rotation = element.get("rotation") instanceof JsonObject r ? r : null;
            double[][] matrix = rotation == null ? null : matrix(rotation);
            double[] origin = rotation == null || !rotation.has("origin") ? new double[]{8.0, 8.0, 8.0} : triple(rotation.getAsJsonArray("origin"));
            double[] rescale = rotation == null ? new double[]{1.0, 1.0, 1.0} : rescale(rotation);
            out.add(new Part(from, to, matrix, origin, rescale, scale, offset));
        }
        return out;
    }

    public static List<Part> parts(JsonObject model) {
        return parts(model, new double[]{1.0, 1.0, 1.0, 0.0, 0.0, 0.0});
    }

    private static double[] triple(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static double[][] matrix(JsonObject rotation) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        if (rotation.get("axis") instanceof JsonPrimitive axis && axis.isString()) {
            double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0.0;
            switch (axis.getAsString()) {
                case "x" -> x = angle;
                case "y" -> y = angle;
                case "z" -> z = angle;
                default -> {
                    return null;
                }
            }
        } else {
            x = rotation.has("x") ? rotation.get("x").getAsDouble() : 0.0;
            y = rotation.has("y") ? rotation.get("y").getAsDouble() : 0.0;
            z = rotation.has("z") ? rotation.get("z").getAsDouble() : 0.0;
        }
        return multiply(multiply(rotZ(Math.toRadians(z)), rotY(Math.toRadians(y))), rotX(Math.toRadians(x)));
    }

    private static double[][] rotX(double t) {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new double[][]{{1.0, 0.0, 0.0}, {0.0, c, -s}, {0.0, s, c}};
    }

    private static double[][] rotY(double t) {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new double[][]{{c, 0.0, s}, {0.0, 1.0, 0.0}, {-s, 0.0, c}};
    }

    private static double[][] rotZ(double t) {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new double[][]{{c, -s, 0.0}, {s, c, 0.0}, {0.0, 0.0, 1.0}};
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return out;
    }

    private static double[] rescale(JsonObject rotation) {
        double[] out = {1.0, 1.0, 1.0};
        if (!(rotation.get("rescale") instanceof JsonPrimitive flag) || !flag.getAsBoolean() || !(rotation.get("axis") instanceof JsonPrimitive axis)) {
            return out;
        }
        double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0.0;
        double cos = Math.abs(Math.cos(Math.toRadians(angle)));
        double factor = cos > 1.0E-6 ? 1.0 / cos : 1.0;
        switch (axis.getAsString()) {
            case "x" -> {
                out[1] = factor;
                out[2] = factor;
            }
            case "y" -> {
                out[0] = factor;
                out[2] = factor;
            }
            default -> {
                out[0] = factor;
                out[1] = factor;
            }
        }
        return out;
    }

    public static Fit measure(List<Part> frame, Anchor anchor, List<Part> accessoryModel, double width, double height, double depth, double recess,
                              double drop) {
        Box box = Box.of(width, height, depth, recess, drop, 1.0);
        List<double[]> model = new ArrayList<>();
        double overhang = Double.NEGATIVE_INFINITY;
        for (Part part : accessoryModel) {
            Part placed = part.shifted(new double[]{0.0, -drop, recess});
            for (double[] corner : placed.corners()) {
                overhang = Math.max(overhang, -corner[2]);
            }
            for (double[] p : surface(placed.corners(), STEP_MM)) {
                model.add(anchor.toFrame(p[0], p[1], p[2]));
            }
        }
        List<double[]> boxSurface = surface(box.corners(anchor), STEP_MM);
        List<Part> near = near(frame, box.corners(anchor), NEAR_MM);
        double gap = gap(model, near);
        double boxGap = gap(boxSurface, near);
        double ahead = 0.0;
        double wanted = 0.0;
        for (Part part : near) {
            for (double[] p : surface(part.corners(), STEP_MM)) {
                double[] local = anchor.toLocal(p);
                ahead = Math.max(ahead, box.depthInside(local, 0.0));
                wanted = Math.max(wanted, box.depthInside(local, box.hi()[2]));
            }
        }
        return new Fit(overhang, gap, boxGap, ahead, wanted, cover(near, anchor, box), box.hi()[1]);
    }

    static double cover(List<Part> frame, Anchor anchor, Box box) {
        double[] up = anchor.up();
        int all = 0;
        int covered = 0;
        int nx = (int) Math.ceil((box.hi()[0] - box.lo()[0]) / COVER_STEP_MM);
        int nz = (int) Math.ceil((box.hi()[2] - box.lo()[2]) / COVER_STEP_MM);
        for (int i = 0; i <= nx; i++) {
            double x = box.lo()[0] + (box.hi()[0] - box.lo()[0]) * i / nx;
            for (int k = 0; k <= nz; k++) {
                double z = box.lo()[2] + (box.hi()[2] - box.lo()[2]) * k / nz;
                double[] start = anchor.toFrame(x, box.hi()[1], z);
                all++;
                if (hit(frame, start, up)) {
                    covered++;
                }
            }
        }
        return all == 0 ? 0.0 : (double) covered / all;
    }

    private static boolean hit(List<Part> frame, double[] start, double[] up) {
        for (double s = 0.0; s <= COVER_REACH_MM + EPS; s += RAY_STEP_MM) {
            double[] p = {start[0] + up[0] * s, start[1] + up[1] * s, start[2] + up[2] * s};
            for (Part part : frame) {
                if (part.contains(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double overlap(Box box, Anchor anchor, Box other, Anchor otherAnchor) {
        double deepest = 0.0;
        for (double[] p : surface(other.corners(otherAnchor), STEP_MM)) {
            deepest = Math.max(deepest, box.depthInside(anchor.toLocal(p), box.hi()[2]));
        }
        for (double[] p : surface(box.corners(anchor), STEP_MM)) {
            deepest = Math.max(deepest, other.depthInside(otherAnchor.toLocal(p), other.hi()[2]));
        }
        return deepest;
    }

    static double gap(List<double[]> points, List<Part> frame) {
        if (frame.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (double[] p : points) {
            for (Part part : frame) {
                best = Math.min(best, part.distance(p));
                if (best <= 0.0) {
                    return 0.0;
                }
            }
        }
        return best;
    }

    static List<Part> near(List<Part> frame, double[][] corners, double margin) {
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double[] corner : corners) {
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], corner[i] - margin);
                hi[i] = Math.max(hi[i], corner[i] + margin);
            }
        }
        List<Part> out = new ArrayList<>();
        for (Part part : frame) {
            double[] min = part.min();
            double[] max = part.max();
            if (max[0] >= lo[0] && min[0] <= hi[0] && max[1] >= lo[1] && min[1] <= hi[1] && max[2] >= lo[2] && min[2] <= hi[2]) {
                out.add(part);
            }
        }
        return out;
    }

    static List<double[]> surface(double[][] corners, double step) {
        List<double[]> out = new ArrayList<>();
        for (int[] face : FACES) {
            double[] a = corners[face[0]];
            double[] b = corners[face[1]];
            double[] c = corners[face[2]];
            double[] d = corners[face[3]];
            int nu = Math.max(1, (int) Math.ceil(distance(a, b) / step));
            int nv = Math.max(1, (int) Math.ceil(distance(a, d) / step));
            for (int i = 0; i <= nu; i++) {
                double u = (double) i / nu;
                for (int j = 0; j <= nv; j++) {
                    double v = (double) j / nv;
                    double[] p = new double[3];
                    for (int k = 0; k < 3; k++) {
                        p[k] = a[k] * (1 - u) * (1 - v) + b[k] * u * (1 - v) + c[k] * u * v + d[k] * (1 - u) * v;
                    }
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
