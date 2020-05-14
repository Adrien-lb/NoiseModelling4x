package org.noise_planet.noisemodelling.emission;

public class TrainParametersNMPB {
    private String typeTrain;
    private double vehPerHour;

    private double speed;
    private double speedRef;
    private int height;
    private final int FreqParam;

    private int spectreVer = 2;
    /**
     * @param spectreVer
     */
    public void setSpectreVer(int spectreVer) {
        this.spectreVer = spectreVer;
    }

    public int getSpectreVer() {
        return this.spectreVer;
    }

    public void setTypeTrain(String typeTrain) {
        this.typeTrain = typeTrain;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
    public void setSpeedRef(double speedRef) {
        this.speedRef = speedRef;
    }
    public void setVehPerHour(double vehPerHour) {
        this.vehPerHour = vehPerHour;
    }
    public void setHeight(int height) {
        this.height = height;
    }


    public String getTypeTrain() {
        return typeTrain;
    }
    public double getVehPerHour() {
        return vehPerHour;
    }
    public double getSpeed() {
        return speed;
    }
    public double getSpeedRef() {
        return speedRef;
    }
    public double getHeight() {
        return height;
    }
    public int getFreqParam() {
        return FreqParam;
    }

    public TrainParametersNMPB(String typeTrain, double speed, double speedRef, double vehPerHour, int height,int freqParam) {

       setTypeTrain(typeTrain);
       this.vehPerHour = Math.max(0, vehPerHour);
       this.FreqParam = Math.max(0, freqParam);
       setSpeed(speed);
       setSpeedRef(speedRef);
       setHeight(height);
    }
}
