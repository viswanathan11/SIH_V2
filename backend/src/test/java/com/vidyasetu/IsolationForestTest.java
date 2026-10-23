package com.vidyasetu;

import com.vidyasetu.anomaly.IsolationForest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IsolationForestTest {

    @Test
    void testDeterminismAndScoring() {
        double[][] data = new double[][]{
                {0.5, 0.1, 0.0, 0.0},
                {0.52, 0.11, 0.0, 0.0},
                {0.49, 0.09, 0.0, 0.0},
                {0.51, 0.10, 0.0, 0.0},
                {0.50, 0.10, 0.0, 0.0},
                {0.53, 0.12, 0.0, 0.0},
                // Extreme outlier
                {1.95, 0.85, 0.70, 1.0}
        };

        IsolationForest forest1 = new IsolationForest(100, 256, 42L);
        forest1.fit(data);

        IsolationForest forest2 = new IsolationForest(100, 256, 42L);
        forest2.fit(data);

        double scoreNormal1 = forest1.score(new double[]{0.5, 0.1, 0.0, 0.0});
        double scoreNormal2 = forest2.score(new double[]{0.5, 0.1, 0.0, 0.0});
        assertEquals(scoreNormal1, scoreNormal2, "Same seed must produce identical scores");

        double scoreOutlier = forest1.score(new double[]{1.95, 0.85, 0.70, 1.0});

        assertTrue(scoreOutlier > scoreNormal1, "Outlier score (" + scoreOutlier + ") must exceed normal score (" + scoreNormal1 + ")");
    }
}
