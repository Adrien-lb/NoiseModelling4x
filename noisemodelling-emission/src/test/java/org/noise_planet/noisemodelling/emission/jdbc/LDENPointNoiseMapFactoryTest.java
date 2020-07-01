package org.noise_planet.noisemodelling.emission.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.functions.io.dbf.DBFRead;
import org.h2gis.functions.io.shp.SHPRead;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SFSUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.propagation.ComputeRays;
import org.noise_planet.noisemodelling.propagation.PropagationProcessPathData;
import org.noise_planet.noisemodelling.propagation.RootProgressVisitor;
import org.noise_planet.noisemodelling.propagation.jdbc.PointNoiseMap;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class LDENPointNoiseMapFactoryTest {

    private Connection connection;

    @Before
    public void tearUp() throws Exception {
        connection = SFSUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(LDENPointNoiseMapFactoryTest.class.getSimpleName(), true, ""));
    }

    @After
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    @Test
    public void testNoiseEmission() throws SQLException, IOException {
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("roads_traff.shp").getFile());
        LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW);
        ldenConfig.setCoefficientVersion(1);
        LDENPropagationProcessData process = new LDENPropagationProcessData(null, ldenConfig);
        try(Statement st = connection.createStatement()) {
            double lv_speed = 70;
            int lv_per_hour = 1000;
            double mv_speed = 70;
            int mv_per_hour = 1000;
            double hgv_speed = 70;
            int hgv_per_hour = 1000;
            double wav_speed = 70;
            int wav_per_hour = 1000;
            double wbv_speed = 70;
            int wbv_per_hour = 1000;
            double Temperature = 15;
            String RoadSurface = "NL01";
            double Pm_stud = 0.5;
            double Ts_stud = 4;
            double Junc_dist = 200;
            int Junc_type = 1;
            StringBuilder qry = new StringBuilder("SELECT ");
            qry.append(lv_speed).append(" LV_SPD_D, ");
            qry.append(lv_per_hour).append(" LV_D, ");
            qry.append(mv_speed).append(" MV_SPD_D, ");
            qry.append(mv_per_hour).append(" MV_D, ");
            qry.append(hgv_speed).append(" HGV_SPD_D, ");
            qry.append(hgv_per_hour).append(" HGV_D, ");
            qry.append(wav_speed).append(" WAV_SPD_D, ");
            qry.append(wav_per_hour).append(" WAV_D, ");
            qry.append(wbv_speed).append(" WBV_SPD_D, ");
            qry.append(wbv_per_hour).append(" WBV_D, ");
            qry.append(Temperature).append(" TEMP, ");
            qry.append(Pm_stud).append(" PM_STUD, ");
            qry.append(Ts_stud).append(" TS_STUD, ");
            qry.append(Junc_dist).append(" JUNC_DIST, '");
            qry.append(Junc_type).append("' JUNC_TYPE, '");
            qry.append(RoadSurface).append("' PVMT ");
            try(ResultSet rs = st.executeQuery(qry.toString())) {
                assertTrue(rs.next());
                double[] leq = process.getEmissionFromResultSet(rs, "D", 10);
                assertEquals(77.67 , leq[leq.length - 1] , 0.1);
            }
        }
    }


    @Test
    public void testTableGenerationFromTraffic() throws SQLException, IOException {
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("roads_traff.shp").getFile());
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("buildings.shp").getFile());
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("receivers.shp").getFile());

        LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW);

        LDENPointNoiseMapFactory factory = new LDENPointNoiseMapFactory(connection, ldenConfig);

        assertFalse(JDBCUtilities.tableExists(connection, ldenConfig.lDayTable));
        assertFalse(JDBCUtilities.tableExists(connection, ldenConfig.lEveningTable));
        assertFalse(JDBCUtilities.tableExists(connection, ldenConfig.lNightTable));
        assertFalse(JDBCUtilities.tableExists(connection, ldenConfig.lDenTable));

        ldenConfig.setComputeLDay(true);
        ldenConfig.setComputeLEvening(true);
        ldenConfig.setComputeLNight(true);
        ldenConfig.setComputeLDEN(true);
        ldenConfig.setMergeSources(true); // No idsource column

        PointNoiseMap pointNoiseMap = new PointNoiseMap("BUILDINGS", "ROADS_TRAFF",
                "RECEIVERS");

        pointNoiseMap.setComputeRaysOutFactory(factory);
        pointNoiseMap.setPropagationProcessDataFactory(factory);

        pointNoiseMap.setMaximumPropagationDistance(100.0);
        pointNoiseMap.setComputeHorizontalDiffraction(false);
        pointNoiseMap.setComputeVerticalDiffraction(false);
        pointNoiseMap.setSoundReflectionOrder(0);

        // Set of already processed receivers
        Set<Long> receivers = new HashSet<>();

        try {
            factory.start();
            RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1);

            pointNoiseMap.initialize(connection, new EmptyProgressVisitor());

            pointNoiseMap.setGridDim(1); // force grid to 1x1

            // Iterate over computation areas
            for (int i = 0; i < pointNoiseMap.getGridDim(); i++) {
                for (int j = 0; j < pointNoiseMap.getGridDim(); j++) {
                    // Run ray propagation
                    pointNoiseMap.evaluateCell(connection, i, j, progressLogger, receivers);
                }
            }
        }finally {
            factory.stop();
        }
        connection.commit();

        // Check table creation
        assertTrue(JDBCUtilities.tableExists(connection, ldenConfig.lDayTable));
        assertTrue(JDBCUtilities.tableExists(connection, ldenConfig.lEveningTable));
        assertTrue(JDBCUtilities.tableExists(connection, ldenConfig.lNightTable));
        assertTrue(JDBCUtilities.tableExists(connection, ldenConfig.lDenTable));

        // Check table number of rows
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) CPT FROM " + ldenConfig.lDayTable)) {
            assertTrue(rs.next());
            assertEquals(830, rs.getInt(1));
        }
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) CPT FROM " + ldenConfig.lEveningTable)) {
            assertTrue(rs.next());
            assertEquals(830, rs.getInt(1));
        }
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) CPT FROM " + ldenConfig.lNightTable)) {
            assertTrue(rs.next());
            assertEquals(830, rs.getInt(1));
        }
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) CPT FROM " + ldenConfig.lDenTable)) {
            assertTrue(rs.next());
            assertEquals(830, rs.getInt(1));
        }

        // Check dB ranges of result
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000), MAX(LEQ), MAX(LAEQ) FROM "+ ldenConfig.lDayTable)) {
            assertTrue(rs.next());
            double[] leqs = new double[PropagationProcessPathData.freq_lvl.size()];
            for(int idfreq = 1; idfreq <= PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                leqs[idfreq - 1] = rs.getDouble(idfreq);
            }
            assertEquals(83, leqs[0], 2.0);
            assertEquals(74, leqs[1], 2.0);
            assertEquals(73, leqs[2], 2.0);
            assertEquals(75, leqs[3], 2.0);
            assertEquals(79, leqs[4], 2.0);
            assertEquals(77, leqs[5], 2.0);
            assertEquals(68, leqs[6], 2.0);
            assertEquals(59, leqs[7], 2.0);

            assertEquals(85, rs.getDouble(9), 2.0);
            assertEquals(82,rs.getDouble(10), 2.0);
        }



        try(ResultSet rs = connection.createStatement().executeQuery("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000), MAX(LEQ), MAX(LAEQ) FROM "+ ldenConfig.lEveningTable)) {
            assertTrue(rs.next());
            double[] leqs = new double[PropagationProcessPathData.freq_lvl.size()];
            for (int idfreq = 1; idfreq <= PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                leqs[idfreq - 1] = rs.getDouble(idfreq);
            }
            assertEquals(76.0, leqs[0], 2.0);
            assertEquals(69.0, leqs[1], 2.0);
            assertEquals(68.0, leqs[2], 2.0);
            assertEquals(70.0, leqs[3], 2.0);
            assertEquals(74.0, leqs[4], 2.0);
            assertEquals(71.0, leqs[5], 2.0);
            assertEquals(62.0, leqs[6], 2.0);
            assertEquals(53.0, leqs[7], 2.0);

            assertEquals(80, rs.getDouble(9), 2.0);
            assertEquals(77,rs.getDouble(10), 2.0);
        }


        try(ResultSet rs = connection.createStatement().executeQuery("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000), MAX(LEQ), MAX(LAEQ) FROM "+ ldenConfig.lNightTable)) {
            assertTrue(rs.next());
            double[] leqs = new double[PropagationProcessPathData.freq_lvl.size()];
            for (int idfreq = 1; idfreq <= PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                leqs[idfreq - 1] = rs.getDouble(idfreq);
            }
            assertEquals(83.0, leqs[0], 2.0);
            assertEquals(74.0, leqs[1], 2.0);
            assertEquals(73.0, leqs[2], 2.0);
            assertEquals(75.0, leqs[3], 2.0);
            assertEquals(79.0, leqs[4], 2.0);
            assertEquals(76.0, leqs[5], 2.0);
            assertEquals(68.0, leqs[6], 2.0);
            assertEquals(58.0, leqs[7], 2.0);

            assertEquals(85, rs.getDouble(9), 2.0);
            assertEquals(82,rs.getDouble(10), 2.0);
        }

        try(ResultSet rs = connection.createStatement().executeQuery("SELECT MAX(HZ63) , MAX(HZ125), MAX(HZ250), MAX(HZ500), MAX(HZ1000), MAX(HZ2000), MAX(HZ4000), MAX(HZ8000), MAX(LEQ), MAX(LAEQ) FROM "+ ldenConfig.lDenTable)) {
            assertTrue(rs.next());
            double[] leqs = new double[PropagationProcessPathData.freq_lvl.size()];
            for (int idfreq = 1; idfreq <= PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                leqs[idfreq - 1] = rs.getDouble(idfreq);
            }
            assertEquals(82.0, leqs[0], 2.0);
            assertEquals(75.0, leqs[1], 2.0);
            assertEquals(74.0, leqs[2], 2.0);
            assertEquals(76.0, leqs[3], 2.0);
            assertEquals(80.0, leqs[4], 2.0);
            assertEquals(77.0, leqs[5], 2.0);
            assertEquals(68.0, leqs[6], 2.0);
            assertEquals(59.0, leqs[7], 2.0);

            assertEquals(86, rs.getDouble(9), 2.0);
            assertEquals(83,rs.getDouble(10), 2.0);
        }
    }


    @Test // Test SHORT format Rail DataBase
    public void testRailNoiseEmission() throws SQLException, IOException {


        DBFRead.read(connection, LDENPointNoiseMapFactoryTest.class.getResource("rail_trafficLDEN.dbf").getFile());
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("rail_geom.shp").getFile());

        LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_RAIL_FLOW);
        ldenConfig.setCoefficientVersion(1);
        LDENPropagationProcessData process = new LDENPropagationProcessData(null, ldenConfig);
        try(Statement st = connection.createStatement()) {

            st.execute("DROP TABLE IF EXISTS RAIL_TRAFFIC_SPEED;");
            st.execute("CREATE TABLE RAIL_TRAFFIC_SPEED AS SELECT a.PR, a.Q,  b.SPEED, a.NAME FROM RAIL_TRAFFICLDEN a, RAIL_GEOM b WHERE a.PR=b.PR;") ;

            StringBuilder qry = new StringBuilder("SELECT  Q,  SPEED, NAME FROM RAIL_TRAFFIC_SPEED ");

            try(ResultSet rs = st.executeQuery(qry.toString())) {
                double[] leqDay=new double[18];
                while (rs.next()) {
                    ldenConfig.setTrainHeight(0);
                    double[] leqDay0 = process.getRailEmissionFromResultSet(rs, "D",  "SHORT");
                    ldenConfig.setTrainHeight(1);
                    double[] leqDay50 = process.getRailEmissionFromResultSet(rs, "D",  "SHORT");
                    double[] leqDay050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay0),ComputeRays.dbaToW(leqDay50)));

                    leqDay= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay),ComputeRays.dbaToW(leqDay050)));

                }
                assertEquals( 84.5078 , ComputeRays.sumEnergeticArray(leqDay) , 0.1);
            }
        }
    }

    @Test // Test LDEN SHORT format Rail DataBase
    public void testRailNoiseEmissionLDEN() throws SQLException, IOException {
        DBFRead.read(connection, LDENPointNoiseMapFactoryTest.class.getResource("rail_trafficLDEN.dbf").getFile());
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("rail_geom.shp").getFile());

        LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_RAIL_FLOW);
        ldenConfig.setCoefficientVersion(1);
        LDENPropagationProcessData process = new LDENPropagationProcessData(null, ldenConfig);
        try(Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS RAIL_TRAFFIC_SPEED;");
            st.execute("CREATE TABLE RAIL_TRAFFIC_SPEED AS SELECT a.PR, b.SPEED, a.TDIURNE, a.TSOIR, a.TNUIT, a.NAME FROM RAIL_TRAFFICLDEN a,  RAIL_GEOM b WHERE a.PR=b.PR;") ;

            StringBuilder qry = new StringBuilder("SELECT SPEED, TDIURNE, TSOIR, TNUIT, NAME FROM RAIL_TRAFFIC_SPEED ");

            try(ResultSet rs = st.executeQuery(qry.toString())) {
                double[] leqDay=new double[18];
                while (rs.next()) {
                    ldenConfig.setTrainHeight(0);
                    double[] leqDay0 = process.getRailEmissionFromResultSet(rs, "D",  "SHORT");
                    ldenConfig.setTrainHeight(1);
                    double[] leqDay50 = process.getRailEmissionFromResultSet(rs, "D",  "SHORT");
                    double[] leqDay050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay0),ComputeRays.dbaToW(leqDay50)));

                    leqDay= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay),ComputeRays.dbaToW(leqDay050)));

                }
                assertEquals( 80.8589 , ComputeRays.sumEnergeticArray(leqDay) , 0.1);
            }
            try(ResultSet rs = st.executeQuery(qry.toString())) {
                double[] leqEvening=new double[18];
                while (rs.next()) {
                    ldenConfig.setTrainHeight(0);
                    double[] leqEvening0 = process.getRailEmissionFromResultSet(rs, "E",  "SHORT");
                    ldenConfig.setTrainHeight(1);
                    double[] leqEvening50 = process.getRailEmissionFromResultSet(rs, "E",  "SHORT");
                    double[] leqEvening050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqEvening0),ComputeRays.dbaToW(leqEvening50)));

                    leqEvening= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqEvening),ComputeRays.dbaToW(leqEvening050)));
                }
                assertEquals( 77.8486 , ComputeRays.sumEnergeticArray(leqEvening) , 0.1);
            }
            try(ResultSet rs = st.executeQuery(qry.toString())) {
                double[] leqNight=new double[18];
                while (rs.next()) {
                    ldenConfig.setTrainHeight(0);
                    double[] leqNight0 = process.getRailEmissionFromResultSet(rs, "N",  "SHORT");
                    ldenConfig.setTrainHeight(1);
                    double[] leqNight50 = process.getRailEmissionFromResultSet(rs, "N",  "SHORT");
                    double[] leqNight050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqNight0),ComputeRays.dbaToW(leqNight50)));

                    leqNight= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqNight),ComputeRays.dbaToW(leqNight050)));
                }
                assertEquals( 19.5424 , ComputeRays.sumEnergeticArray(leqNight) , 0.1); // TODO change value expectd 0 ! wtodb -> 0
            }
        }
    }

    @Test // Test LDEN GEOSTANDARD format Rail DataBase
    public void testRailNoiseEmissionGEOSTANDARD() throws SQLException, IOException {
        DBFRead.read(connection, LDENPointNoiseMapFactoryTest.class.getResource("N_FERROVIAIRE_TRAFIC_003new.dbf").getFile());
        SHPRead.readShape(connection, LDENPointNoiseMapFactoryTest.class.getResource("N_FERROVIAIRE_TRONCON_L_003new.shp").getFile());

        LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_RAIL_FLOW);
        ldenConfig.setCoefficientVersion(1);
        LDENPropagationProcessData process = new LDENPropagationProcessData(null, ldenConfig);
        try(Statement st = connection.createStatement()) {

            st.execute("DROP TABLE IF EXISTS RAIL_TRAFFIC_SPEED;");
            st.execute("CREATE TABLE RAIL_TRAFFIC_SPEED AS SELECT a.IDTRONCON, a.ENGMOTEUR, a.TYPVOITWAG, a.NBVOITWAG, b.VMAXINFRA, a.TDIURNE, a.TSOIR, a.TNUIT FROM N_FERROVIAIRE_TRAFIC_003new a, N_FERROVIAIRE_TRONCON_L_003new b WHERE a.IDTRONCON=b.IDTRONCON;");

            StringBuilder qry2 = new StringBuilder("SELECT ENGMOTEUR, TYPVOITWAG, NBVOITWAG, VMAXINFRA, TDIURNE, TSOIR, TNUIT FROM RAIL_TRAFFIC_SPEED ");

            try (ResultSet rs = st.executeQuery(qry2.toString())) {
                double[] leqDay = new double[18];
                double[] leqEvening = new double[18];
                double[] leqNight = new double[18];

                while (rs.next()) {
                    ldenConfig.setTrainHeight(0); // 0cm height
                    double[] leqDay0 = process.getRailEmissionFromResultSet(rs, "D",  "GEOSTANDARD");
                    double[] leqEvening0 = process.getRailEmissionFromResultSet(rs, "E",  "GEOSTANDARD");
                    double[] leqNight0 = process.getRailEmissionFromResultSet(rs, "N", "GEOSTANDARD");

                    ldenConfig.setTrainHeight(1);// 50cm height
                    double[] leqDay50 = process.getRailEmissionFromResultSet(rs, "D",  "GEOSTANDARD");
                    double[] leqEvening50 = process.getRailEmissionFromResultSet(rs, "E",  "GEOSTANDARD");
                    double[] leqNight50 = process.getRailEmissionFromResultSet(rs, "N", "GEOSTANDARD");

                    double[] leqDay050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay0),ComputeRays.dbaToW(leqDay50)));
                    double[] leqEvening050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqEvening0),ComputeRays.dbaToW(leqEvening50)));
                    double[] leqNight050= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqNight0),ComputeRays.dbaToW(leqNight50)));

                    leqDay= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqDay),ComputeRays.dbaToW(leqDay050)));
                    leqEvening= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqEvening),ComputeRays.dbaToW(leqEvening050)));
                    leqNight= ComputeRays.wToDba(ComputeRays.sumArray(ComputeRays.dbaToW(leqNight),ComputeRays.dbaToW(leqNight050)));


                }
                assertEquals(104.3123, ComputeRays.sumEnergeticArray(leqDay), 0.1);
                assertEquals(102.2601, ComputeRays.sumEnergeticArray(leqEvening), 0.1);
                assertEquals(103.2869, ComputeRays.sumEnergeticArray(leqNight), 0.1);
            }




        }


    }
}