package lvc.csc;

// a point object to represent a point in n dimensional data
public class Point {

    private double[] attributes;

    public Point(int n) {
        this.attributes = new double[n];
    }

    public Point(double[] a) {
        this.attributes = a;
    }

    public void setAttribute(int n, double d) {
        this.attributes[n] = d;
    }

    public double[] getAttributes() {
        return this.attributes;
    }

    public int getDimension() {
        return this.attributes.length;
    }

    // convert Point to String for printing purposes
    public String toString() {
        String s = "";
        s = s + attributes[0];
        for(int i = 1; i < attributes.length; i++) {
            s = s + ", " + attributes[i];
        }
        return s;
    }

    // calculate the distance between two points
    public static double distance(Point a, Point b) {
        double dist = 0;
        double[] aData = a.getAttributes();
        double[] bData = b.getAttributes();
        for(int i = 0; i < aData.length; i++) {
            dist += Math.pow((aData[i] - bData[i]), 2);
        }
        dist = Math.sqrt(dist);
        return dist;
    }

    // add two points together...this will be useful when computing centroids
    public static Point addPoints(Point a, Point b) {
        Point p = new Point(a.getDimension());
        double[] aData = a.getAttributes();
        double[] bData = b.getAttributes();
        for(int i = 0; i < aData.length; i++) {
            p.setAttribute(i, aData[i] + bData[i]);
        }
        return p;
    }

    // divide a point by a scaler - for computing partial centroids
    public static Point scalePoint(Point a, int scale) {
        Point p = new Point(a.getDimension());
        double[] aData = a.getAttributes();
        for(int i = 0; i < aData.length; i++) {
            p.setAttribute(i, aData[i] / scale);
        }
        return p;
    }
}
