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
package org.noise_planet.noisemodelling.pathfinder;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestWallReflection {

    public static int pushBuildingToWalls(ProfileBuilder.Building building, int index, List<ProfileBuilder.Wall> wallList) {
        ArrayList<ProfileBuilder.Wall> wallsOfBuilding = new ArrayList<>();
        Coordinate[] coords = building.getGeometry().getCoordinates();
        for (int i = 0; i < coords.length - 1; i++) {
            LineSegment lineSegment = new LineSegment(coords[i], coords[i + 1]);
            ProfileBuilder.Wall w = new ProfileBuilder.Wall(lineSegment, index, ProfileBuilder.IntersectionType.BUILDING);
            w.setProcessedWallIndex(i);
            wallsOfBuilding.add(w);
        }
        building.setWalls(wallsOfBuilding);
        wallList.addAll(wallsOfBuilding);
        return coords.length;
    }

    @Test
    public void testMultipleDepthReflexion() {
        double maxPropagationDistance = 1000;

        List<ProfileBuilder.Wall> buildWalls = new ArrayList<>();
        Coordinate cA = new Coordinate(1, 1, 5);
        Coordinate cB = new Coordinate(1, 8, 5);
        Coordinate cC = new Coordinate(8, 8, 5);
        Coordinate cD = new Coordinate(8, 5, 5);
        Coordinate cE = new Coordinate(5, 5, 5);
        Coordinate cF = new Coordinate(5, 1, 5);
        Coordinate cG = new Coordinate(13, 1, 2.5);
        Coordinate cH = new Coordinate(13, 8, 2.5);

        GeometryFactory factory = new GeometryFactory();
        Polygon buildingGeometry = factory.createPolygon(new Coordinate[] {cA, cB, cC, cD, cE, cF, cA});

        ProfileBuilder.Building building = new ProfileBuilder.Building(buildingGeometry, 5,
                Collections.emptyList(), 0, true);

        pushBuildingToWalls(building, 0, buildWalls);
        buildWalls.add(new ProfileBuilder.Wall(cG, cH, 0, ProfileBuilder.IntersectionType.BUILDING));

        Coordinate receiverCoordinates = new Coordinate(6, 3, 1.6);

        int reflectionOrder = 1;
        MirrorReceiverResultIndex mirrorReceiverResultIndex = new MirrorReceiverResultIndex(buildWalls,
                receiverCoordinates, reflectionOrder, maxPropagationDistance, maxPropagationDistance);

        Coordinate source1 = new Coordinate(10, 7, 0.1);

        List<MirrorReceiverResult> result = mirrorReceiverResultIndex.findCloseMirrorReceivers(source1);

        // Reflection only on [g h] wall
        assertEquals(1, result.size());


        reflectionOrder = 2;

        mirrorReceiverResultIndex = new MirrorReceiverResultIndex(buildWalls,
                receiverCoordinates, reflectionOrder, maxPropagationDistance, maxPropagationDistance);


        result = mirrorReceiverResultIndex.findCloseMirrorReceivers(source1);

        // Reflection on [g h] [h g -> e f] [h g -> c d]
        assertEquals(7, result.size());
    }

//    @Test
//    public void testExportVisibilityCones() throws Exception {
//        double maxPropagationDistance = 1200;
//
//        List<ProfileBuilder.Wall> buildWalls = new ArrayList<>();
//        Coordinate cA = new Coordinate(1, 1, 5);
//        Coordinate cB = new Coordinate(1, 8, 5);
//        Coordinate cC = new Coordinate(8, 8, 5);
//        Coordinate cD = new Coordinate(8, 5, 5);
//        Coordinate cE = new Coordinate(5, 5, 5);
//        Coordinate cF = new Coordinate(5, 1, 5);
//        Coordinate cG = new Coordinate(13, 1, 2.5);
//        Coordinate cH = new Coordinate(13, 8, 2.5);
//        buildWalls.add(new ProfileBuilder.Wall(cE, cF, 0, ProfileBuilder.IntersectionType.WALL));
//        buildWalls.add(new ProfileBuilder.Wall(cB, cC, 1, ProfileBuilder.IntersectionType.WALL));
//        buildWalls.add(new ProfileBuilder.Wall(cA, cF, 2, ProfileBuilder.IntersectionType.WALL));
//
//
//        Coordinate receiverCoordinates = new Coordinate(200, 50, 14);
//        Coordinate source1 = new Coordinate(10, 10, 1);
//
//        int reflectionOrder = 1;
//
//        MirrorReceiverResultIndex mirrorReceiverResultIndex = new MirrorReceiverResultIndex(buildWalls,
//                receiverCoordinates, reflectionOrder, maxPropagationDistance, maxPropagationDistance);
//
//        List<MirrorReceiverResult> objs = (List<MirrorReceiverResult>) mirrorReceiverResultIndex.mirrorReceiverTree.
//                query(new Envelope(new Coordinate(0, 0), new Coordinate(500, 500)));
//
//        WKTWriter wktWriter = new WKTWriter();
//        GeometryFactory factory = new GeometryFactory();
//        try(FileWriter fileWriter = new FileWriter(new File("target/testVisibilityCone.csv"))) {
//            fileWriter.write("geom, type\n");
//            for (MirrorReceiverResult res : objs) {
//                Polygon visibilityCone = MirrorReceiverResultIndex.createWallReflectionVisibilityCone(res.getReceiverPos(), res.getWall().getLineSegment(), maxPropagationDistance);
//                fileWriter.write("\"");
//                fileWriter.write(wktWriter.write(visibilityCone));
//                fileWriter.write("\",0");
//                fileWriter.write("\n");
//            }
//            for(ProfileBuilder.Wall wall : buildWalls) {
//                fileWriter.write("\"");
//                fileWriter.write(wktWriter.write(factory.createLineString(new Coordinate[]{wall.p0, wall.p1})
//                        .buffer(0.05, 8, BufferParameters.CAP_SQUARE)));
//                fileWriter.write("\",1");
//                fileWriter.write("\n");
//            }
//            fileWriter.write("\"");
//            fileWriter.write(wktWriter.write(factory.createPoint(receiverCoordinates)
//                    .buffer(2, 12, BufferParameters.CAP_ROUND)));
//            fileWriter.write("\",2");
//            fileWriter.write("\n");
//            fileWriter.write("\"");
//            fileWriter.write(wktWriter.write(factory.createPoint(source1)
//                    .buffer(2, 12, BufferParameters.CAP_ROUND)));
//            fileWriter.write("\",3");
//            fileWriter.write("\n");
//        }
//    }

}
