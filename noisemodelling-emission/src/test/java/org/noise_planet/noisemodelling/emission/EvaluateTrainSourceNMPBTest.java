package org.noise_planet.noisemodelling.emission;

import org.junit.Test;
import org.noise_planet.noisemodelling.propagation.ComputeRays;

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.noise_planet.noisemodelling.emission.EvaluateRoadSourceCnossos.sumDba;

public class EvaluateTrainSourceNMPBTest {
    private static final double EPSILON_TEST1 = 0.01;
    private static final int[] FREQUENCIES = new int[] {100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000};

    @Test
    public void Test_TGV00_38_100() {
        String vehCat="TGV00_38_100";
        double vehicleSpeed = 160;
        double vehiclePerHour = 1;
        int numVeh = 1;
        double expectedValues = 75.9991;
        double[] LW0cm = new double[18];
        double[] LW50cm = new double[18];
        double[] LWm = new double[18];

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }

        double LWmsum = ComputeRays.wToDba(DoubleStream.of(ComputeRays.dbaToW(LWm)).sum());

        assertEquals(String.format("Is it equal ?"),expectedValues,LWmsum,EPSILON_TEST1);
    }

    @Test
    public void Test_Z24500_4() {
        String vehCat="Z24500_4";
        double vehicleSpeed = 160;
        double vehiclePerHour = 1;
        int numVeh = 1;
        double expectedValues = 72.0479;
        double[] LW0cm = new double[18];
        double[] LW50cm = new double[18];
        double[] LWm = new double[18];

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

           // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }

        double LWmsum = ComputeRays.wToDba(DoubleStream.of(ComputeRays.dbaToW(LWm)).sum());

        assertEquals(String.format("Is it equal ?"),expectedValues,LWmsum,EPSILON_TEST1);
    }

    @Test
    public void Test_X72500_Bi() {
        String vehCat="X72500_Bi";
        double vehicleSpeed = 160;
        double vehiclePerHour = 1;
        int numVeh = 1;
        double expectedValues = 70.3031;
        double[] LW0cm = new double[18];
        double[] LW50cm = new double[18];
        double[] LWm = new double[18];

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat,vehicleSpeed,vehiclePerHour,numVeh,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }

        double LWmsum = ComputeRays.wToDba(DoubleStream.of(ComputeRays.dbaToW(LWm)).sum());

        assertEquals(String.format("Is it equal ?"),expectedValues,LWmsum,EPSILON_TEST1);
    }

    @Test
    public void Test_intercite() {
        String vehCat1="BB15000";
        String vehCat2="Corail_FF";
        double vehicleSpeed = 160;
        double vehiclePerHour = 1;

        int numVeh1 = 1;
        int numVeh2 = 7;
        double expectedValues = 84.5078;
        double[] LW0cm = new double[18];
        double[] LW50cm = new double[18];
        double[] LWm = new double[18];

        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat1,vehicleSpeed,vehiclePerHour,numVeh1,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat1,vehicleSpeed,vehiclePerHour,numVeh1,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }
        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat1,vehicleSpeed,vehiclePerHour,numVeh1,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat1,vehicleSpeed,vehiclePerHour,numVeh1,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }
        double LWmsum1 = ComputeRays.wToDba(DoubleStream.of(ComputeRays.dbaToW(LWm)).sum());


        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat2,vehicleSpeed,vehiclePerHour,numVeh2,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat2,vehicleSpeed,vehiclePerHour,numVeh2,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }
        for(int idFreq = 0; idFreq < FREQUENCIES.length; idFreq++) {

            int sourceHeight = 0;
            TrainParametersNMPB parameters = new TrainParametersNMPB(vehCat2,vehicleSpeed,vehiclePerHour,numVeh2,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW0cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            sourceHeight = 1;
            parameters = new TrainParametersNMPB(vehCat2,vehicleSpeed,vehiclePerHour,numVeh2,sourceHeight,
                    FREQUENCIES[idFreq]);
            LW50cm[idFreq] = EvaluateTrainSourceNMPB.evaluate(parameters);

            // Sum L0cm and L50cm
            LWm[idFreq] = sumDba(LW0cm[idFreq],LW50cm[idFreq]);
        }
        double LWmsum2 = ComputeRays.wToDba(DoubleStream.of(ComputeRays.dbaToW(LWm)).sum());
        double LWmsum = sumDba(LWmsum1,LWmsum2);
        assertEquals(String.format("Is it equal ?"),expectedValues,LWmsum,EPSILON_TEST1);
    }

}