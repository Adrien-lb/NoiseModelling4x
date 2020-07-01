/**
 * NoiseModelling is a free and open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 * as part of:
 * the Eval-PDU project (ANR-08-VILL-0005) 2008-2011, funded by the Agence Nationale de la Recherche (French)
 * the CENSE project (ANR-16-CE22-0012) 2017-2021, funded by the Agence Nationale de la Recherche (French)
 * the Nature4cities (N4C) project, funded by European Union’s Horizon 2020 research and innovation programme under grant agreement No 730468
 *
 * Noisemap is distributed under GPL 3 license.
 *
 * Contact: contact@noise-planet.org
 *
 * Copyright (C) 2011-2012 IRSTV (FR CNRS 2488) and Ifsttar
 * Copyright (C) 2013-2019 Ifsttar and CNRS
 * Copyright (C) 2020 Université Gustave Eiffel and CNRS
 *
 * @Author Adrien LE BELLEC, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import groovy.time.TimeCategory
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.SFSUtilities
import org.h2gis.utilities.SpatialResultSet
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.noise_planet.noisemodelling.emission.EvaluateRoadSourceCnossos
import org.noise_planet.noisemodelling.emission.RSParametersCnossos
import org.noise_planet.noisemodelling.emission.jdbc.LDENConfig
import org.noise_planet.noisemodelling.emission.jdbc.LDENPropagationProcessData
import org.noise_planet.noisemodelling.propagation.ComputeRays
import org.noise_planet.noisemodelling.propagation.FastObstructionTest
import org.noise_planet.noisemodelling.propagation.PropagationProcessData
import org.noise_planet.noisemodelling.propagation.PropagationProcessPathData
import org.noise_planet.noisemodelling.propagation.jdbc.PointNoiseMap

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

title = 'Compute rail emission noise map from rail_traffic table AND rail_geom table.'
description = 'Compute Rail Emission Noise Map from Day Evening Night traffic flow rate and speed estimates (specific format, see input details). ' +
        '</br> </br> <b> The output table is called : LW_RAIL </b> '

inputs = [tableRailTraffic: [name: 'Rail traffic table name', title: 'Rail table name', description: "<b>Name of the Rail traffic table.</b>  </br>  " +
        "<br>  This function recognize the following columns (* mandatory) : </br><ul>" +
        "<li><b> IDTRAFIC </b>* : an identifier. It shall be a primary key (STRING, PRIMARY KEY)</li>" +
        "<li><b> IDTRONCON </b>* : an identifier. It shall be a primary key (STRING, PR)</li>" +
        "<li><b> ENGMOTEUR </b>* : Motor vehicle (STRING)/li>" +
        "<li><b> TYPVOITWAG </b>* : Wagon type (STRING)</li>" +
        "<li><b> TYPVOITWAG </b>* : Number of Wagon (DOUBLE)</li>" +
        "<li><b> VMAX </b>* : Maximum Train speed (DOUBLE) </li>" +
        "<li><b> TDIURNE </b><b> TSOIR </b><b> TNUIT </b> : Hourly average train count (6-18h)(18-22h)(22-6h) (DOUBLE)</li>", type: String.class],
          tableRailGeom: [name: 'Rail Geom table name', title: 'Rail table name', description: "<b>Name of the Rail Geom table.</b>  </br>  " +
                  "<br>  This function recognize the following columns (* mandatory) : </br><ul>" +
                  "<li><b> PK </b>* : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY)</li>" , type: String.class]]

outputs = [result: [name: 'Result output string', title: 'Result output string', description: 'This type of result does not allow the blocks to be linked together.', type: String.class]]

// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

// run the script
def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}

// main function of the script
def exec(Connection connection, input) {

    //Load GeneralTools.groovy
    File generalTools = new File(new File("").absolutePath+"/data_dir/scripts/wpsTools/GeneralTools.groovy")

    //if we are in dev, the path is not the same as for geoserver
    if (new File("").absolutePath.substring(new File("").absolutePath.length() - 11) == 'wps_scripts') {
        generalTools = new File(new File("").absolutePath+"/src/main/groovy/org/noise_planet/noisemodelling/wpsTools/GeneralTools.groovy")
     }

    // Get external tools
    Class groovyClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(generalTools)
    GroovyObject tools = (GroovyObject) groovyClass.newInstance()


    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // output string, the information given back to the user
    String resultString = null

    // print to command window
    System.out.println('Start : Rail Emission from DEN')
    def start = new Date()

    // -------------------
    // Get every inputs
    // -------------------

    String sources_geom_table_name = input['tableRailGeom'] as String
    // do it case-insensitive
    sources_geom_table_name = sources_geom_table_name.toUpperCase()

    String sources_table_traffic_name = input['tableRailTraffic'] as String
    // do it case-insensitive
    sources_table_traffic_name = sources_table_traffic_name.toUpperCase()

    //Get the geometry field of the source table
    TableLocation sourceTableIdentifier = TableLocation.parse(sources_geom_table_name)
    List<String> geomFields = SFSUtilities.getGeometryFields(connection, sourceTableIdentifier)
    if (geomFields.isEmpty()) {
        resultString = String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier)
        throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier))
    }

    //Get the primary key field of the source table
    /*int pkIndex = JDBCUtilities.getIntegerPrimaryKey(connection, sources_table_name)
    if (pkIndex < 1) {
        resultString = String.format("Source table %s does not contain a primary key", sourceTableIdentifier)
        throw new IllegalArgumentException(String.format("Source table %s does not contain a primary key", sourceTableIdentifier))
    }*/


    // -------------------
    // Init table LW_RAIL
    // -------------------

    // Create a sql connection to interact with the database in SQL
    Sql sql = new Sql(connection)

    // drop table LW_RAIL if exists and the create and prepare the table
    sql.execute("drop table if exists LW_RAIL_0;")
    sql.execute("create table LW_RAIL_0 (pr varchar, the_geom geometry," +
            "LWD63 double precision, LWD125 double precision, LWD250 double precision, LWD500 double precision, LWD1000 double precision, LWD2000 double precision, LWD4000 double precision, LWD8000 double precision," +
            "LWE63 double precision, LWE125 double precision, LWE250 double precision, LWE500 double precision, LWE1000 double precision, LWE2000 double precision, LWE4000 double precision, LWE8000 double precision," +
            "LWN63 double precision, LWN125 double precision, LWN250 double precision, LWN500 double precision, LWN1000 double precision, LWN2000 double precision, LWN4000 double precision, LWN8000 double precision);")

    def qry00 = 'INSERT INTO LW_RAIL_0(pr, the_geom,' +
            'LWD63, LWD125, LWD250, LWD500, LWD1000,LWD2000, LWD4000, LWD8000,' +
            'LWE63, LWE125, LWE250, LWE500, LWE1000,LWE2000, LWE4000, LWE8000,' +
            'LWN63, LWN125, LWN250, LWN500, LWN1000,LWN2000, LWN4000, LWN8000) ' +
            'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);'


    sql.execute("drop table if exists LW_RAIL_50;")
    sql.execute("create table LW_RAIL_50 (pr varchar, the_geom geometry," +
            "LWD63 double precision, LWD125 double precision, LWD250 double precision, LWD500 double precision, LWD1000 double precision, LWD2000 double precision, LWD4000 double precision, LWD8000 double precision," +
            "LWE63 double precision, LWE125 double precision, LWE250 double precision, LWE500 double precision, LWE1000 double precision, LWE2000 double precision, LWE4000 double precision, LWE8000 double precision," +
            "LWN63 double precision, LWN125 double precision, LWN250 double precision, LWN500 double precision, LWN1000 double precision, LWN2000 double precision, LWN4000 double precision, LWN8000 double precision);")

    def qry50 = 'INSERT INTO LW_RAIL_50(pr, the_geom,' +
            'LWD63, LWD125, LWD250, LWD500, LWD1000,LWD2000, LWD4000, LWD8000,' +
            'LWE63, LWE125, LWE250, LWE500, LWE1000,LWE2000, LWE4000, LWE8000,' +
            'LWN63, LWN125, LWN250, LWN500, LWN1000,LWN2000, LWN4000, LWN8000) ' +
            'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);'


    // --------------------------------------
    // Start calculation and fill the table
    // --------------------------------------

    // Get Class to compute LW
    LDENConfig ldenConfig = new LDENConfig(LDENConfig.INPUT_MODE.INPUT_MODE_RAIL_FLOW)
    LDENPropagationProcessData ldenData =  new LDENPropagationProcessData(null, ldenConfig)


    // Get size of the table (number of rail segments
    PreparedStatement st = connection.prepareStatement("SELECT COUNT(*) AS total FROM " + sources_geom_table_name)
    SpatialResultSet rs1 = st.executeQuery().unwrap(SpatialResultSet.class)
    int nbSegment = 0
    while (rs1.next()) {
        nbSegment = rs1.getInt("total")
        System.println('The table Rail Geom has ' + nbSegment + ' rail segments.')
    }
    int k = 0
    int currentVal = 0

    st = connection.prepareStatement("SELECT * FROM " + sources_geom_table_name)
    SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)
    while (rs.next()) {
        double[][] results0cm = new double[4][PropagationProcessPathData.third_freq_lvl.size()];
        double[][] results50cm = new double[4][PropagationProcessPathData.third_freq_lvl.size()];
        k++
        currentVal = tools.invokeMethod("ProgressBar", [Math.round(10*k/nbSegment).toInteger(),currentVal])
        //System.println(rs)
        Geometry geo = rs.getGeometry()
        String PR = rs.getString("IDTRONCON")
        st = connection.prepareStatement("SELECT a.*, b.* FROM " + sources_table_traffic_name + " a, " + sources_geom_table_name + " b WHERE a.IDTRONCON = '" + PR.toString() + "' AND b.IDTRONCON = '" + PR.toString() + "'")
        SpatialResultSet rs2 = st.executeQuery().unwrap(SpatialResultSet.class)

        while (rs2.next()) {
            // Compute emission sound level for each rail segment

            ldenConfig.setTrainHeight(0)
            def results0 = ldenData.computeLw(rs2)

            ldenConfig.setTrainHeight(1)
            def results50 = ldenData.computeLw(rs2)

            for (int idfreq = 0; idfreq < PropagationProcessPathData.third_freq_lvl.size(); idfreq++) {
                results0cm[0][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results0cm[0][idfreq])+ComputeRays.dbaToW(results0[0][idfreq]));
                results50cm[0][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results50cm[0][idfreq])+ComputeRays.dbaToW(results50[0][idfreq]));
                results0cm[1][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results0cm[1][idfreq])+ComputeRays.dbaToW(results0[1][idfreq]));
                results50cm[1][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results50cm[1][idfreq])+ComputeRays.dbaToW(results50[1][idfreq]));
                results0cm[2][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results0cm[2][idfreq])+ComputeRays.dbaToW(results0[2][idfreq]));
                results50cm[2][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results50cm[2][idfreq])+ComputeRays.dbaToW(results50[2][idfreq]));
                //results0cm[3][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results0cm[3][idfreq])+ComputeRays.dbaToW(results0[3][idfreq]));
                //results50cm[3][idfreq]=ComputeRays.wToDba(ComputeRays.dbaToW(results50cm[3][idfreq])+ComputeRays.dbaToW(results50[3][idfreq]));
            }

        }

        // fill the LW_RAIL table
        sql.withBatch(100, qry00) { ps ->
            ps.addBatch(PR as String, geo as Geometry,
                    results0cm[0][10] as Double, results0cm[0][11] as Double, results0cm[0][12] as Double,
                    results0cm[0][13] as Double, results0cm[0][14] as Double, results0cm[0][15] as Double,
                    results0cm[0][16] as Double, results0cm[0][17] as Double,
                    results0cm[1][10] as Double, results0cm[1][11] as Double, results0cm[1][12] as Double,
                    results0cm[1][13] as Double, results0cm[1][14] as Double, results0cm[1][15] as Double,
                    results0cm[1][16] as Double, results0cm[1][17] as Double,
                    results0cm[2][10] as Double, results0cm[2][11] as Double, results0cm[2][12] as Double,
                    results0cm[2][13] as Double, results0cm[2][14] as Double, results0cm[2][15] as Double,
                    results0cm[2][16] as Double, results0cm[2][17] as Double)
        }
        sql.withBatch(100, qry50) { ps ->
        ps.addBatch(PR as String, geo as Geometry,
                results50cm[0][10] as Double, results50cm[0][11] as Double, results50cm[0][12] as Double,
                results50cm[0][13] as Double, results50cm[0][14] as Double, results50cm[0][15] as Double,
                results50cm[0][16] as Double, results50cm[0][17] as Double,
                results50cm[1][10] as Double, results50cm[1][11] as Double, results50cm[1][12] as Double,
                results50cm[1][13] as Double, results50cm[1][14] as Double, results50cm[1][15] as Double,
                results50cm[1][16] as Double, results50cm[1][17] as Double,
                results50cm[2][10] as Double, results50cm[2][11] as Double, results50cm[2][12] as Double,
                results50cm[2][13] as Double, results50cm[2][14] as Double, results50cm[2][15] as Double,
                results50cm[2][16] as Double, results50cm[2][17] as Double)
        }

    }

    // Fusion geometry and traffic table

    // Add Z dimension to the rail segments
    sql.execute("UPDATE LW_RAIL_0 SET THE_GEOM = ST_UPDATEZ(The_geom,0.01);")
    sql.execute("UPDATE LW_RAIL_50 SET THE_GEOM = ST_UPDATEZ(The_geom,0.5);")

    // Add primary key to the LW table
    sql.execute("ALTER TABLE  LW_RAIL_0  ADD PK INT AUTO_INCREMENT PRIMARY KEY;")
    sql.execute("ALTER TABLE  LW_RAIL_50  ADD PK INT AUTO_INCREMENT PRIMARY KEY;")


    resultString = "Calculation Done ! The table LW_RAIL_0 and LW_RAIL_50 has been created."

    // print to command window
    System.out.println('\nResult : ' + resultString)
    System.out.println('End : LW_RAIL from Emission')
    System.out.println('Duration : ' + TimeCategory.minus(new Date(), start))

    // print to WPS Builder
    return resultString

}


