package com.googlecode.mp4parser.boxes.piff;

import com.googlecode.mp4parser.boxes.AbstractTrackEncryptionBoxTest;
import org.junit.Before;
import org.junit.Test;
import org.mp4parser.boxes.microsoft.PiffTrackEncryptionBox;

import java.io.IOException;


public class PiffTrackEncryptionBoxTest extends AbstractTrackEncryptionBoxTest {


    @Before
    public void setUp() throws Exception {
        tenc = new PiffTrackEncryptionBox();
    }

    @Test
    public void testRoundTrip() throws IOException {
        super.testRoundTrip();
    }
}
