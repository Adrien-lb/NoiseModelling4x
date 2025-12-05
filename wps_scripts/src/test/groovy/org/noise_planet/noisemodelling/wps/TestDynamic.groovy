package org.noise_planet.noisemodelling.wps

import groovy.sql.Sql
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.TableUtilities
import org.h2gis.utilities.dbtypes.DBUtils
import org.noise_planet.noisemodelling.emission.railway.Railway
import org.noise_planet.noisemodelling.jdbc.NoiseMapDatabaseParameters
import org.noise_planet.noisemodelling.wps.Acoustic_Tools.Create_Isosurface;
import org.noise_planet.noisemodelling.wps.Acoustic_Tools.DynamicIndicators;
import org.noise_planet.noisemodelling.wps.Database_Manager.Add_Primary_Key;
import org.noise_planet.noisemodelling.wps.Dynamic.Flow_2_Noisy_Vehicles;
import org.noise_planet.noisemodelling.wps.Dynamic.Ind_Vehicles_2_Noisy_Vehicles;
import org.noise_planet.noisemodelling.wps.Dynamic.Noise_From_Attenuation_Matrix;
import org.noise_planet.noisemodelling.wps.Dynamic.Point_Source_From_Network
import org.noise_planet.noisemodelling.wps.Dynamic.RailWayNetworkFusion
import org.noise_planet.noisemodelling.wps.Dynamic.Split_Sources_Period
import org.noise_planet.noisemodelling.wps.Dynamic.TrainNetworkParameters
import org.noise_planet.noisemodelling.wps.Dynamic.TrainRailwayPosition
import org.noise_planet.noisemodelling.wps.Dynamic.TrainSourcesFromPosition
import org.noise_planet.noisemodelling.wps.Experimental.DynamicTrainFromAADTTraffic;
import org.noise_planet.noisemodelling.wps.Geometric_Tools.Set_Height
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table;
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_File;
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_OSM
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_emission_from_DopplerEffect;
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_source
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_train_source
import org.noise_planet.noisemodelling.wps.Receivers.Delaunay_Grid
import org.noise_planet.noisemodelling.wps.Receivers.Regular_Grid


class TestDynamic extends JdbcTestCase {

    /**
     * as SUMO or SYMUVIA or Drone input
     */
    void testDynamicIndividualVehiclesTutorial() {

        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/buildings_nm_ready_pop_heights.shp").getPath() ,
                "inputSRID": "32635",
                "tableName": "buildings"])

        // Import the receivers (or generate your set of receivers using Regular_Grid script for example)
        new Import_File().exec(connection,
                ["pathFile" : TestDatabaseManager.getResource("Dynamic/receivers_python_method0_50m_pop.shp").getPath() ,
                "inputSRID": "32635",
                "tableName": "receivers"])

        // Set the height of the receivers
        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                "height": 1.5
                ])

        // Import the road network
        new Import_File().exec(connection,
                ["pathFile" :TestDatabaseManager.getResource("Dynamic/network_tartu_32635_.geojson").getPath() ,
                "inputSRID": "32635",
                "tableName": "network_tartu"])

        // (optional) Add a primary key to the road network
        new Add_Primary_Key().exec(connection,
                ["pkName" :"PK",
                "tableName": "network_tartu"])

        // Import the vehicles trajectories
        new Import_File().exec(connection,
                ["pathFile" : TestDatabaseManager.getResource("Dynamic/SUMO.geojson").getPath() ,
                "inputSRID": "32635",
                "tableName": "vehicle"])

        // Create point sources from the network every 10 meters. This point source will be used to compute the noise attenuation level from them to each receiver.
        // The created table will be named SOURCES_GEOM
        new Point_Source_From_Network().exec(connection,
                ["tableNetwork": "network_tartu",
                 "gridStep" : 10
                ])

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new Ind_Vehicles_2_Noisy_Vehicles().exec(connection,
                ["tableSourceGeom" : "SOURCES_GEOM",
                 "tableVehicles": "vehicle",
                 "distance2snap" : 30,
                 "tableFormat" : "SUMO"])

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                "tableSources"   : "SOURCES_GEOM",
                "tableReceivers": "RECEIVERS",
                "maxError" : 0.0,
                "confMaxSrcDist" : 300,
                "confReflOrder" : 0,
                "confDiffHorizontal" : false,
                "confExportSourceId": true,
                ])


        // Compute the noise level from the moving vehicles to the receivers
        // the output table is called here LT_GEOM and contains the time series of the noise level at each receiver
        new Noise_From_Attenuation_Matrix().exec(connection,
                ["lwTable"   : "SOURCES_EMISSION",
                "attenuationTable"   : "RECEIVERS_LEVEL",
                "outputTable"   : "LT_GEOM"
                ])

        def columnNames = JDBCUtilities.getColumnNames(connection, "LT_GEOM")
        assertTrue(columnNames.containsAll(Arrays.asList("PERIOD", "THE_GEOM")))

        // This step is optional, it compute the LEQA, LEQ, L10, L50 and L90 at each receiver from the table LT_GEOM
        String res = new DynamicIndicators().exec(connection,
                ["tableName"   : "LT_GEOM",
                "columnName"   : "LAEQ",
                "outputTableName" : "INDICATORS"
                ])

        columnNames = JDBCUtilities.getColumnNames(connection, "INDICATORS")
        assertTrue(columnNames.containsAll(Arrays.asList("L90", "L50", "L10")))
    }


    /**
     * as OSM input
     */
    void testDynamicFlowTutorialProbabilisticWithAttenuationMatrix() {

        // Import the road network (with predicted traffic flows) and buildings from an OSM file
        new Import_OSM().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.gz").getPath(),
                "targetSRID"    : 2154,
                "ignoreGround"  : true,
                "ignoreBuilding": false,
                "ignoreRoads"   : false,
                "removeTunnels" : true
        ]);

        // Create a receiver grid
        new Regular_Grid().exec(connection,  [
                "fenceTableName": "ROADS",
                "delta"            : 25])

        // Set a height to the receivers at 1.5 m
        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                  "height": 1.5
                ])

        // From the network with traffic flow to individual trajectories with associated Lw using the Probabilistic method
        // This method place randomly the vehicles on the network according to the traffic flow
        new Flow_2_Noisy_Vehicles().exec(connection,
                ["tableRoads": "ROADS",
                 "method": "PROBA",
                 "timestep": 1,
                 "gridStep" : 10,
                 "duration" : 60])

        // Compute the attenuation noise level from the network sources (SOURCES_GEOM) to the receivers
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableReceivers": "RECEIVERS",
                 "confExportSourceId": true,
                 "maxError" : 0.0,
                 "confMaxSrcDist" : 800,
                 "confDiffHorizontal" : false
                ])

        // Compute the noise level from the moving vehicles to the receivers
        // the output table is called here LT_GEOM and contains the time series of the noise level at each receiver
        new Noise_From_Attenuation_Matrix().exec(connection,
                ["lwTable"   : "SOURCES_EMISSION",
                 "lwTable_sourceId": "IDSOURCE",
                 "attenuationTable": NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME,
                 "outputTable"   : "LT_GEOM"
                ])

        // This step is optional, it compute the L10, L50 and L90 at each receiver from the table LT_GEOM
        String res =new DynamicIndicators().exec(connection,
                ["tableName"   : "LT_GEOM",
                 "columnName"   : "LAEQ",
                 "outputTableName" : "INDICATORS"
                ])

        def columnNames = JDBCUtilities.getColumnNames(connection, "INDICATORS")
        assertTrue(columnNames.containsAll(Arrays.asList("L90", "L50", "L10")))
    }


    /**
     * as OSM input
     */
    void testDynamicFlowTutorialProba() {

        // Import the road network (with predicted traffic flows) and buildings from an OSM file
        new Import_OSM().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.gz").getPath(),
                "targetSRID"    : 2154,
                "ignoreGround"  : true,
                "ignoreBuilding": false,
                "ignoreRoads"   : false,
                "removeTunnels" : true
        ]);

        // Create a receiver grid
        new Regular_Grid().exec(connection,  [
                "fenceTableName": "ROADS",
                "delta"            : 25])

        // Set a height to the receivers at 1.5 m
        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                "height": 1.5
                ])

        // From the network with traffic flow to individual trajectories with associated Lw using the Probabilistic method
        // This method place randomly the vehicles on the network according to the traffic flow
        new Flow_2_Noisy_Vehicles().exec(connection,
                ["tableRoads": "ROADS",
                "method": "PROBA",
                "timestep": 1,
                "gridStep" : 10,
                "duration" : 60])


        def expected = JDBCUtilities.getUniqueFieldValues(connection,
                "SOURCES_EMISSION", "PERIOD")

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                "tableSources"   : "SOURCES_GEOM",
                "tableSourcesEmission" : "SOURCES_EMISSION",
                "tableReceivers": "RECEIVERS",
                "maxError" : 2.0,
                "confMaxSrcDist" : 800,
                "confDiffHorizontal" : false
                ])

        def periods = JDBCUtilities.getUniqueFieldValues(connection,
                NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME, "PERIOD")


        assertEquals(expected.size(), periods.size())
        assertTrue(periods.containsAll(expected))

        // This step is optional, it compute the L10, L50 and L90 at each receiver from the table LT_GEOM
        String res =new DynamicIndicators().exec(connection,
                ["tableName"   : NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME,
                "columnName"   : "LAEQ",
                "outputTableName" : "INDICATORS"
                ])

        def columnNames = JDBCUtilities.getColumnNames(connection, "INDICATORS")
        assertTrue(columnNames.containsAll(Arrays.asList("L90", "L50", "L10")))
    }

    /**
     * as OSM input
     */
    void testDynamicFlowTutorialPoisson() {

        File tutorialOutputFolder = new File("build/tmp/TUTO_DYNAMIC_POISSON/")

        if(!tutorialOutputFolder.exists()) {
            assertTrue(tutorialOutputFolder.mkdir())
        }

        // Import the road network (with predicted traffic flows) and buildings from an OSM file
        new Import_OSM().exec(connection, [
                "pathFile"      : TestImportExport.getResource("map.osm.gz").getPath(),
                "targetSRID"    : 2154,
                "ignoreGround"  : true,
                "ignoreBuilding": false,
                "ignoreRoads"   : false,
                "removeTunnels" : true
        ]);

        // Export result table
        new Export_Table().exec(connection,
                [exportPath: new File(tutorialOutputFolder, "BUILDINGS.shp").absolutePath,
                 tableToExport: "BUILDINGS"])

        // Export result table
        new Export_Table().exec(connection,
                [exportPath: new File(tutorialOutputFolder, "ROADS.shp").absolutePath,
                 tableToExport: "ROADS"])

        // Create a receiver grid
        new Regular_Grid().exec(connection,  [
                "fenceTableName": "ROADS",
                "delta" : 25,
                "outputTriangleTable" : true])

        // Set a height to the receivers at 1.5 m
        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                  "height": 1.5
                ])

        // From the network with traffic flow to individual trajectories with associated Lw using the Poisson method
        // This method place the vehicles on the network according to the traffic flow following a poisson law
        // It keeps a coherence in the time series of the noise level
        new Flow_2_Noisy_Vehicles().exec(connection,
                ["tableRoads": "ROADS",
                 "method"    : "POISSON",
                 "timestep"  : 1,
                 "duration"  : 60,
                 "gridStep"  : 8])

        assertTrue(JDBCUtilities.tableExists(connection, "SOURCES_EMISSION"))
        assertTrue(JDBCUtilities.tableExists(connection, "SOURCES_GEOM"))

        def expected = JDBCUtilities.getUniqueFieldValues(connection,
                "SOURCES_EMISSION", "PERIOD")

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"       : "BUILDINGS",
                 "tableSources"        : "SOURCES_GEOM",
                 "tableSourcesEmission": "SOURCES_EMISSION",
                 "tableReceivers"      : "RECEIVERS",
                 "maxError"            : 3.0,
                 "confMaxSrcDist"      : 800,
                 "confDiffHorizontal"  : true,
                 "confReflOrder"       : 0
                ])

        def periods = JDBCUtilities.getUniqueFieldValues(connection,
                NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME, "PERIOD")

        // Export result table
        new Export_Table().exec(connection,
                [exportPath: new File(tutorialOutputFolder, NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME+".shp").absolutePath,
                 tableToExport: NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME])

        // This step is optional, it compute the L10, L50 and L90 at each receiver from the table RECEIVERS_LEVEL
        String res = new DynamicIndicators().exec(connection,
                ["tableName"      : NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME,
                 "columnName"     : "LAEQ",
                 "outputTableName": "INDICATORS"
                ])

        def columnNames = JDBCUtilities.getColumnNames(connection, "INDICATORS")
        assertTrue(columnNames.containsAll(Arrays.asList("L90", "L50", "L10")))

        // Compute contouring noise map
        new Create_Isosurface().exec(connection,
                ["resultTable"      : NoiseMapDatabaseParameters.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME,
                 "smoothCoefficient": 0])

        assertTrue(JDBCUtilities.tableExists(connection, "CONTOURING_NOISE_MAP"))

        // Export result table
        new Export_Table().exec(connection,
                [exportPath: new File(tutorialOutputFolder, "CONTOURING_NOISE_MAP.shp").absolutePath,
                 tableToExport: "CONTOURING_NOISE_MAP"])

        assertEquals(expected.size(), periods.size())
        assertTrue(periods.containsAll(expected))

    }

    /**
     * as MATSIM input
     */
    void testDynamicFluctuatingFlowTutorial() {

        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/Z_EXPORT_TEST_BUILDINGS.geojson").getPath() ,
                "inputSRID": "2154",
                "tableName": "buildings"])

        // Import the road network
        new Import_File().exec(connection,
                ["pathFile" :TestDatabaseManager.getResource("Dynamic/Z_EXPORT_TEST_TRAFFIC.geojson").getPath() ,
                "inputSRID": "2154",
                "tableName": "ROADS"])

        // Create a receiver grid
        new Regular_Grid().exec(connection,  [
                "fenceTableName": "ROADS",
                "delta"            : 25,
                "height": 1.5])

        // From the network with traffic flow to individual trajectories with associated Lw using the Probabilistic method
        // This method place randomly the vehicles on the network according to the traffic flow
        new Split_Sources_Period().exec(connection,
                ["tableSourceDynamic": "ROADS",
                "sourceIndexFieldName" : "LINK_ID",
                "sourcePeriodFieldName" : "TIME"])

        // Compute the noise level from the network sources for each time period
        new Noise_level_from_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                "tableSources"   : "SOURCES_GEOM",
                "tableEmission"   : "SOURCES_EMISSION",
                "tableReceivers": "RECEIVERS",
                "confDiffHorizontal" : true,
                "confReflOrder"       : 0
                ])

        def columnNames = JDBCUtilities.getColumnNames(connection, "RECEIVERS_LEVEL")

        columnNames.containsAll(Arrays.asList("PERIOD", "LAEQ"))

    }

    /**
    * Train tests
     */
    void testDynamicTrainGeneration() {
        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainTrafficSource/RAILS_GEOM.geojson").getPath()])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainTrafficSource/RAILS_TRAFFIC.csv").getPath()])

        def columns  = JDBCUtilities.getColumnNames(connection, "RAILS_TRAFFIC")

        new DynamicTrainFromAADTTraffic().exec(connection,
                [railsGeometries: "RAILS_GEOM",
                  railsTraffic: "RAILS_TRAFFIC"])
    }
    void testDynamicTrainSourcesInterpolation() {
        new TrainRailwayPosition().exec(connection, [
                railwayGeom: [[0.0, 0.0, 0.0],[1000.0, 0.0, 0.0]],
                fieldTrainset: "TGVSE-10U2",
                speedSet: 350,
                idSection: 1,
                integrationTimeSet: 0.125,
                timeStartSet: 1734297900,
                nameFile: "vehicle/vehicleCasR4E",
        ])
    }
    void testTrainRailWayNetwork() {
        new TrainNetworkParameters().exec(connection, [
                railwayGeom: [[0.0, 0.0, 0.0],[1000.0, 0.0, 0.0]],
                idSection: 1,
                nTrack: 1,
                speedTrack: 300,
                trackTrans: 5,
                railRoughn: 2,
                impactNois: 0,
                curvature: 0,
                bridgeTran: 0,
                speedComme: 300,
                isTunnel: false,
        ])
    }
    void testFusionGeojson() {
        new RailWayNetworkFusion().exec(connection, [
                file1Path: "Dynamic/TrainExport/test_Fret_200.geojson",
                file2Path: "Dynamic/TrainExport/test_TGVSE10U2_300.geojson",
                nameFile: "Dynamic/TrainExport/test_TrainTraffic_Fusion"
        ])
    }




    /**
     * Test the generation of multiple wagons sources from engine train position
     */
    void testDynamicTrainSourcesPlacement() {
        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/R4E/vehicleR4E.geojson").getPath()])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/R4E/railTrackR4E.geojson").getPath()])


        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "pointTrainDynamic",
                railwayGeometries: "train_network_32635",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])


        // Check output table content
        def sql = new Sql(connection)

        def cols = sql.rows("SELECT MIN(PERIOD::long) min_period, MAX(PERIOD::long) max_period FROM SOURCES_EMISSION")[0]
        assertEquals(1734297901, cols["min_period"])
        assertEquals(1734297955, cols["max_period"])

    }

    void testDynamicIndividualTrainCas1() {
        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/TrainDynamicTest/testBati.geojson").getPath() ,
                     "inputSRID": "32635",
                 "tableName": "buildings"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/receiverTest.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/vehicle/vehicleCasTest1.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/rail_track/railTrackCasTest1.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "rail_track"])

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                 "confTemperature":20,
                 "confMaxSrcDist" : 1000,
                 "confReflOrder" : 0,
                 "paramWallAlpha" : 1,
                 "confDiffHorizontal" : false,
                 "confDiffVertical" : false,
                 "confExportSourceId": false
                ])

//        new Export_Table().exec(connection,
//                ["tableToExport"   : "SOURCES_EMISSION",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/testEmissionDynamic_cas_test_1.csv"
//                ])
//
//        new Export_Table().exec(connection,
//                ["tableToExport"   : "RECEIVERS_LEVEL",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/testInterpolationReceiversDynamic_cas_test1.csv"
//                ])
//
//        new Export_Table().exec(connection,
//                ["tableToExport"   : "SOURCES_GEOM",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/SOURCES_GEOM_cas_test1.shp"
//                ])
    }
    void testDynamicIndividualTrainCas2() {
        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/TrainDynamicTest/testBati.geojson").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/receiverTest.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/vehicle/vehicleCasTest2.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/rail_track/railTrackCasTest2.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "rail_track"])


        // TODO prevoir le ENRICH_DEM_with_rail

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                 "confTemperature":20,
                 "confMaxSrcDist" : 1000,
                 "confReflOrder" : 0,
                 "paramWallAlpha" : 1,
                 "confDiffHorizontal" : false,
                 "confDiffVertical" : false,
                 "confExportSourceId": false
                ])

//        new Export_Table().exec(connection,
//                ["tableToExport"   : "SOURCES_EMISSION",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/testEmissionDynamic_cas_test_2.csv"
//                ])
//
//        new Export_Table().exec(connection,
//                ["tableToExport"   : "RECEIVERS_LEVEL",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/testInterpolationReceiversDynamic_cas_test2.csv"
//                ])
//
//        new Export_Table().exec(connection,
//                ["tableToExport"   : "SOURCES_GEOM",
//                 "exportPath"   : "C:/Users/lebellec/Documents/1_Projets/NoiseModelling/CasTest/SOURCES_GEOM_cas_test2.shp"
//                ])
    }

    void testDopplerEffect(){
        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/TrainDynamicTest/testBati.geojson").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/receiverTest.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/vehicle/vehicleCasTest1.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/rail_track/railTrackCasTest1.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "rail_track"])

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])
        new Noise_emission_from_DopplerEffect().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                ])



        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                 "confTemperature":20,
                 "confMaxSrcDist" : 1000,
                 "confReflOrder" : 0,
                 "paramWallAlpha" : 1,
                 "confDiffHorizontal" : false,
                 "confDiffVertical" : false,
                 "confExportSourceId": false
                ])

    }

    /**
     * analyse R4E
     */
    void testDynamicTrainSourcesInterpolationR4E() {
        def vehUse = ["TGV-A-12U1", "TGV-A-12U2", "TGV-D-10U1", "TGV-D-10U2"]
        def speedUse = (200..350).step(5).toList()
        def railwayGeomEO = [
                [546521.693513195612468, 6777500.572052604518831, 0.0],
                [546477.599999999976717, 6777514.200000000186265, 0.0],
                [546391.099999999976717, 6777539.799999999813735, 0.0],
                [546282.0, 6777573.400000000372529, 0.0],
                [546253.599999999976717, 6777582.099999999627471, 0.0],
                [546103.099999999976717, 6777630.900000000372529, 0.0],
                [545999.0, 6777666.599999999627471, 0.0],
                [545876.800000000046566, 6777708.299999999813735, 0.0],
                [545740.5, 6777756.700000000186265, 0.0],
                [545597.900000000023283, 6777806.900000000372529, 0.0],
                [545481.900000000023283, 6777847.599999999627471, 0.0],
                [545446.900000000023283, 6777859.299999999813735, 0.0],
                [545380.199999999953434, 6777881.700000000186265, 0.0],
                [545340.0, 6777895.400000000372529, 0.0],
                [545219.0, 6777935.0, 0.0],
                [545116.0, 6777965.400000000372529, 0.0],
                [545034.400000000023283, 6777989.099999999627471, 0.0],
                [544938.699999999953434, 6778013.900000000372529, 0.0],
                [544836.199999999953434, 6778040.099999999627471, 0.0],
                [544835.56068417429924, 6778040.249127665534616, 0.0]
        ]

        def railwayGeomOE = [
                [544835.56068417429924, 6778040.249127665534616, 0.0],
                [544836.199999999953434, 6778040.099999999627471, 0.0],
                [544938.699999999953434, 6778013.900000000372529, 0.0],
                [545034.400000000023283, 6777989.099999999627471, 0.0],
                [545116.0, 6777965.400000000372529, 0.0],
                [545219.0, 6777935.0, 0.0],
                [545340.0, 6777895.400000000372529, 0.0],
                [545380.199999999953434, 6777881.700000000186265, 0.0],
                [545446.900000000023283, 6777859.299999999813735, 0.0],
                [545481.900000000023283, 6777847.599999999627471, 0.0],
                [545597.900000000023283, 6777806.900000000372529, 0.0],
                [545740.5, 6777756.700000000186265, 0.0],
                [545876.800000000046566, 6777708.299999999813735, 0.0],
                [545999.0, 6777666.599999999627471, 0.0],
                [546103.099999999976717, 6777630.900000000372529, 0.0],
                [546253.599999999976717, 6777582.099999999627471, 0.0],
                [546282.0, 6777573.400000000372529, 0.0],
                [546391.099999999976717, 6777539.799999999813735, 0.0],
                [546477.599999999976717, 6777514.200000000186265, 0.0],
                [546521.693513195612468, 6777500.572052604518831, 0.0]
        ]

        vehUse.each { veh ->
            speedUse.each { speed ->
                // Aller (A->B)
                new TrainRailwayPosition().exec(connection, [
                        railwayGeom: railwayGeomEO,
                        fieldTrainset: veh,
                        speedSet: speed,
                        idSection: 1,
                        integrationTimeSet: 0.125,
                        timeStartSet: 1734297900,
                        nameFile: "vehicle/R4E/${veh}_EO_${speed}.geojson",
                ])

                // Retour (B->A)
                new TrainRailwayPosition().exec(connection, [
                        railwayGeom: railwayGeomOE,
                        fieldTrainset: veh,
                        speedSet: speed,
                        idSection: 1,
                        integrationTimeSet: 0.125,
                        timeStartSet: 1734297900,
                        nameFile: "vehicle/R4E/${veh}_OE_${speed}.geojson",
                ])
            }
        }
    }
    void testTrainRailWayNetworkR4E() {
        new TrainNetworkParameters().exec(connection, [
                railwayGeom: [ [ 546521.693513195612468, 6777500.572052604518831, 0.0],
                               [ 546477.599999999976717, 6777514.200000000186265, 0.0],
                               [ 546391.099999999976717, 6777539.799999999813735, 0.0],
                               [ 546282.0, 6777573.400000000372529, 0.0],
                               [ 546253.599999999976717, 6777582.099999999627471, 0.0],
                               [ 546103.099999999976717, 6777630.900000000372529, 0.0],
                               [ 545999.0, 6777666.599999999627471, 0.0],
                               [ 545876.800000000046566, 6777708.299999999813735, 0.0],
                               [ 545740.5, 6777756.700000000186265, 0.0],
                               [ 545597.900000000023283, 6777806.900000000372529, 0.0],
                               [ 545481.900000000023283, 6777847.599999999627471, 0.0],
                               [ 545446.900000000023283, 6777859.299999999813735, 0.0],
                               [ 545380.199999999953434, 6777881.700000000186265, 0.0],
                               [ 545340.0, 6777895.400000000372529, 0.0],
                               [ 545219.0, 6777935.0, 0.0],
                               [ 545116.0, 6777965.400000000372529, 0.0],
                               [ 545034.400000000023283, 6777989.099999999627471, 0.0],
                               [ 544938.699999999953434, 6778013.900000000372529, 0.0],
                               [ 544836.199999999953434, 6778040.099999999627471, 0.0],
                               [ 544835.56068417429924, 6778040.249127665534616, 0.0] ] ,
                idSection: 1,
                nTrack: 1,
                speedTrack: 300,
                trackTrans: 5,
                railRoughn: 2,
                impactNois: 0,
                curvature: 0,
                bridgeTran: 0,
                speedComme: 300,
                isTunnel: false,
                nameFile: "rail_track/R4E/R4E_rail_EO",
        ])
        new TrainNetworkParameters().exec(connection, [
                railwayGeom:[ [ 544835.56068417429924, 6778040.249127665534616, 0.0 ],
                              [ 544836.199999999953434, 6778040.099999999627471, 0.0 ],
                              [ 544938.699999999953434, 6778013.900000000372529, 0.0 ],
                              [ 545034.400000000023283, 6777989.099999999627471, 0.0 ],
                              [ 545116.0, 6777965.400000000372529, 0.0 ],
                              [ 545219.0, 6777935.0, 0.0 ],
                              [ 545340.0, 6777895.400000000372529, 0.0 ],
                              [ 545380.199999999953434, 6777881.700000000186265, 0.0 ],
                              [ 545446.900000000023283, 6777859.299999999813735, 0.0 ],
                              [ 545481.900000000023283, 6777847.599999999627471, 0.0 ],
                              [ 545597.900000000023283, 6777806.900000000372529, 0.0 ],
                              [ 545740.5, 6777756.700000000186265, 0.0 ],
                              [ 545876.800000000046566, 6777708.299999999813735, 0.0 ],
                              [ 545999.0, 6777666.599999999627471, 0.0 ],
                              [ 546103.099999999976717, 6777630.900000000372529, 0.0 ],
                              [ 546253.599999999976717, 6777582.099999999627471, 0.0 ],
                              [ 546282.0, 6777573.400000000372529, 0.0 ],
                              [ 546391.099999999976717, 6777539.799999999813735, 0.0 ],
                              [ 546477.599999999976717, 6777514.200000000186265, 0.0 ],
                              [ 546521.693513195612468, 6777500.572052604518831, 0.0 ] ],
                idSection: 1,
                nTrack: 1,
                speedTrack: 300,
                trackTrans: 5,
                railRoughn: 2,
                impactNois: 0,
                curvature: 0,
                bridgeTran: 0,
                speedComme: 300,
                isTunnel: false,
                nameFile: "rail_track/R4E/R4E_rail_OE",
        ])
    }
    void testDynamicR4E(){
        def vehUse = ["TGV-A-12U1", "TGV-A-12U2", "TGV-D-10U1", "TGV-D-10U2"]
        def speedUse = (200..350).step(5).toList()
        def orientation = ["EO", "OE"]

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/R4E/R4E_receivers.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/R4E/R4E_buildings.geojson").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        orientation.each { direction ->
            def railfile = "Dynamic/R4E/R4E_rail_${direction}.geojson"
            vehUse.each { veh ->
                speedUse.each { speed ->
                    def vehfile = "Dynamic/R4E/veh/${veh}_${direction}_${speed}.geojson"
                    def exportSourceGeom = "src/test/resources/org/noise_planet/noisemodelling/wps/Dynamic/R4E/Resultats/Sources/SOURCES_GEOM_R4E_${veh}_${direction}_${speed}.csv"
                    def exportSourceEmission = "src/test/resources/org/noise_planet/noisemodelling/wps/Dynamic/R4E/Resultats/Sources/SOURCES_EMISSION_R4E_${veh}_${direction}_${speed}.csv"
                    def exportReceiversLevel = "src/test/resources/org/noise_planet/noisemodelling/wps/Dynamic/R4E/Resultats/Receivers/receiversResultsR4E_${veh}_${direction}_${speed}.csv"
                    new Import_File().exec(connection, [
                            pathFile: TestDynamic.getResource(vehfile).getPath(),
                            "inputSRID": "32635",
                            "tableName": "vehicle"])

                    new Import_File().exec(connection, [
                            pathFile: TestDynamic.getResource(railfile).getPath(),
                            "inputSRID": "32635",
                            "tableName": "rail_track"])

                    new TrainSourcesFromPosition().exec(connection, [
                            trainsPosition: "vehicle",
                            railwayGeometries: "rail_track",
                            fieldTrainset: "train_set",
                            fieldTrainId: "train_id",
                            fieldTimeStep: "timestep",
                            trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                            trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                            trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
                    ])

                    // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
                    new Noise_level_from_train_source().exec(connection,
                            ["tableBuilding"   : "BUILDINGS",
                             "tableSources"   : "SOURCES_GEOM",
                             "tableSourcesEmission" : "SOURCES_EMISSION",
                             "selectSource":"ALL",
                             "tableReceivers": "RECEIVERS",
                             "maxError" : 0.0,
                             "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                             "confTemperature":20,
                             "confMaxSrcDist" : 1000,
                             "confReflOrder" : 0,
                             "paramWallAlpha" : 1,
                             "confDiffHorizontal" : false,
                             "confDiffVertical" : false,
                             "confExportSourceId": false
                            ])
                    new Export_Table().exec(connection, [exportPath:exportSourceGeom,
                                                         tableToExport:"SOURCES_GEOM"])
                    new Export_Table().exec(connection, [exportPath:exportSourceEmission,
                                                         tableToExport:"SOURCES_EMISSION"])
                    new Export_Table().exec(connection,["exportPath"   :exportReceiversLevel,
                                                        "tableToExport"   : "RECEIVERS_LEVEL",])
                }
            }
        }

    }

    void testDynamicIndividualTrainSimple() {

        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/TrainDynamicTest/testBati.geojson").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/receiverTest.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
//                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/PointFastTrain.geojson").getPath(),
//                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/SimplePointFastTrain.geojson").getPath(),
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/test_TrainTraffic_Fusion.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/test_RailwayNetwork_Fusion.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "rail_track"])

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])

        /*new Delaunay_Grid().exec(connection, ["buildingTableName"  : "buildings",
                                              "sourcesTableName"   : "rail_track",
                                              "maxArea" : 1000
        ]);


        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                  "height": 1.5
                ])*/

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                 "confTemperature":20,
                 //                 "confRaysName":"RaysExport",
                 "confMaxSrcDist" : 1000,
                 "confReflOrder" : 0,
                 "paramWallAlpha" : 1,
                 "confDiffHorizontal" : false,
                 "confDiffVertical" : false,
                 "confExportSourceId": false
                ])
    }

    void testDynamicDoubleTrain() {

        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/TrainDynamicTest/testBati.geojson").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainDynamicTest/receiverTest.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/test_TrainTraffic_Fusion.geojson").getPath(),
//                pathFile: TestDynamic.getResource("Dynamic/TrainExport/test_FRET_200.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainExport/test_RailwayNetwork_Fusion.geojson").getPath(),
                "inputSRID": "32635",
                "tableName": "rail_track"])

        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "selectSource":"ALL",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confFavorableOccurrencesDefault"  :"0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0, 0, 0, 0, 0, 0, 0, 0",
                 "confTemperature":20,
                 "confMaxSrcDist" : 1000,
                 "confReflOrder" : 0,
                 "paramWallAlpha" : 1,
                 "confDiffHorizontal" : false,
                 "confDiffVertical" : false,
                 "confExportSourceId": false
                ])

    }

    void testDynamicIndividualTrainTutorial() {

        // Import Buildings for your study area
        new Import_File().exec(connection,
                ["pathFile" :  TestDatabaseManager.getResource("Dynamic/buildings_nm_ready_pop_heights.shp").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "buildings"])

        // Import the receivers (or generate your set of receivers using Regular_Grid script for example)
        // create grid Delaunay
        /*new Import_File().exec(connection,
                ["pathFile" : TestDatabaseManager.getResource("Dynamic/receivers_python_method0_50m_pop.shp").getPath() ,
                 "inputSRID": "32635",
                 "tableName": "receivers"])*/
        // Set the height of the receivers

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainSourceDistribution/pointTrainDynamic.geojson").getPath(),
                "tableName": "vehicle"])

        new Import_File().exec(connection, [
                pathFile: TestDynamic.getResource("Dynamic/TrainSourceDistribution/train_network_32635.geojson").getPath(),
                "tableName": "rail_track"])


        // Create a table with the noise level from the vehicles and snap the vehicles to the discretized network
        new TrainSourcesFromPosition().exec(connection, [
                trainsPosition: "vehicle",
                //sourceRelativePosition: Railway.class.getResource("RailwaySourcePosition.json").toString(),
                railwayGeometries: "rail_track",
                fieldTrainset: "train_set",
                fieldTrainId: "train_id",
                fieldTimeStep: "timestep",
                trainTrainsetData: Railway.class.getResource("RailwayTrainsets.json").toString(),
                trainVehicleData: Railway.class.getResource("RailwayVehiclesCnossos.json").toString(),
                trainCoefficientsData: Railway.class.getResource("RailwayCnossosSNCF_2021.json").toString()
        ])


        new Delaunay_Grid().exec(connection, ["buildingTableName"  : "buildings",
                                              "sourcesTableName"   : "rail_track",
                                              "maxArea" : 500
                                            ]);

        new Set_Height().exec(connection,
                [ "tableName":"RECEIVERS",
                  "height": 1.5
                ])

        // Compute the attenuation noise level from the network sources (SOURCES_0DB) to the receivers
        new Noise_level_from_train_source().exec(connection,
                ["tableBuilding"   : "BUILDINGS",
                 "tableSources"   : "SOURCES_GEOM",
                 "tableSourcesEmission" : "SOURCES_EMISSION",
                 "tableReceivers": "RECEIVERS",
                 "maxError" : 0.0,
                 "confMaxSrcDist" : 500,
                 "confReflOrder" : 0,
                 "confDiffHorizontal" : true,
                 "confDiffVertical" : true,
                 "confExportSourceId": false
                ])


        new Create_Isosurface().exec(connection,
                [resultTable: "RECEIVERS_LEVEL",
                 smoothCoefficient : 0])

    }

}
