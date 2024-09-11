package com.reactnativecompressor.Video.VideoCompressor.video

import android.media.MediaCodec
import android.media.MediaFormat
import org.mp4parser.Box
import org.mp4parser.boxes.iso14496.part12.*

import org.mp4parser.support.Matrix
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class MP4Builder {

    private lateinit var mdat: Mdat
    private lateinit var currentMp4Movie: Mp4Movie
    private lateinit var fos: FileOutputStream
    private lateinit var fc: FileChannel
    private var dataOffset: Long = 0
    private var wroteSinceLastMdat: Long = 0
    private var writeNewMdat = true
    private val track2SampleSizes = HashMap<Track, LongArray>()
    private lateinit var sizeBuffer: ByteBuffer

    @Throws(Exception::class)
    fun createMovie(mp4Movie: Mp4Movie): MP4Builder {
        currentMp4Movie = mp4Movie

        fos = FileOutputStream(mp4Movie.getCacheFile())
        fc = fos.channel

        val fileTypeBox: FileTypeBox = createFileTypeBox()
        fileTypeBox.getBox(fc)
        dataOffset += fileTypeBox.size
        wroteSinceLastMdat = dataOffset

        mdat = Mdat()
        sizeBuffer = ByteBuffer.allocateDirect(4)

        return this
    }

    @Throws(Exception::class)
    private fun flushCurrentMdat() {
        val oldPosition = fc.position()
        fc.position(mdat.getOffset())
        mdat.getBox(fc)
        fc.position(oldPosition)
        mdat.setDataOffset(0)
        mdat.setContentSize(0)
        fos.flush()
    }

    @Throws(Exception::class)
    fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        isAudio: Boolean
    ) {

        if (writeNewMdat) {
            mdat.apply {
                setContentSize(0)
                getBox(fc)
                setDataOffset(dataOffset)
            }
            dataOffset += 16
            wroteSinceLastMdat += 16
            writeNewMdat = false
        }

        mdat.setContentSize(mdat.getContentSize() + bufferInfo.size)
        wroteSinceLastMdat += bufferInfo.size.toLong()

        var flush = false
        if (wroteSinceLastMdat >= 32 * 1024) {
            flushCurrentMdat()
            writeNewMdat = true
            flush = true
            wroteSinceLastMdat = 0
        }

        currentMp4Movie.addSample(trackIndex, dataOffset, bufferInfo)

        if (!isAudio) {
            byteBuf.position(bufferInfo.offset + 4)
            byteBuf.limit(bufferInfo.offset + bufferInfo.size)

            sizeBuffer.position(0)
            sizeBuffer.putInt(bufferInfo.size - 4)
            sizeBuffer.position(0)
            fc.write(sizeBuffer)
        } else {
            byteBuf.position(bufferInfo.offset + 0)
            byteBuf.limit(bufferInfo.offset + bufferInfo.size)
        }

        fc.write(byteBuf)
        dataOffset += bufferInfo.size.toLong()

        if (flush) {
            fos.flush()
        }
    }

    fun addTrack(mediaFormat: MediaFormat, isAudio: Boolean): Int =
        currentMp4Movie.addTrack(mediaFormat, isAudio)

    @Throws(Exception::class)
    fun finishMovie() {
        if (mdat.getContentSize() != 0L) {
            flushCurrentMdat()
        }

        for (track in currentMp4Movie.getTracks()) {
            val samples: List<Sample> = track.getSamples()
            val sizes = LongArray(samples.size)
            for (i in sizes.indices) {
                sizes[i] = samples[i].size
            }
            track2SampleSizes[track] = sizes
        }

        val moov: Box = createMovieBox(currentMp4Movie)
        moov.getBox(fc)

        fos.flush()
        fc.close()
        fos.close()
    }

    private fun createFileTypeBox(): FileTypeBox {
        // completed list can be found at https://www.ftyps.com/
        val minorBrands = listOf(
            "isom", "iso2", "mp41"
        )

        return FileTypeBox("isom", 0, minorBrands)
    }

    private fun gcd(a: Long, b: Long): Long {
        return if (b == 0L) a
        else gcd(b, a % b)
    }

    private fun getTimescale(mp4Movie: Mp4Movie): Long {
        var timescale: Long = 0
        if (mp4Movie.getTracks().isNotEmpty()) {
            timescale = mp4Movie.getTracks().iterator().next().getTimeScale().toLong()
        }

        for (track in mp4Movie.getTracks()) {
            timescale = gcd(
                track.getTimeScale().toLong(),
                timescale
            )
        }

        return timescale
    }

    private fun createMovieBox(movie: Mp4Movie): MovieBox {
        val movieBox = MovieBox()
        val mvhd = MovieHeaderBox()

        mvhd.apply {
            creationTime = Date()
            modificationTime = Date()
            matrix = Matrix.ROTATE_0
        }

        val movieTimeScale = getTimescale(movie)
        var duration: Long = 0

        for (track in movie.getTracks()) {
            val tracksDuration = track.getDuration() * movieTimeScale / track.getTimeScale()
            if (tracksDuration > duration) {
                duration = tracksDuration
            }
        }

        mvhd.duration = duration
        mvhd.timescale = movieTimeScale
        mvhd.nextTrackId = (movie.getTracks().size + 1).toLong()
        movieBox.addBox(mvhd)

        for (track in movie.getTracks()) {
            movieBox.addBox(createTrackBox(track, movie))
        }

        return movieBox
    }

    private fun createTrackBox(track: Track, movie: Mp4Movie): TrackBox {
        val trackBox = TrackBox()
        val tkhd = TrackHeaderBox()
        tkhd.apply {
            isEnabled = true
            isInPreview = true
            isInMovie = true
            matrix = if (track.isAudio()) {
                Matrix.ROTATE_0
            } else {
                movie.getMatrix()
            }
            alternateGroup = 0
            creationTime = track.getCreationTime()
            duration = track.getDuration() * getTimescale(movie) / track.getTimeScale()
            height = track.getHeight().toDouble()
            width = track.getWidth().toDouble()
            layer = 0
            modificationTime = Date()
            trackId = track.getTrackId() + 1
            volume = track.getVolume()
        }
        trackBox.addBox(tkhd)

        val mdia = MediaBox()
        trackBox.addBox(mdia)

        val mdhd = MediaHeaderBox()
        mdhd.apply {
            creationTime = track.getCreationTime()
            duration = track.getDuration()
            timescale = track.getTimeScale().toLong()
            language = "eng"
        }
        mdia.addBox(mdhd)

        val hdlr = HandlerBox()
        hdlr.apply {
            name = if (track.isAudio()) "SoundHandle" else "VideoHandle"
            handlerType = track.getHandler()
        }
        mdia.addBox(hdlr)

        val minf = MediaInformationBox()
        when {
            track.getHandler() == "vide" -> {
                minf.addBox(VideoMediaHeaderBox())
            }
            track.getHandler() == "soun" -> {
                minf.addBox(SoundMediaHeaderBox())
            }
            track.getHandler() == "text" -> {
                minf.addBox(NullMediaHeaderBox())
            }
            track.getHandler() == "subt" -> {
                minf.addBox(SubtitleMediaHeaderBox())
            }
            track.getHandler() == "hint" -> {
                minf.addBox(HintMediaHeaderBox())
            }
            track.getHandler() == "sbtl" -> {
                minf.addBox(NullMediaHeaderBox())
            }
        }

        val dinf = DataInformationBox()
        val dref = DataReferenceBox()
        dinf.addBox(dref)

        val url = DataEntryUrlBox()
        url.flags = 1

        dref.addBox(url)
        minf.addBox(dinf)

        val stbl: Box = createStbl(track)
        minf.addBox(stbl)
        mdia.addBox(minf)

        return trackBox
    }

    private fun createStbl(track: Track): Box {
        val stbl = SampleTableBox()
        createStsd(track, stbl)
        createStts(track, stbl)
        createStss(track, stbl)
        createStsc(track, stbl)
        createStsz(track, stbl)
        createStco(track, stbl)
        return stbl
    }

    private fun createStsd(track: Track, stbl: SampleTableBox) {
        stbl.addBox(track.getSampleDescriptionBox())
    }

    private fun createStts(track: Track, stbl: SampleTableBox) {
        var lastEntry: TimeToSampleBox.Entry? = null
        val entries: MutableList<TimeToSampleBox.Entry> = ArrayList()
        for (delta in track.getSampleDurations()) {
            if (lastEntry != null && lastEntry.delta == delta) {
                lastEntry.count = lastEntry.count + 1
            } else {
                lastEntry = TimeToSampleBox.Entry(1, delta)
                entries.add(lastEntry)
            }
        }
        val stts = TimeToSampleBox()
        stts.entries = entries
        stbl.addBox(stts)
    }

    private fun createStss(track: Track, stbl: SampleTableBox) {
        val syncSamples = track.getSyncSamples()
        if (syncSamples != null && syncSamples.isNotEmpty()) {
            val stss = SyncSampleBox()
            stss.sampleNumber = syncSamples
            stbl.addBox(stss)
        }
    }

    private fun createStsc(track: Track, stbl: SampleTableBox) {
        val stsc = SampleToChunkBox()
        stsc.entries = LinkedList()

        var lastOffset: Long
        var lastChunkNumber = 1
        var lastSampleCount = 0
        var previousWrittenChunkCount = -1

        val samplesCount = track.getSamples().size
        for (a in 0 until samplesCount) {
            val sample = track.getSamples()[a]
            val offset = sample.offset
            val size = sample.size

            lastOffset = offset + size
            lastSampleCount++

            var write = false
            if (a != samplesCount - 1) {
                val nextSample = track.getSamples()[a + 1]
                if (lastOffset != nextSample.offset) {
                    write = true
                }
            } else {
                write = true
            }

            if (write) {
                if (previousWrittenChunkCount != lastSampleCount) {
                    stsc.entries.add(
                        SampleToChunkBox.Entry(
                            lastChunkNumber.toLong(),
                            lastSampleCount.toLong(), 1
                        )
                    )
                    previousWrittenChunkCount = lastSampleCount
                }
                lastSampleCount = 0
                lastChunkNumber++
            }
        }
        stbl.addBox(stsc)
    }

    private fun createStsz(track: Track, stbl: SampleTableBox) {
        val stsz = SampleSizeBox()
        stsz.sampleSizes = track2SampleSizes[track]
        stbl.addBox(stsz)
    }

    private fun createStco(track: Track, stbl: SampleTableBox) {
        val chunksOffsets = ArrayList<Long>()
        var lastOffset: Long = -1
        for (sample in track.getSamples()) {
            val offset = sample.offset
            if (lastOffset != -1L && lastOffset != offset) {
                lastOffset = -1
            }
            if (lastOffset == -1L) {
                chunksOffsets.add(offset)
            }
            lastOffset = offset + sample.size
        }
        val chunkOffsetsLong = LongArray(chunksOffsets.size)
        for (a in chunksOffsets.indices) {
            chunkOffsetsLong[a] = chunksOffsets[a]
        }
        val stco = StaticChunkOffsetBox()
        stco.chunkOffsets = chunkOffsetsLong
        stbl.addBox(stco)
    }
}
