package com.googlecode.mp4parser.boxes.basemediaformat;

import com.googlecode.mp4parser.boxes.AbstractTrackEncryptionBoxTest;
import org.junit.Before;
import org.junit.Test;
import org.mp4parser.boxes.iso23001.part7.TrackEncryptionBox;

import java.io.IOException;


public class TrackEncryptionBoxTest extends AbstractTrackEncryptionBoxTest {


    @Before
    public void setUp() throws Exception {
        tenc = new TrackEncryptionBox();
    }

    @Test
    public void testRoundTrip() throws IOException {
        super.testRoundTrip();
    }
}
