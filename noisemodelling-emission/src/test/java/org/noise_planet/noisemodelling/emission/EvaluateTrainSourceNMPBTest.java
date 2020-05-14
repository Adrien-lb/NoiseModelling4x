package org.noise_planet.noisemodelling.emission;

import org.junit.Test;

import static org.junit.Assert.*;

public class EvaluateTrainSourceNMPBTest {
    private static final double EPSILON_TEST1 = 0.01;
    private static final int[] FREQUENCIES = new int[] {100,125,250,500,1000,2000,4000,8000}; // Todo a modifier

    @Test
    public void TrainEmissionTest() {
        String vehCat="TGV_00_100_TGV_SE_TGV_38_La_Poste";
        double vehicleSpeed = 100;
        double vehiclePerHour = 1000;
        double[] expectedValues = new double[]{88.421,77.1136,75.5712,75.6919,73.6689,71.3471,68.1195,63.4796}; // pour le moment

        for(int idFreq = 1; idFreq < FREQUENCIES.length; idFreq++) {
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehicleSpeed,vehiclePerHour,
                    FREQUENCIES[idFreq]);

            assertEquals(String.format("%d Hz", FREQUENCIES[idFreq]), expectedValues[idFreq], EvaluateTrainSourceNMPB.evaluate(parameters), EPSILON_TEST1);
        }
    }

}
