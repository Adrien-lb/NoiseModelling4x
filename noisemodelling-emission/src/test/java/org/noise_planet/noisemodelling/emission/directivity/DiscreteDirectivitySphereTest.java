/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.emission.directivity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.directivity.cnossos.RailwayCnossosDirectivitySphere;

/**
 * Test the Directivity Sphere classes
 * @author Nicolas Fortin, Université Gustave Eiffel
 */

public class DiscreteDirectivitySphereTest {
    final static double[] freqTest = new double[]{50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000};

    @Test
    public void testInsert() {
        DiscreteDirectivitySphere d = new DiscreteDirectivitySphere(1, freqTest);

        RailwayCnossosDirectivitySphere att = new RailwayCnossosDirectivitySphere(new LineSource("TRACTIONB"));

        for (int yaw = 0; yaw < 360; yaw += 5) {
            float phi = (float) Math.toRadians(yaw);
            for (int pitch = -85; pitch < 90; pitch += 5) {
                float theta = (float) Math.toRadians(pitch);
                double[] attSpectrum = new double[freqTest.length];
                for (int idFreq = 0; idFreq < freqTest.length; idFreq++) {
                    attSpectrum[idFreq] = att.getAttenuation(freqTest[idFreq], phi, theta);
                }
                d.addDirectivityRecord(theta, phi,
                        attSpectrum);
            }
        }

        // test nearest neighbors

        assertEquals(new DirectivityRecord((float) Math.toRadians(30),
                        (float) Math.toRadians(25), null),
                d.getRecord((float) Math.toRadians(31), (float) Math.toRadians(26), 0));

        assertEquals(new DirectivityRecord((float) Math.toRadians(85),
                        (float) Math.toRadians(0), null),
                d.getRecord((float) Math.toRadians(88), (float) Math.toRadians(358), 0));

        assertEquals(new DirectivityRecord((float) Math.toRadians(-85),
                        (float) Math.toRadians(0), null),
                d.getRecord((float) Math.toRadians(-89), (float) Math.toRadians(2), 0));


        // Test bilinear interpolation
        DirectivityRecord r = d.getRecord((float) Math.toRadians(26),
                (float) Math.toRadians(31), 1);
        assertEquals(new DirectivityRecord((float) Math.toRadians(26),
                (float) Math.toRadians(31), null), r);
        assertArrayEquals(new double[]{-5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63,-5.63, -5.63, -5.63, -5.63,
                        -5.63, -5.63, -5.63, -5.63,-5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63}, r.getAttenuation(),
                0.1);

        // check for non-existing frequency
        assertArrayEquals(new double[]{-5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63,
                        -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63, -5.63},
                d.getAttenuationArray(new double[]{50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000,
                                1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000},
                        (float) Math.toRadians(31), (float) Math.toRadians(26)), 0.1);


        assertEquals(-5.63, d.getAttenuation(freqTest[0], (float) Math.toRadians(31),
                (float) Math.toRadians(26)), 0.1);
        assertEquals(-5.63, d.getAttenuation(freqTest[1], (float) Math.toRadians(31),
                (float) Math.toRadians(26)), 0.1);
        assertEquals(-5.63, d.getAttenuation(freqTest[1] + 1, (float) Math.toRadians(31),
                (float) Math.toRadians(26)), 0.1);
        assertEquals(-5.63, d.getAttenuation(freqTest[0] - 1, (float) Math.toRadians(31),
                (float) Math.toRadians(26)), 0.1);
        assertEquals(-5.63, d.getAttenuation(freqTest[freqTest.length - 1] + 1, (float) Math.toRadians(31),
                (float) Math.toRadians(26)), 0.1);

    }

}