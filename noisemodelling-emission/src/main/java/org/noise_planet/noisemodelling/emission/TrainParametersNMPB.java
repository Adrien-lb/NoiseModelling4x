package org.noise_planet.noisemodelling.emission;

public class TrainParametersNMPB {
    private double vehPerHour;

    private double speed;
    private final int FreqParam;


    public void setSpeed(double speed) {
        this.speed = speed;
    }
    public void setVehPerHour(double vehPerHour) {
        this.vehPerHour = vehPerHour;
    }


    public double getVehPerHour() {
        return vehPerHour;
    }
    public double getSpeed() {
        return speed;
    }
    public int getFreqParam() {
        return FreqParam;
    }

    public TrainParametersNMPB(double vehPerHour, double speed, int freqParam) {

       this.vehPerHour = Math.max(0, vehPerHour);
       this.FreqParam = Math.max(0, freqParam);
       setSpeed(speed);
    }
}
