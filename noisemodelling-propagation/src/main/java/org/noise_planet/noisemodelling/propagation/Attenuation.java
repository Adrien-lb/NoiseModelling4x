/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation;


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.*;
import org.noise_planet.noisemodelling.pathfinder.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.PointPath;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossos;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;


import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.*;
import static java.lang.Math.log10;
//import static org.noise_planet.noisemodelling.pathfinder.path.PointPath.POINT_TYPE.DIFH;
import static org.noise_planet.noisemodelling.pathfinder.utils.Utils.*;

/**
 * Way to store data computed by threads.
 * Multiple threads use one instance.
 * This class must be thread safe
 * @author Nicolas Fortin
 * @author Pierre Aumond
 */
public class Attenuation implements IComputePathsOut {
    public ConcurrentLinkedDeque<SourceReceiverAttenuation> receiversAttenuationLevels = new ConcurrentLinkedDeque<>();
    public Deque<CnossosPath> pathParameters = new ConcurrentLinkedDeque<>();
    public AtomicInteger propagationPathsSize = new AtomicInteger(0);

    public AttenuationCnossosParameters genericMeteoData;
    public Scene inputData;
    //public CnossosPathParameters inputData;
   // public org.noise_planet.noisemodelling.pathfinder.cnossos.CnossosPathParameters CnossosPathParameters;

    public Attenuation(boolean exportPaths, AttenuationCnossosParameters pathData, Scene inputData) {
        this.exportPaths = exportPaths;
        this.genericMeteoData = pathData;
        this.inputData = inputData;
    }

    public Attenuation(boolean exportPaths, AttenuationCnossosParameters pathData) {
        this.exportPaths = exportPaths;
        this.genericMeteoData = pathData;
    }

    public Attenuation(boolean exportPaths, boolean exportAttenuationMatrix, AttenuationCnossosParameters pathData) {
        this.exportPaths = exportPaths;
        this.exportAttenuationMatrix = exportAttenuationMatrix;
        this.genericMeteoData = pathData;
    }

    public boolean exportPaths;
    public boolean exportAttenuationMatrix = false;
    public AtomicLong rayCount = new AtomicLong();
    public AtomicLong nb_couple_receiver_src = new AtomicLong();
    public AtomicLong nb_obstr_test = new AtomicLong();
    public AtomicLong nb_image_receiver = new AtomicLong();
    public AtomicLong nb_reflexion_path = new AtomicLong();
    public AtomicLong nb_diffraction_path = new AtomicLong();
    public AtomicInteger cellComputed = new AtomicInteger();

    /**
     * No more propagation paths will be pushed for this receiver identifier
     * @param receiverId
     */

    public void finalizeReceiver(long receiverId) {

    }

    public Scene getInputData() {
        return inputData;
    }

    /**
     * Get propagation path result
     * @param sourceId Source identifier
     * @param sourceLi Source power per meter coefficient
     * @param path Propagation path result
     */
    @Override
    public double[] addPropagationPaths(long sourceId, double sourceLi, long receiverId, List<CnossosPath> path) {
        rayCount.addAndGet(path.size());
        if(exportPaths) {
            pathParameters.addAll(path);
            propagationPathsSize.addAndGet(path.size());
        }
        double[] aGlobalMeteo = computeCnossosAttenuation(genericMeteoData, sourceId, sourceLi, receiverId, path);
        if (aGlobalMeteo != null && aGlobalMeteo.length > 0) {
            if(inputData != null) {
                if(sourceId < inputData.sourcesPk.size()) {
                    sourceId = inputData.sourcesPk.get((int)sourceId);
                }
                if(receiverId < inputData.receiversPk.size()) {
                    receiverId = inputData.receiversPk.get((int)receiverId);
                }
            }
            receiversAttenuationLevels.add(new SourceReceiverAttenuation(receiverId, sourceId, aGlobalMeteo));
            return aGlobalMeteo;
        } else {
            return new double[0];
        }
    }

    /**
     * Compute the Attenuation for each frequency with a given sourceId, sourceLi and sourceId
     * @param data
     * @param sourceId
     * @param sourceLi
     * @param receiverId
     * @param pathParameters
     * @return double list of attenuation
     */
    public double[] computeCnossosAttenuation(AttenuationCnossosParameters data, long sourceId, double sourceLi, long receiverId, List<CnossosPath> pathParameters) {
        if (data == null) {
            return new double[0];
        }
        // cache frequencies
        double[] frequencies = new double[0];
        if(inputData != null) {
            frequencies = new double[inputData.freq_lvl.size()];
            for (int idFrequency = 0; idFrequency < frequencies.length; idFrequency++) {
                frequencies[idFrequency] = inputData.freq_lvl.get(idFrequency);
            }
        }
        // Compute receiver/source attenuation
        double[] propagationAttenuationSpectrum = null;
        for (CnossosPath proPathParameters : pathParameters) {
            if(exportAttenuationMatrix) {
                proPathParameters.keepAbsorption = true;
                //CnossosPathsParameters.keepAbsorption = true;
                proPathParameters.groundAttenuation.init(data.freq_lvl.size());
                proPathParameters.init(data.freq_lvl.size());
            }
            AttenuationCnossos.init(data);
            //ADiv computation
            double[] aDiv = AttenuationCnossos.aDiv(proPathParameters, data);
            //AAtm computation
            double[] aAtm = AttenuationCnossos.aAtm(data, proPathParameters.getSRSegment().d);
            //Reflexion computation
            double[] aRef = AttenuationCnossos.evaluateAref(proPathParameters, data);
            double[] aRetroDiff;
            //ABoundary computation
            double[] aBoundary;
            double[] aGlobalMeteoHom = new double[data.freq_lvl.size()];
            double[] aGlobalMeteoFav = new double[data.freq_lvl.size()];
            double[] deltaBodyScreen = new double[data.freq_lvl.size()];

            List<PointPath> ptList = proPathParameters.getPointList();

            // todo get hRail from input data
            double hRail = 0.5;
            Coordinate src = ptList.get(0).coordinate;
            PointPath pDif = ptList.stream().filter(p -> p.type.equals(PointPath.POINT_TYPE.DIFH)).findFirst().orElse(null);

            if (pDif != null && pDif.alphaWall.size()>0) {
                if (pDif.bodyBarrier){

                    int n = 3;
                    Coordinate rcv = ptList.get(ptList.size() - 1).coordinate;
                    double[][] deltaGeo = new double[n+1][data.freq_lvl.size()];
                    double[][] deltaAbs = new double[n+1][data.freq_lvl.size()];
                    double[][] deltaDif = new double[n+1][data.freq_lvl.size()];
                    double[][] deltaRef = new double[n+1][data.freq_lvl.size()];
                    double[][] deltaRetroDifi = new double[n+1][data.freq_lvl.size()];
                    double[][] deltaRetroDif = new double[n+1][data.freq_lvl.size()];
                    double[] deltaL = new double[data.freq_lvl.size()];
                    Arrays.fill(deltaL,dbaToW(0.0));

                    double db = pDif.coordinate.x;
                    double hb = pDif.coordinate.y;
                    Coordinate B = new Coordinate(db,hb);

                    double Cref = 1;
                    double dr = rcv.x;
                    double h0 = ptList.get(0).altitude+hRail;
                    double hs = ptList.get(0).altitude+src.y-hRail;
                    double hr = ptList.get(ptList.size()-1).altitude + ptList.get(ptList.size()-1).coordinate.y-h0;
                    double[] r = new double[4];
                    if (db<5*hb) {
                        for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
                            if (pDif.alphaWall.get(idfreq)<0.8){

                                double dif0 =0 ;
                                double ch = 1.;
                                double lambda = 340.0 / data.freq_lvl.get(idfreq);
                                double hi = hs;
                                double cSecond = 1;

                                for (int i = 0; i <= n; i++) {
                                    double di = -2 * i * db;

                                    Coordinate si = new Coordinate(src.x+di, src.y);
                                    r[i] = sqrt(pow(di - (db + dr), 2) + pow(hi - hr, 2));
                                    deltaGeo[i][idfreq] =  20 * log10(r[0] / r[i]);
                                    double deltai = si.distance(B)+B.distance(rcv)-si.distance(rcv);

                                    double dif = 0;
                                    double testForm = (40/lambda)*cSecond*deltai;
                                    if (testForm>=-2) {
                                        dif = 10*ch*log10(3+testForm);
                                    }

                                    if (i==0){
                                        dif0=dif;
                                        deltaRetroDif[i][idfreq] = dif;
                                    }else{
                                        deltaDif[i][idfreq] = dif0-dif;
                                    }

                                    deltaAbs[i][idfreq] = 10 * i * log10(1 - pDif.alphaWall.get(idfreq));
                                    deltaRef[i][idfreq] = 10 * i * log10(Cref);

                                    double retroDif =0 ;
                                    Coordinate Pi = new Coordinate(-(2 * i -1)* db,hb);
                                    Coordinate RcvPrime = new Coordinate(dr,max(hr,hb*(db+dr-di)/(db-di)));
                                    deltai = -(si.distance(Pi)+Pi.distance(RcvPrime)-si.distance(RcvPrime));

                                    testForm = (40/lambda)*cSecond*deltai;
                                    if (testForm>=-2) {
                                        retroDif = 10*ch*log10(3+testForm);
                                    }

                                    if (i==0){
                                        deltaRetroDifi[i][idfreq] = 0;
                                    }else{
                                        deltaRetroDifi[i][idfreq] = retroDif;
                                    }


                                }
                                // Compute deltaRetroDif
                                deltaRetroDif[0][idfreq] = 0;
                                for (int i = 1; i <= n; i++) {
                                    double sumRetrodif = 0;
                                    for (int j = 1; j <= i; j++) {
                                        sumRetrodif = sumRetrodif + deltaRetroDifi[j][idfreq];
                                    }
                                    deltaRetroDif[i][idfreq] = - sumRetrodif;
                                }
                                // Compute deltaL
                                for (int i = 0; i <= n; i++) {
                                    deltaL[idfreq] = deltaL[idfreq] + dbaToW(deltaGeo[i][idfreq] + deltaDif[i][idfreq] + deltaAbs[i][idfreq] + deltaRef[i][idfreq] + deltaRetroDif[i][idfreq]);
                                }
                            }
                        }
                        deltaBodyScreen = wToDba(deltaL);
                    }
                }

            }

            // restore the Map relative propagation direction from the emission propagation relative to the sound source orientation
            // just swap the inverse boolean parameter
            // @see ComputeCnossosRays#computeOrientation
            Vector3D fieldVectorPropagation = Orientation.rotate(proPathParameters.getSourceOrientation(),
                    Orientation.toVector(proPathParameters.raySourceReceiverDirectivity), false);
            int roseIndex = AttenuationCnossosParameters.getRoseIndex(Math.atan2(fieldVectorPropagation.getY(), fieldVectorPropagation.getX()));
            // Homogenous conditions
            if (data.getWindRose()[roseIndex] != 1) {
                proPathParameters.setFavorable(false);


                aBoundary = AttenuationCnossos.aBoundary(proPathParameters, data);
                aRetroDiff = AttenuationCnossos.deltaRetrodif(proPathParameters, data);
                for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
                    aGlobalMeteoHom[idfreq] = -(aDiv[idfreq] + aAtm[idfreq] + aBoundary[idfreq] + aRef[idfreq] + aRetroDiff[idfreq] - deltaBodyScreen[idfreq]); // Eq. 2.5.6
                }
                //For testing purpose
                if(exportAttenuationMatrix) {
                    proPathParameters.double_aBoundaryH = aBoundary.clone();
                    proPathParameters.aGlobalH = aGlobalMeteoHom.clone();
                }
            }
            // Favorable conditions
            if (data.getWindRose()[roseIndex] != 0) {
                proPathParameters.setFavorable(true);
                aBoundary = AttenuationCnossos.aBoundary(proPathParameters, data);
                aRetroDiff = AttenuationCnossos.deltaRetrodif(proPathParameters, data);
                for (int idfreq = 0; idfreq < data.freq_lvl.size(); idfreq++) {
                    aGlobalMeteoFav[idfreq] = -(aDiv[idfreq] + aAtm[idfreq] + aBoundary[idfreq]+ aRef[idfreq] + aRetroDiff[idfreq] -deltaBodyScreen[idfreq]); // Eq. 2.5.8
                }
                //For testing purpose
                if(exportAttenuationMatrix) {
                    proPathParameters.double_aBoundaryF = aBoundary.clone();
                    proPathParameters.aGlobalF = aGlobalMeteoFav.clone();
                }
            }

            //For testing purpose
            if(exportAttenuationMatrix) {
                proPathParameters.keepAbsorption = true;
                proPathParameters.aDiv = aDiv.clone();
                proPathParameters.aAtm = aAtm.clone();
            }

            // Compute attenuation under the wind conditions using the ray direction
            double[] aGlobalMeteoRay = sumArrayWithPonderation(aGlobalMeteoFav, aGlobalMeteoHom, data.getWindRose()[roseIndex]);

            // Apply attenuation due to sound direction
            if(inputData != null && !inputData.isOmnidirectional((int)sourceId)) {
                Orientation directivityToPick = proPathParameters.raySourceReceiverDirectivity;
                double[] attSource = inputData.getSourceAttenuation((int) sourceId,
                        frequencies, Math.toRadians(directivityToPick.yaw),
                        Math.toRadians(directivityToPick.pitch));
                if(exportAttenuationMatrix) {
                    proPathParameters.aSource = attSource;
                }
                aGlobalMeteoRay = sumArray(aGlobalMeteoRay, attSource);
            }

            // For line source, take account of li coefficient
            if(sourceLi > 1.0) {
                for (int i = 0; i < aGlobalMeteoRay.length; i++) {
                    aGlobalMeteoRay[i] = wToDba(dbaToW(aGlobalMeteoRay[i]) * sourceLi);
                }
            }
            // Keep global attenuation
            if(exportAttenuationMatrix) {
                proPathParameters.aGlobal = aGlobalMeteoRay.clone();
            }

            if (propagationAttenuationSpectrum != null) {
                propagationAttenuationSpectrum = sumDbArray(aGlobalMeteoRay, propagationAttenuationSpectrum);
            } else {
                propagationAttenuationSpectrum = aGlobalMeteoRay;
            }
        }
        if (propagationAttenuationSpectrum != null) {
            return propagationAttenuationSpectrum;
        } else {
            return new double[0];
        }
    }


    /**
     *
     * @return an instance of the interface IComputePathsOut
     */
    public IComputePathsOut subProcess() {
        return new AttenuationVisitor(this, genericMeteoData);
    }

    /**
     *
     * @return a list of SourceReceiverAttenuation
     */
    public List<SourceReceiverAttenuation> getVerticesSoundLevel() {
        return new ArrayList<>(receiversAttenuationLevels);
    }

    /**
     *
     * @return a list of Path propagation
     */
    public List<CnossosPath> getPropagationPaths() {
        return new ArrayList<>(pathParameters);
    }

    public void clearPropagationPaths() {
        pathParameters.clear();
        propagationPathsSize.set(0);
    }

    public void appendReflexionPath(long added) {
        nb_reflexion_path.addAndGet(added);
    }

    public void appendDiffractionPath(long added) {
        nb_diffraction_path.addAndGet(added);
    }

    public void appendImageReceiver(long added) {
        nb_image_receiver.addAndGet(added);
    }

    public void appendSourceCount(long srcCount) {
        nb_couple_receiver_src.addAndGet(srcCount);
    }

    public void appendFreeFieldTestCount(long freeFieldTestCount) {
        nb_obstr_test.addAndGet(freeFieldTestCount);
    }

    public synchronized void log(String str) {

    }

    /**
     * Increment cell computed counter by 1
     */
    public synchronized void appendCellComputed() {
        cellComputed.addAndGet(1);
    }

    public synchronized long getCellComputed() {
        return cellComputed.get();
    }

    /**
     * Noise level or attenuation level for each source/receiver
     */


    public static class SourceReceiverAttenuation {
        public final long sourceId;
        public final long receiverId;
        public final double[] value;
        public final Coordinate receiverPosition;

        /**
         *
         * @param receiverId Receiver identifier
         * @param sourceId Source identifier
         * @param value Noise level in dB
         */
        public SourceReceiverAttenuation(long receiverId, long sourceId, double[] value) {
            this.sourceId = sourceId;
            this.receiverId = receiverId;
            this.value = value;
            this.receiverPosition = null;
        }



    }


}