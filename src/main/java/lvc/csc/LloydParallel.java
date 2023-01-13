package lvc.csc;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class LloydParallel {

    // notes about timing are in README

    private static final int SIZE = 10_000_000;
    private static final int dimensions = 5;
    private static final int CORES = 6;
    private static final int k = 3; // number of clusters
    private static final int THRESHOLD =  SIZE / (CORES * 32);
    private static Random r = new Random();


    // map & reduction fused
    public static class CreateClusters extends RecursiveAction {

        private int[] clusterSizes; 
        private int[] labels;
        private int start;
        private int end;

        public CreateClusters(int[] l, int s, int e) {
            this.clusterSizes = new int[k];
            this.labels = l;
            this.start = s;
            this.end = e;
        }

        @Override
        protected void compute() {
            if(end - start < THRESHOLD) {
                create();
            } else {
                int mid = start + (end - start) / 2;

                var left = new CreateClusters(labels, start, mid);
                left.fork();
                var right = new CreateClusters(labels, mid, end);
                right.compute();
                left.join();

                int[] lcs = left.getClusterSizes();
                int[] rcs = right.getClusterSizes();
                for (int i=0; i<clusterSizes.length; ++i) {
                    clusterSizes[i] = lcs[i] + rcs[i];
                }

            }
        }

        private void create() {
            for(int i = start; i < end; i++) {
                int c = r.nextInt(k);
                labels[i] = c;
                clusterSizes[c]++;
            }
        }

        public int[] getClusterSizes() {
            return  clusterSizes;
        }
    }


    // map for each centroid reduction
    public static class ComputeCentroids extends RecursiveAction {

        private Point[] data;
        private Point[] centroids;
        private int[] clusterSizes; 
        private int[] labels;
        private int start;
        private int end;

        public ComputeCentroids(Point[] d, int[] cs, int[] l, int s, int e) {
            this.data = d;
            this.centroids = new Point[k];
            this.clusterSizes = cs;
            this.labels = l;
            this.start = s;
            this.end = e;
        }

        public Point[] getCentroids() {
            return this.centroids;
        }

        @Override
        protected void compute() {
            if(end-start < THRESHOLD) {
                computeCentroid();
            } else {
                int mid = start + (end - start) / 2;

                var left = new ComputeCentroids(data, clusterSizes, labels, start, mid);
                left.fork();
                var right = new ComputeCentroids(data, clusterSizes, labels, mid, end);
                right.compute();
                left.join();

                // add them together since we compute partial centroids serially
                Point[] lcc = left.getCentroids();
                Point[] rcc = right.getCentroids();
                for(int i = 0; i < centroids.length; ++i) {
                    centroids[i] = Point.addPoints(lcc[i], rcc[i]);
                }
            }
            
        }

        /* compute centroids in a map by adding in the partial averages
           instead of summing numerator and dividing once, divide denominator
           at each data point, results in the same average */
        private void computeCentroid() {
            for(int i = 0; i < centroids.length; i++) {
                centroids[i] = new Point(dimensions); // init all centroids
            }
            for(int i = start; i < end; i++) {
                int cluster = labels[i];
                Point point = data[i];
                Point partial = Point.scalePoint(point, clusterSizes[cluster]);
                Point sum = Point.addPoints(partial, centroids[cluster]); // partial centroid, already divided
                centroids[cluster] = sum;
            }
        }

    }


    // map & reduction fused
    public static class MovePoints extends RecursiveAction {

        private Point[] data;
        private Point[] centroids;
        private int[] clusterSizes; 
        private int[] labels;
        private int start;
        private int end;

        // partial data for reductions
        private int moved;
        private int[] movedCS; // cluster Sizes after moving

        public MovePoints(Point[] d, Point[] c, int[] cs, int[] l, int s, int e) {
            this.data = d;
            this.centroids = c;
            this.clusterSizes = cs;
            this.labels = l;
            this.start = s;
            this.end = e;
            this.moved = 0;
            this.movedCS = new int[cs.length];
        }

        public int getMoved() {
            return moved;
        }

        public int[] getClusterSizes() {
            return movedCS;
        }

        @Override
        protected void compute() {
            if(end-start < THRESHOLD) {
                move();
            } else {
                int mid = start + (end - start) / 2;

                var left = new MovePoints(data, centroids, clusterSizes, labels, start, mid);
                left.fork();
                var right = new MovePoints(data, centroids, clusterSizes, labels, mid, end);
                right.compute();
                left.join();

                // update partial reductions
                moved = left.getMoved() + right.getMoved();
                int[] lcs = left.getClusterSizes();
                int[] rcs = right.getClusterSizes();
                for(int i = 0; i < centroids.length; ++i) {
                    movedCS[i] = lcs[i] + rcs[i];
                }

            }
        }

        private void move() {
            for(int i = start; i < end; i++) { // for all the data points
                double minDist = Double.MAX_VALUE;
                int minCluster = 0; // best cluster to be in
                for(int j = 0; j < centroids.length; j++) { // for each centroid
                    double dist = Point.distance(data[i], centroids[j]);
                    if(dist < minDist) { // find best centroids for point i
                        minDist = dist;
                        minCluster = j;
                    }
                }
                if(labels[i] != minCluster) { // the best cluster for this point has changed
                    moved++;
                }
                // do this anyway, we recompute the cluster sizes
                labels[i] = minCluster;
                movedCS[minCluster]++;
            }
        }
        
    }
    

    // reduction
    public static class FindOutlier extends RecursiveAction {

        private Point[] data;
        private Point[] centroids;
        private int[] clusterSizes;
        private int[] labels;
        private int start;
        private int end;

        // partial data for reduction
        private int outlierIdx; // outlier's idx for data and labels array
        private double outlierDist; // distance for finding biggest outlier
        private int outlierOldCluster; // old cluster to decrement size afterwards

        public FindOutlier(Point[] d, Point[] c, int[] cs, int[] l, int s, int e) {
            this.data = d;
            this.centroids = c;
            this.clusterSizes = cs;
            this.labels = l;
            this.start = s;
            this.end = e;
            this.outlierIdx = 0;
            this.outlierDist = Double.MIN_VALUE;
            this.outlierOldCluster = 0;
        }

        public int getOutlierIdx() {
            return outlierIdx;
        }

        public double getOutlierDist() {
            return outlierDist;
        }

        public int getOutlierOldCluster() {
            return outlierOldCluster;
        }


        @Override
        protected void compute() {
            if(end-start < THRESHOLD) {
                findOutlier();
            } else {
                int mid = start + (end - start) / 2;

                var left = new FindOutlier(data, centroids, clusterSizes, labels, start, mid);
                left.fork();
                var right = new FindOutlier(data, centroids, clusterSizes, labels, mid, end);
                right.compute();
                left.join();

                if(left.getOutlierDist() > right.getOutlierDist()) { // left is outlier
                    outlierDist = left.outlierDist;
                    outlierIdx = left.outlierIdx;
                    outlierOldCluster = left.outlierOldCluster;
                } else { // right is outlier, or they are equal
                    outlierDist = right.outlierDist;
                    outlierIdx = right.outlierIdx;
                    outlierOldCluster = right.outlierOldCluster;
                }
            }
            
        }

        private void findOutlier() {
            for(int i = start; i < end; i++) {
                double dist = Point.distance(data[i], centroids[labels[i]]); // distance to own centroid
                if((dist > outlierDist) && (clusterSizes[labels[i]] > 1)) { // outlier not in a set of its own
                    outlierDist = dist;
                    outlierIdx = i;
                    outlierOldCluster = labels[i];
                }
            }
        }
        
    }


    // parallel lloyd's algorithm
    public static void lloyd(Point[] data) {
        Point[] centroids = new Point[k];
        int[] clusterSizes = null;
        int[] labels = new int[data.length];


        //create a thread pool
        ForkJoinPool pool = new ForkJoinPool(CORES);

        // split into k clusters
        var cc = new CreateClusters(labels, 0, data.length);
        pool.invoke(cc); // map
        clusterSizes = cc.getClusterSizes();

        // compute the initial centroids
        var compCen = new ComputeCentroids(data, clusterSizes, labels, 0, data.length);
        pool.invoke(compCen);
        centroids = compCen.getCentroids();

        // move points around
        int moved = 1;
        while(moved > 0) { // while we are moving points
            // move points
            var mp = new MovePoints(data, centroids, clusterSizes, labels, 0, data.length); // map & reduction fused
            pool.invoke(mp);
            moved = mp.getMoved();
            clusterSizes = mp.getClusterSizes();
            if(moved > 0) { // work has been done
                // check for empty and move outliers
                for(int i = 0; i < clusterSizes.length; i++) {
                    if(clusterSizes[i] == 0) { // i is an empty cluster, move outlier into it
                        var mo = new FindOutlier(data, centroids, clusterSizes, labels, 0, data.length);  // reduction
                        pool.invoke(mo);
                        // move the outlier into new cluster once it is found
                        int outlierIdx = mo.getOutlierIdx();
                        int oldCluster = mo.getOutlierOldCluster();
                        labels[outlierIdx] = i;
                        clusterSizes[i]++;
                        clusterSizes[oldCluster]--;
                    }
                }

                // recompute the centroids
                var cen = new ComputeCentroids(data, clusterSizes, labels, 0, data.length);
                pool.invoke(cen);
                centroids = cen.getCentroids();
            } // end if moved > 0
            System.out.println("moved: " + moved); // watch it converge

        } // end while working loop

        // you will see one of the clusters having about 2/3 of the data in it, with 
        // smaller centroid numbers - this makes sense with my test code I believe
        for(int i = 0; i < k; i++) {
            System.out.println("Set " + (i+1) + ": Size = " + clusterSizes[i]);
            System.out.println("Centroid: " + centroids[i]);
        }
    }


    public static void main(String[] args) {
        Point[] data = new Point[SIZE];
        r.setSeed(1);
        for(int i = 0; i < SIZE; i++) { // add some small and big numbers -> cluster with small numbers should be bigger
            Point p = new Point(dimensions);
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
        var start = System.currentTimeMillis();
        lloyd(data);
        var end = System.currentTimeMillis();
        System.out.println("Time for parallel algorithm: " + (end-start)/1000.0 + " seconds");

    }
}
