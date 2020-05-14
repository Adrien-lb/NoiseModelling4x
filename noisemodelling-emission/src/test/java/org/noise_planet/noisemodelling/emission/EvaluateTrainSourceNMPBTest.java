package org.noise_planet.noisemodelling.emission;

import org.junit.Test;

import static org.junit.Assert.*;

public class EvaluateTrainSourceNMPBTest {
    private static final double EPSILON_TEST1 = 0.01;
    private static final int[] FREQUENCIES = new int[] {100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000}; // Todo a modifier

    @Test
    public void TrainEmissionTest0cm() {
        String vehCat="TGV300_400_TGV_A";
        double vehicleSpeed = 300;
        double vehicleSpeedRef = 300;
        double vehiclePerHour = 1000;
        int sourceHeight = 0;
        double[] expectedValues = new double[]{113.1,110.8,111.5,111.6,118.5,122.6,118.9,109.2,109,111,110.7,110,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9}; // pour le moment

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehicleSpeedRef,vehiclePerHour,sourceHeight,
                    FREQUENCIES[idFreq]);

            assertEquals(String.format("%d Hz", FREQUENCIES[idFreq]), expectedValues[idFreq], EvaluateTrainSourceNMPB.evaluate(parameters), EPSILON_TEST1);
        }
    }

    @Test
    public void TrainEmissionTest50m() {
        String vehCat="TGV300_400_TGV_A";
        double vehicleSpeed = 300;
        double vehicleSpeedRef = 300;
        double vehiclePerHour = 1000;
        int sourceHeight = 1;
        double[] expectedValues = new double[]{-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,-99.9,110.2,111,112.7,114,110.5,105.9}; // pour le moment

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehicleSpeedRef,vehiclePerHour,sourceHeight,
                    FREQUENCIES[idFreq]);

            assertEquals(String.format("%d Hz", FREQUENCIES[idFreq]), expectedValues[idFreq], EvaluateTrainSourceNMPB.evaluate(parameters), EPSILON_TEST1);
        }
    }

    @Test
    public void TrainEmissionTestSelection() {
        String vehCat="TGV_00_100_TGV_SE_TGV_38_La_Poste";
        double vehicleSpeed = 300;
        double vehicleSpeedRef = 300;
        double vehiclePerHour = 1000;
        int sourceHeight = 0;
        double[] expectedValues = new double[]{
                114.7,
                113.2,
                111.4,
                111.2,
                112.2,
                111.8,
                111.3,
                110.6,
                110.7,
                113.1,
                113.4,
                113.8,
                -99.9,
                -99.9,
                -99.9,
                -99.9,
                -99.9,
                -99.9}; // pour le moment

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehicleSpeedRef,vehiclePerHour,sourceHeight,
                    FREQUENCIES[idFreq]);

            assertEquals(String.format("%d Hz", FREQUENCIES[idFreq]), expectedValues[idFreq], EvaluateTrainSourceNMPB.evaluate(parameters), EPSILON_TEST1);
        }
    }
}
