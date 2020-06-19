package com.googlecode.mp4parser.stuff;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;
import org.mp4parser.Container;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.BitWriterBuffer;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderConfigDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderSpecificInfo;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.ESDescriptor;
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox;
import org.mp4parser.boxes.iso14496.part12.CompositionTimeToSample;
import org.mp4parser.boxes.iso14496.part12.HandlerBox;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.MediaHeaderBox;
import org.mp4parser.boxes.iso14496.part12.SampleSizeBox;
import org.mp4parser.boxes.iso14496.part12.SampleTableBox;
import org.mp4parser.boxes.iso14496.part12.SampleToChunkBox;
import org.mp4parser.boxes.iso14496.part12.StaticChunkOffsetBox;
import org.mp4parser.boxes.iso14496.part12.SyncSampleBox;
import org.mp4parser.boxes.iso14496.part12.TimeToSampleBox;
import org.mp4parser.boxes.iso14496.part12.TrackBox;
import org.mp4parser.boxes.iso14496.part14.ESDescriptorBox;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;
import org.mp4parser.boxes.sampleentry.AudioSampleEntry;
import org.mp4parser.boxes.sampleentry.VisualSampleEntry;
import org.mp4parser.muxer.FileRandomAccessSourceImpl;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.RandomAccessSource;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.container.mp4.Mp4SampleList;
import org.mp4parser.tools.IsoTypeReader;
import org.mp4parser.tools.IsoTypeWriter;
import org.mp4parser.tools.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class ReadWriteExample {


    public static void main(String[] args) throws IOException {

        String inputFile = "/workplace/Acuity/testdata/input/vogels.mp4";
        String outputFile = "/workplace/Acuity/testdata/input/vogels_converted_15_June_5_30PM.mkv";
        transmuxMP4ToMKV(inputFile, outputFile);

    }

    public static void transmuxMP4ToMKV(String inputFile, String outputFile) throws IOException {
        // MP4 boxes
        RandomAccessSource mp4RandomAccessSource = new FileRandomAccessSourceImpl(new RandomAccessFile(inputFile, "r"));
        ReadableByteChannel readableByteChannel = new FileInputStream(inputFile).getChannel();
        IsoFile mp4File = new IsoFile(inputFile);

        File targetFile = new File(outputFile);
        if (targetFile.exists()) {
            targetFile.delete();
        }
        MatroskaFileWriter mkvWriter = new MatroskaFileWriter(new FileDataWriter(outputFile));
        List<TrackBox> mp4Tracks = mp4File.getBoxes(TrackBox.class, true);
        Map<String, TrackBox> trackBoxMap = mp4Tracks.stream()
                .filter(trackBox -> trackBox.getMediaBox().getHandlerBox().getHandlerType() != null)
                .collect(Collectors.toMap((track -> track.getMediaBox().getHandlerBox().getHandlerType()), Function.identity()));

        // MP4 Audio boxes
        TrackBox mp4AudioTrack = trackBoxMap.get("soun");
        MediaHeaderBox mp4AudioMediaHeaderBox = getBox(mp4AudioTrack.getBoxes(MediaHeaderBox.class, true));
        SampleTableBox mp4AudioSampleTableBox = getBox(mp4AudioTrack.getBoxes(SampleTableBox.class, true));
        AudioSampleEntry mp4AudioSampleEntry = getBox(mp4AudioSampleTableBox.getBoxes(AudioSampleEntry.class, true));
        SampleSizeBox mp4AudioSampleSizeBox = mp4AudioSampleTableBox.getSampleSizeBox();
        TimeToSampleBox mp4AudioTimeToSampleBox = mp4AudioSampleTableBox.getTimeToSampleBox();
        Optional<CompositionTimeToSample> mp4AudioCompositeOffset = Optional.ofNullable(mp4AudioSampleTableBox.getCompositionTimeToSample());
        Mp4SampleList mp4AudioSamples = new Mp4SampleList(mp4AudioTrack.getTrackHeaderBox().getTrackId(), mp4File, mp4RandomAccessSource);
        ESDescriptorBox mp4audioESBox = getBox(mp4AudioSampleEntry.getBoxes(ESDescriptorBox.class));
        DecoderConfigDescriptor mp4AudioDecoderConfig = mp4audioESBox.getEsDescriptor().getDecoderConfigDescriptor();

        // MP4 Video boxes
        TrackBox mp4VideoTrack = trackBoxMap.get("vide");
        MediaHeaderBox mp4VideoMediaHeaderBox = getBox(mp4VideoTrack.getBoxes(MediaHeaderBox.class, true));
        SampleTableBox mp4VideoSampleTableBox = getBox(mp4VideoTrack.getBoxes(SampleTableBox.class, true));
        VisualSampleEntry mp4VisualSampleEntry = getBox(mp4VideoTrack.getBoxes(VisualSampleEntry.class, true));
        AvcConfigurationBox mp4AvcConfigurationBox = getBox(mp4VisualSampleEntry.getBoxes(AvcConfigurationBox.class, true));
        SampleSizeBox mp4VideoSampleSizeBox = mp4VideoSampleTableBox.getSampleSizeBox();
        TimeToSampleBox mp4VideoTimeToSampleBox = mp4VideoSampleTableBox.getTimeToSampleBox();
        Optional<CompositionTimeToSample> mp4VideoCompositeOffset = Optional.ofNullable(mp4VideoSampleTableBox.getCompositionTimeToSample());
        Mp4SampleList mp4VideoSamples = new Mp4SampleList(mp4VideoTrack.getTrackHeaderBox().getTrackId(), mp4File, mp4RandomAccessSource);
        SyncSampleBox mp4VideoSyncSampleBox = mp4VideoTrack.getSampleTableBox().getSyncSampleBox();
        Set<Long> videoKeyFramesSet = Arrays.stream(mp4VideoSyncSampleBox.getSampleNumber()).boxed()
                                      .collect(Collectors.toSet());

        // MKV Elements
        // MKV Video
        final int videoTrackNo = 1;
        MatroskaFileTrack mkvVideoTrack = new MatroskaFileTrack();
        mkvVideoTrack.setTrackNo(videoTrackNo);
        mkvVideoTrack.setTrackUID(videoTrackNo);
        mkvVideoTrack.setFlagLacing(false);
        mkvVideoTrack.setLanguage(mp4VideoMediaHeaderBox.getLanguage());
        mkvVideoTrack.setCodecID("V_MPEG4/ISO/AVC");
        mkvVideoTrack.setTrackType(MatroskaFileTrack.TrackType.VIDEO);
        MatroskaFileTrack.MatroskaVideoTrack mkvVideoSubTrack = new MatroskaFileTrack.MatroskaVideoTrack();
        mkvVideoSubTrack.setPixelWidth((short) mp4VisualSampleEntry.getWidth());
        mkvVideoSubTrack.setPixelHeight((short) mp4VisualSampleEntry.getHeight());
        mkvVideoSubTrack.setDisplayWidth((short) mp4VisualSampleEntry.getWidth());
        mkvVideoSubTrack.setDisplayHeight((short) mp4VisualSampleEntry.getHeight());
        mkvVideoTrack.setVideo(mkvVideoSubTrack);
        mkvVideoTrack.setCodecPrivate(getVideoCpd(mp4AvcConfigurationBox));
        mkvVideoTrack.setDefaultDuration(33366 * 1000);
        mkvWriter.addTrack(mkvVideoTrack);

        // MKV Audio
        final int audioTrackNo = 2;
        MatroskaFileTrack mkvAudioTrack = new MatroskaFileTrack();
        mkvAudioTrack.setTrackNo(audioTrackNo);
        mkvAudioTrack.setTrackUID(audioTrackNo);
        mkvAudioTrack.setFlagLacing(false);
        mkvAudioTrack.setLanguage(mp4AudioMediaHeaderBox.getLanguage());
        mkvAudioTrack.setCodecID("A_AAC");
        mkvAudioTrack.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
        MatroskaFileTrack.MatroskaAudioTrack mkvAudioSubTrack = new MatroskaFileTrack.MatroskaAudioTrack();
        mkvAudioSubTrack.setChannels((short) mp4AudioSampleEntry.getChannelCount());
        mkvAudioSubTrack.setSamplingFrequency(mp4AudioSampleEntry.getSampleRate());
        mkvAudioSubTrack.setOutputSamplingFrequency(mp4AudioSampleEntry.getSampleRate());
        mkvAudioSubTrack.setBitDepth(mp4AudioSampleEntry.getSampleSize());
        mkvAudioTrack.setAudio(mkvAudioSubTrack);
        final ByteBuffer audioCpd = mp4AudioDecoderConfig.getDecoderSpecificInfo() != null
                ? mp4AudioDecoderConfig.getDecoderSpecificInfo().serialize()
                : ByteBuffer.wrap(mp4AudioDecoderConfig.getAudioSpecificInfo().getConfigBytes());
        mkvAudioTrack.setCodecPrivate(audioCpd);
        mkvWriter.addTrack(mkvAudioTrack);

        final long mp4VideoTimescale = mp4VideoMediaHeaderBox.getTimescale();
        final long mp4AudioTimescale = mp4AudioMediaHeaderBox.getTimescale();
        long totalSampleCount = mp4VideoSampleSizeBox.getSampleCount() + mp4AudioSampleSizeBox.getSampleCount();
        int audioSampleNo = 1;
        int videoSampleNo = 1;
        double videoDtsSecs = 0;
        double audioDtsSecs = 0;
        mkvWriter.setTimecodeScale(1_000_000); // Millis
        while ((audioSampleNo + videoSampleNo) <= totalSampleCount) {
            if ((audioDtsSecs < videoDtsSecs && audioSampleNo <= mp4AudioSampleSizeBox.getSampleCount())
                    || videoSampleNo > mp4VideoSampleSizeBox.getSampleCount()) {
                audioDtsSecs = putMkvFrame(mkvWriter, mp4AudioTimeToSampleBox, mp4AudioCompositeOffset,
                        mp4AudioSamples, audioTrackNo, mp4AudioTimescale, true, audioSampleNo, audioDtsSecs);
                audioSampleNo++;

            }
            if (videoDtsSecs <= audioDtsSecs && videoSampleNo <= mp4VideoSampleSizeBox.getSampleCount()
                    || audioSampleNo > mp4AudioSampleSizeBox.getSampleCount()) {
                boolean isKeyFrame = videoKeyFramesSet.contains((long) videoSampleNo);
                videoDtsSecs = putMkvFrame(mkvWriter, mp4VideoTimeToSampleBox, mp4VideoCompositeOffset,
                        mp4VideoSamples, videoTrackNo, mp4VideoTimescale, isKeyFrame, videoSampleNo, videoDtsSecs);
                videoSampleNo++;

            }
        }
        mkvWriter.flush();
        mkvWriter.close();
        System.out.println("Successfully transmuxed");
    }

    private static double putMkvFrame(MatroskaFileWriter mkvWriter,
                                      TimeToSampleBox timeToSampleBox,
                                      Optional<CompositionTimeToSample> compositeOffset,
                                      Mp4SampleList samples,
                                      int trackNo,
                                      long timescale,
                                      boolean isKeyFrame,
                                      int sampleNo,
                                      double dtsSecs) {
        double ptsSecs = dtsSecs + ((double) getCompositionOffsetForSample(compositeOffset, sampleNo) / timescale);
        final MatroskaFileFrame frame = new MatroskaFileFrame();
        ByteBuffer sample = samples.get(sampleNo - 1).asByteBuffer();
        sample.rewind();
        frame.setData(sample);
        double timecode = ptsSecs * 1000;
        //System.out.println("Track no : " + trackNo + " Key : " + isKeyFrame + " Sample size : " + sample.limit() +  " timecode : " + Math.round(timecode) );
        frame.setTimecode(Math.round(timecode)); // convert from  secs to millis
        frame.setKeyFrame(isKeyFrame);
        frame.setDiscardable(false);
        if (isKeyFrame && trackNo == 1) {
            mkvWriter.flush();
        }
        frame.setTrackNo(trackNo);
        mkvWriter.addFrame(frame);
        dtsSecs = dtsSecs + ((double) getDtsDeltaForSample(timeToSampleBox, sampleNo) / timescale);
        return dtsSecs;
    }

    private static long getDtsDeltaForSample(TimeToSampleBox timeToSampleBox, final long sampleNo) {
        int totalSamples = 0;
        for (TimeToSampleBox.Entry entry : timeToSampleBox.getEntries()) {
            totalSamples += entry.getCount();
            if (sampleNo <= totalSamples) {
                return entry.getDelta();
            }
        }
        throw new RuntimeException("Unable to find DTS delta for sample no : " + sampleNo);
    }

    private static long getCompositionOffsetForSample(Optional<CompositionTimeToSample> compositionTimeToSample,
                                                      final long sampleNo) {
        if (compositionTimeToSample.isPresent()) {
            int totalSamples = 0;
            for (CompositionTimeToSample.Entry entry : compositionTimeToSample.get().getEntries()) {
                totalSamples += entry.getCount();
                if (sampleNo <= totalSamples) {
                    return entry.getOffset();
                }
            }
            throw new RuntimeException("Unable to find comp offset for sample no : " + sampleNo);
        }
        return 0;
    }

    private static ByteBuffer getVideoCpd(AvcConfigurationBox avccBox) {
        ByteBuffer cpd = ByteBuffer.allocate((int) avccBox.getContentSize());
//        BitWriterBuffer cpdBuffer = new BitWriterBuffer(cpd);
//        cpdBuffer.writeBits(7, 3); // sps pads
//        cpdBuffer.writeBits(avccBox.getSequenceParameterSets().size(), 5); // no of sps sets
//        for (ByteBuffer sps : avccBox.getSequenceParameterSets()) {
//            sps.rewind();
//            IsoTypeWriter.writeUInt16(cpd, sps.remaining());
//            cpd.put(sps);
//        }
//        IsoTypeWriter.writeUInt8(cpd, avccBox.getPictureParameterSets().size()); // no of pps sets
//        for (ByteBuffer pps : avccBox.getPictureParameterSets()) {
//            pps.rewind();
//            IsoTypeWriter.writeUInt16(cpd, pps.remaining());
//            cpd.put(pps);
//        }
        avccBox.getContent(cpd);
        cpd.rewind();
        return cpd;
    }

    private static <T> T getBox(List<T> boxes) {
        return getBoxOptional(boxes)
                .orElseThrow(() -> new RuntimeException("Box not found !"));
    }

    private static <T> Optional<T> getBoxOptional(List<T> boxes) {
        return boxes.stream().findAny();
    }

    private static void example() throws IOException {
        //Movie video = mc.build(Channels.newChannel(ReadWriteExample.class.getResourceAsStream("/smoothstreaming/video-128h-75kbps.mp4")));
        Movie video = MovieCreator.build("/workplace/Acuity/testdata/input/vogels.mp4");

        //IsoFile out1 = new FragmentedMp4Builder().build(video);
        Container out2 = new DefaultMp4Builder().build(video);


       /* long starttime1 = System.currentTimeMillis();
        FileChannel fc1 = new RandomAccessFile("video-128h-75kbps.fmp4", "rw").getChannel();
        fc1.position(0);
        out1.getBox(fc1);
        long size1 = fc1.size();
        fc1.truncate(fc1.position());
        fc1.close();
        System.err.println("Writing " + size1 / 1024 / 1024 + "MB took " + (System.currentTimeMillis() - starttime1));*/

        long starttime2 = System.currentTimeMillis();
        FileChannel fc2 = new RandomAccessFile("output_uvu.mp4", "rw").getChannel();
        out2.writeContainer(fc2);
        long size2 = fc2.size();
        fc2.truncate(fc2.position());
        fc2.close();
        System.err.println("Writing " + size2 / 1024 / 1024 + "MB took " + (System.currentTimeMillis() - starttime2));
    }

    /*
      public static void main(String[] args) throws IOException {
        MovieCreator mc = new MovieCreator();

        Movie video = mc.build(new FileInputStream("/media/scratch/qualitaetstest_cinovu_sherminfiles/abendlandinchristenhand_1039kps.mp4").getChannel());

        IsoFile out1 = new FragmentedMp4Builder().build(video);
        IsoFile out2 = new DefaultMp4Builder().build(video);


        FileChannel fc1 = new RandomAccessFile("output.fmp4", "rw").getChannel();
        fc1.position(0);
        out1.getBox(fc1);
        fc1.truncate(fc1.position());
        fc1.close();

        FileChannel fc2 = new RandomAccessFile("output.mp4", "rw").getChannel();
        fc2.position(0);
        out2.getBox(fc2);
        fc2.truncate(fc2.position());
        fc2.close();


    }


     */


}
