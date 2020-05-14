/**
 * NoiseMap is a scientific computation plugin for OrbisGIS developed in order to
 * evaluate the noise impact on urban mobility plans. This model is
 * based on the French standard method NMPB2008. It includes traffic-to-noise
 * sources evaluation and sound propagation processing.
 *
 * This version is developed at French IRSTV Institute and at IFSTTAR
 * (http://www.ifsttar.fr/) as part of the Eval-PDU project, funded by the
 * French Agence Nationale de la Recherche (ANR) under contract ANR-08-VILL-0005-01.
 *
 * Noisemap is distributed under GPL 3 license. Its reference contact is Judicaël
 * Picaut <judicael.picaut@ifsttar.fr>. It is maintained by Nicolas Fortin
 * as part of the "Atelier SIG" team of the IRSTV Institute <http://www.irstv.fr/>.
 *
 * Copyright (C) 2011 IFSTTAR
 * Copyright (C) 2011-2012 IRSTV (FR CNRS 2488)
 *
 * Noisemap is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Noisemap is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Noisemap. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.noise_planet.noisemodelling.emission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.noise_planet.noisemodelling.propagation.ComputeRays;

import java.io.IOException;
import java.io.InputStream;


/**
 * Return the dB value corresponding to the parameters
 * @author Adrien Le Bellec - 13/05/2020
 */


public class EvaluateTrainSourceNMPB {
// Todo evaluation du niveau sonore d'un train
    private static JsonNode nmpbTraindata = parse(EvaluateTrainSourceNMPB.class.getResourceAsStream("coefficients_train_NMPB.json"));

    private static JsonNode parse(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(inputStream);
        } catch (IOException ex) {
            return NullNode.getInstance();
        }
    }
    public static JsonNode getnmpbTraindata(int spectreVer){
        if (spectreVer==1){
            return nmpbTraindata;
        }
        else {
            return nmpbTraindata;
        }
    }
    public static Double getTrainVmax(String typeTrain, int spectreVer) { //
        return getnmpbTraindata(spectreVer).get("Train").get(typeTrain).get("Vmax").doubleValue();
    }
    public static Double getTrainVref(String typeTrain, int spectreVer) { //
        return getnmpbTraindata(spectreVer).get("Train").get(typeTrain).get("Vref").doubleValue();
    }

    public static Double getbase(String typeTrain, int spectreVer, int freq, double height) { //
        int Freq_ind;
        switch (freq) {
            case 100:
                Freq_ind=0;
                break;
            case 125:
                Freq_ind=1;
                break;
            case 160:
                Freq_ind=2;
                break;
            case 200:
                Freq_ind=3;
                break;
            case 250:
                Freq_ind=4;
                break;
            case 315:
                Freq_ind=5;
                break;
            case 400:
                Freq_ind=6;
                break;
            case 500:
                Freq_ind=7;
                break;
            case 630:
                Freq_ind=8;
                break;
            case 800:
                Freq_ind=9;
                break;
            case 1000:
                Freq_ind=10;
                break;
            case 1250:
                Freq_ind=11;
                break;
            case 1600:
                Freq_ind=12;
                break;
            case 2000:
                Freq_ind=13;
                break;
            case 2500:
                Freq_ind=14;
                break;
            case 3150:
                Freq_ind=15;
                break;
            case 4000:
                Freq_ind=16;
                break;
            case 5000:
                Freq_ind=17;
                break;
            default:
                Freq_ind=0;
        }
        String heightSource;
        if (height==1){
            heightSource="Spectrum50cm";
        }
        else {
            heightSource="Spectrum0cm";
        }
        return getnmpbTraindata(spectreVer).get("Train").get(typeTrain).get(heightSource).get(Freq_ind).doubleValue();
    }

    /** get noise level source from speed **/
    private static Double getNoiseLvl(double base, double speed,
                                      double speedBase) {
        return base + 30 * Math.log10(speed / speedBase);
    }


    /** compute Noise Level from flow_rate and speed **/
    private static Double Vperhour2NoiseLevel(double NoiseLevel, double vperhour, double speed) {
        if (speed > 0) {
            return NoiseLevel + 10 * Math.log10(vperhour / (1000 * speed));
        }else{
            return 0.;
        }
    }

    /**
     * Road noise evaluation.
     * @param parameters Noise emission parameters
     * @return Noise level in dB
     */
    public static double evaluate(TrainParametersNMPB parameters) {
        final int freqParam = parameters.getFreqParam();

        final int spectreVer = parameters.getSpectreVer();
        // ///////////////////////
        // N
        double trainLvl; // Lw/m (1 veh/h)

        // Noise level
        trainLvl = 0;

        // ////////////////////////
        // Lw/m (1 veh/h) to ?
        double base = getbase(parameters.getTypeTrain(),spectreVer, freqParam , parameters.getHeight());
        double testLvl=getNoiseLvl(base,parameters.getSpeed(),parameters.getSpeed());
        double lvFlowLvl = Vperhour2NoiseLevel(trainLvl , parameters.getVehPerHour(), parameters.getSpeed());
        return testLvl;
    }
}


