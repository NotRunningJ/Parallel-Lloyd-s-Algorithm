package lvc.csc;

import java.util.Random;

public class Lloyd {

    // notes about timing are in README

    // using Points for multidimensionality

    private static Point[] data;
    private static int[] clusterSizes;
    private static int[] labels;
    private static Point[] centroids;

    private static final int dimensions = 5;
    
    private static final int k = 3; // number of clusters
    private static final int SIZE = 10_000_000;
    private static Random r = new Random();

    // serial Lloyd's algorithm
    public static void lloyd() {
        centroids = new Point[k];
        clusterSizes = new int[k];
        labels = new int[data.length];

        // split the data into initial clusters
        createClusters(); 

        computeCentroids();

        // move points around
        int moved = 1;
        while(moved > 0) { // attempt to do work
            moved = optimizeClusters();
            if(moved > 0) { // work has been done, check for empty clusters and recompute centroids
                checkForEmpty(); // check as we go? keeping track of sizes
                // compute all the centroids
                computeCentroids();
            }
            System.out.println("moved: " + moved); // watch it converge
        }

        System.out.println();
        for(int i = 0; i < k; i++) {
            System.out.println("Set " + (i+1) + ": Size = " + clusterSizes[i]);
            System.out.println("Centroid: " + centroids[i]);
        }
    }



    /* put the data into k clusters by filling in the labels array
       keep track of the size of each cluster as we go */
    private static void createClusters() {
        r.setSeed(1);
        for(int i = 0; i < data.length; i++) {
            int c = r.nextInt(k);
            labels[i] = c;
            clusterSizes[c]++;
        }
    }


    // compute sums of all clusters -> divide once at the end
    private static void computeCentroids() {
        for(int i = 0; i < centroids.length; i++) {
            centroids[i] = new Point(dimensions); // reset all centroids to 0
        }
        for(int i = 0; i < data.length; i++) {
            int cluster = labels[i];
            Point point = data[i];
            Point partial = Point.scalePoint(point, clusterSizes[cluster]);
            Point sum = Point.addPoints(partial, centroids[cluster]); // partial centroid, already divided
            centroids[cluster] = sum;
        }
    }

    // moves points around until no more can be done
    private static int optimizeClusters() {
        int moved = 0;
        for(int i = 0; i < data.length; i++) { // for all the data points
            double minDist = Double.MAX_VALUE;
            int minCluster = 0; // best cluster to be in
            for(int j = 0; j < centroids.length; j++) { // for each centroid
                double dist = Point.distance(data[i], centroids[j]);
                if(dist < minDist) {
                    minDist = dist;
                    minCluster = j;
                }
            }
            if(labels[i] != minCluster) { // the best cluster for this point has changed
                int oldLabel = labels[i];
                labels[i] = minCluster;
                clusterSizes[minCluster]++;
                clusterSizes[oldLabel]--;
                moved++;
            }
        }
        return moved;
    }


    // checks for empty clusters, moving outliers into them
    private static void checkForEmpty() {
        for(int i = 0; i < clusterSizes.length; i++) {
            if(clusterSizes[i] == 0) { // empty cluster
                moveOutlier(i); 
            }
        }
    }


    // moves the biggest outlier into cluster i...return old cluster
    // single reduction?
    private static void moveOutlier(int cluster) {
        double outlierdist = Double.MIN_VALUE; // how far from its centroid
        int outlieridx = 0;
        for(int i = 0; i < data.length; i++) {
            double dist = Point.distance(data[i], centroids[labels[i]]); // distance to own centroid
            if((dist > outlierdist) && (clusterSizes[labels[i]] > 1)) { // outlier not in a set of its own
                outlierdist = dist;
                outlieridx = i;
            }
        }
        int oldCluster = labels[outlieridx];
        labels[outlieridx] = cluster; // put the outlier in the empty cluster
        clusterSizes[oldCluster]--;
        clusterSizes[cluster]++;
    }

    

    public static void main(String[] args) {
        // predictable test data
        // data = new double[9];
        // data[0] = 1;
        // data[1] = 8;
        // data[2] = 23;
        // data[3] = 2;
        // data[4] = 76;
        // data[5] = 14;
        // data[6] = -1;
        // data[7] = 100;
        // data[8] = 15;
        data = new Point[SIZE];
        r.setSeed(1);
        for(int i = 0; i < SIZE; i++) { // add some small and big numbers -> cluster with small numbers should be bigger
            Point p = new Point(dimensions);
            // generate data to sort of fit into 3 clusters
            // I believe it makes more sense then random data -> then randomly assign initial clusters
            if(i < SIZE/3) {
                for(int j = 0; j < dimensions; j++) {
                    p.setAttribute(j, r.nextInt(100));
                }
            } else if( i < 2*SIZE/3) {
                for(int j = 0; j < dimensions; j++) {
                    p.setAttribute(j, r.nextInt(100000));
                }
            } else {
                for(int j = 0; j < dimensions; j++) {
                    p.setAttribute(j, r.nextInt(1000000000));
                }
            }
            data[i] = p;
        }
        System.out.println("started");
        var start = System.currentTimeMillis();
        lloyd();
        var end = System.currentTimeMillis();
        System.out.println("Time for serial algorithm: " + (end-start)/1000.0 + " seconds");

    }
    
}
