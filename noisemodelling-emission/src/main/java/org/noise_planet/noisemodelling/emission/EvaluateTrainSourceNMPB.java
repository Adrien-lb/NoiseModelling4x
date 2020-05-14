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

    public static Double getbase0cm(String typeTrain, int spectreVer) { //
        return getnmpbTraindata(spectreVer).get("Train").get(typeTrain).get("Spectrum0cm").doubleValue();
    }

    /** get noise level from 0cm source from speed **/
    private static Double getNoiseLvl0cm(double base0cm, double speed,
                                      double speedBase) {
        return base0cm + 30 * Math.log10(speed / speedBase);
    }
    /** get noise level from 50cm source from speed **/
    private static Double getNoiseLvl50cm(double base50cm, double speed,
                                         double speedBase) {
        return base50cm + 30 * Math.log10(speed / speedBase);
    }
    /** get noise level from 50cm source from speed **/
    private static Double sumDb(Double dB0cm, Double dB50cm) {
        return 10*Math.log10(Math.pow(10,(dB0cm/10)) + Math.pow(10,(dB50cm/10)));
    }


    /**
     * Train noise evaluation.
     * @param parameters Noise emission parameters
     * @return Noise level in dB
     */
    public static double evaluate(TrainParametersNMPB parameters) {
        //final int freqParam = parameters.getFreqParam();
        //lvTrainLvl = getNoiseLvl(getCoeff("Spectrum0cm", freqParam , "1"  ,spectreVer), parameters.getSpeedLv(), 70.);

    }
}


