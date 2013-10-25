/*
 *  Copyright (C) 2011 in-somnia
 * 
 *  This file is part of JAAD.
 * 
 *  JAAD is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  JAAD is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;
import net.sourceforge.jaad.mp4.api.VideoTrack;
import net.sourceforge.jaad.util.wav.WaveFileWriter;

/**
 * Command line example, that can decode an AAC file to a WAVE file.
 * @author in-somnia
 */
public class Main {
	private static final String USAGE = "Usage:\n"
			+ "	java -jar ismt-patcher.jar <filename.ismt> [...]\n"
			+ "\n"
			+ "Attempts to repair any invalid TFHD (TrackFragmentHeaderData) boxes within an ISMT close-captioning data file\n";

	public static void main(String[] args) {
		File lastProcessedFile = null;
		File lastPatchedFile = null;
		try {
			if (args.length < 1)
				printUsage();
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-mp4"))
					continue;
				File inputFile = new File(args[i]);
				File outputFile = new File(args[i] + ".patch");
				lastProcessedFile = inputFile;
				lastPatchedFile = outputFile;
				System.out.println("INFO: ---------------------------------------------------------------------------------------------");
				if (!inputFile.exists()) {
					System.out.println("WARNING: skipping non-existent input file " + inputFile.toString());
					continue;
				}
				System.out.println("INFO: processing input file " + inputFile.toString() + " output file will be " + outputFile.toString());
				copyFile(inputFile, outputFile);
				System.out.println("INFO: scanning input file for invalid tfhd box lengths...");
				decodeMP4(inputFile.toString(), outputFile.toString());
				System.out.println("INFO: finished processing file " + inputFile.toString() + " patched file is " + outputFile.toString());
			}
		}
		catch(Exception e) {
			try {
				if (lastPatchedFile.exists())
					lastPatchedFile.delete();
			} catch (Exception ex) {
				// silently ignore these specific exceptions, since we're just removing the temp patch file that failed
			}
			System.err.print("ERROR: processing of " + lastProcessedFile + " failed: ");
			e.printStackTrace();
		}
	}

	private static void printUsage() {
		System.out.println(USAGE);
		System.exit(1);
	}

	private static void decodeMP4(String in, String out) throws Exception {
		//WaveFileWriter wav = null;
		try {
			final MP4Container cont = new MP4Container(new RandomAccessFile(in, "r"), in + ".patch");
			final Movie movie = cont.getMovie();
			
			//final List<Track> vids = movie.getTracks(VideoTrack.VideoCodec.MP4_ASP);
			//if (vids.isEmpty()) 
			//	throw new Exception("movie does not contain any MP4 track");
			
			final List<Track> tracks = movie.getTracks(VideoTrack.VideoCodec.UNKNOWN_VIDEO_CODEC);
			//final List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
			if (tracks.isEmpty()) 
				throw new Exception("movie does not contain any AAC track");
			
			// write the text parser here
			// or skip to the end and parse the mfro, mfra, tfra
			
			//final AudioTrack track = (AudioTrack) tracks.get(0);
			final VideoTrack track = (VideoTrack) tracks.get(0);
			
			//wav = new WaveFileWriter(new File(out), track.getSampleRate(), track.getChannelCount(), track.getSampleSize());

			final Decoder dec = new Decoder(track.getDecoderSpecificInfo());

			Frame frame;
			final SampleBuffer buf = new SampleBuffer();
			while(track.hasMoreFrames()) {
				frame = track.readNextFrame();
				dec.decodeFrame(frame.getData(), buf);
				System.out.println(buf.getData());
				//wav.write(buf.getData());
			}
		}
		finally {
			//if(wav!=null) wav.close();
		}
	}

	private static void decodeAAC(String in, String out) throws IOException {
		WaveFileWriter wav = null;
		try {
			final ADTSDemultiplexer adts = new ADTSDemultiplexer(new FileInputStream(in));
			final Decoder dec = new Decoder(adts.getDecoderSpecificInfo());

			final SampleBuffer buf = new SampleBuffer();
			byte[] b;
			while(true) {
				b = adts.readNextFrame();
				dec.decodeFrame(b, buf);

				if(wav==null) wav = new WaveFileWriter(new File(out), buf.getSampleRate(), buf.getChannels(), buf.getBitsPerSample());
				wav.write(buf.getData());
			}
		}
		finally {
			if(wav!=null) wav.close();
		}
	}
	
	private static void copyFile(File source, File dest) throws IOException {
		FileInputStream input = null;
		FileOutputStream output = null;
		try {
			input = new FileInputStream(source);
			output = new FileOutputStream(dest);
			byte[] buf = new byte[4096];
			int bytesRead;
			while ((bytesRead = input.read(buf)) > 0) {
				output.write(buf, 0, bytesRead);
			}
		} finally {
			input.close();
			output.close();
		}
	}
}
