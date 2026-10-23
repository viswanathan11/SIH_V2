package com.vidyasetu.anomaly;

import java.util.*;

/**
 * Lightweight, zero-dependency Isolation Forest in Java (~120 lines).
 * Deterministic with fixed seed RNG.
 */
public class IsolationForest {
    private final int numTrees;
    private final int subsampleSize;
    private final Random random;
    private final List<Node> trees = new ArrayList<>();

    private static class Node {
        int splitFeature = -1;
        double splitValue = 0.0;
        int size = 0;
        Node left;
        Node right;

        boolean isLeaf() { return left == null && right == null; }
    }

    public IsolationForest(int numTrees, int subsampleSize, long seed) {
        this.numTrees = numTrees;
        this.subsampleSize = subsampleSize;
        this.random = new Random(seed);
    }

    public void fit(double[][] data) {
        trees.clear();
        if (data == null || data.length == 0) return;
        int maxDepth = (int) Math.ceil(Math.log(Math.max(2, subsampleSize)) / Math.log(2));

        for (int i = 0; i < numTrees; i++) {
            double[][] sample = subsample(data, subsampleSize);
            trees.add(buildTree(sample, 0, maxDepth));
        }
    }

    private double[][] subsample(double[][] data, int size) {
        int n = Math.min(data.length, size);
        double[][] sample = new double[n][];
        List<Integer> indices = new ArrayList<>(data.length);
        for (int i = 0; i < data.length; i++) indices.add(i);
        Collections.shuffle(indices, random);
        for (int i = 0; i < n; i++) {
            sample[i] = data[indices.get(i)];
        }
        return sample;
    }

    private Node buildTree(double[][] x, int currentDepth, int maxDepth) {
        Node node = new Node();
        node.size = x.length;

        if (currentDepth >= maxDepth || x.length <= 1) {
            return node;
        }

        int numFeatures = x[0].length;
        int feature = random.nextInt(numFeatures);

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : x) {
            double v = row[feature];
            if (v < min) min = v;
            if (v > max) max = v;
        }

        if (min == max) {
            return node;
        }

        double split = min + random.nextDouble() * (max - min);
        node.splitFeature = feature;
        node.splitValue = split;

        List<double[]> leftList = new ArrayList<>();
        List<double[]> rightList = new ArrayList<>();
        for (double[] row : x) {
            if (row[feature] < split) leftList.add(row);
            else rightList.add(row);
        }

        node.left = buildTree(leftList.toArray(new double[0][]), currentDepth + 1, maxDepth);
        node.right = buildTree(rightList.toArray(new double[0][]), currentDepth + 1, maxDepth);
        return node;
    }

    /** Returns anomaly score between 0.0 and 1.0 (higher = more anomalous) */
    public double score(double[] point) {
        if (trees.isEmpty()) return 0.0;
        double totalPath = 0.0;
        for (Node tree : trees) {
            totalPath += pathLength(point, tree, 0);
        }
        double avgPath = totalPath / trees.size();
        double c = cFactor(subsampleSize);
        if (c <= 0) return 0.0;
        return Math.pow(2.0, - (avgPath / c));
    }

    private double pathLength(double[] point, Node node, double currentDepth) {
        if (node.isLeaf()) {
            return currentDepth + cFactor(node.size);
        }
        if (point[node.splitFeature] < node.splitValue) {
            return pathLength(point, node.left, currentDepth + 1);
        } else {
            return pathLength(point, node.right, currentDepth + 1);
        }
    }

    private static double cFactor(int n) {
        if (n <= 1) return 0.0;
        if (n == 2) return 1.0;
        // Euler-Mascheroni constant = 0.5772156649
        return 2.0 * (Math.log(n - 1.0) + 0.5772156649) - (2.0 * (n - 1.0) / n);
    }
}
