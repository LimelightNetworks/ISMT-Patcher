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
package net.sourceforge.jaad.mp4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.jaad.mp4.api.Brand;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.boxes.Box;
import net.sourceforge.jaad.mp4.boxes.BoxFactory;
import net.sourceforge.jaad.mp4.boxes.BoxTypes;
import net.sourceforge.jaad.mp4.boxes.impl.FileTypeBox;
import net.sourceforge.jaad.mp4.boxes.impl.ProgressiveDownloadInformationBox;
import net.sourceforge.jaad.mp4.boxes.impl.TrackFragmentHeaderBox;

/**
 * The MP4Container is the central class for the MP4 demultiplexer. It reads the
 * container and gives access to the containing data.
 *
 * The data source can be either an <code>InputStream</code> or a
 * <code>RandomAccessFile</code>. Since the specification does not decree a
 * specific order of the content, the data needed for parsing (the sample
 * tables) may be at the end of the stream. In this case, random access is
 * needed and reading from an <code>InputSteam</code> will cause an exception.
 * Thus, whenever possible, a <code>RandomAccessFile</code> should be used for 
 * local files. Parsing from an <code>InputStream</code> is useful when reading 
 * from a network stream.
 *
 * Each <code>MP4Container</code> can return the used file brand (file format
 * version). Optionally, the following data may be present:
 * <ul>
 * <li>progressive download informations: pairs of download rate and playback
 * delay, see {@link #getDownloadInformationPairs() getDownloadInformationPairs()}</li>
 * <li>a <code>Movie</code></li>
 * </ul>
 *
 * Additionally it gives access to the underlying MP4 boxes, that can be 
 * retrieved by <code>getBoxes()</code>. However, it is not recommended to 
 * access the boxes directly.
 * 
 * @author in-somnia
 */
public class MP4Container {

	static {
		Logger log = Logger.getLogger("MP4 API");
		for(Handler h : log.getHandlers()) {
			log.removeHandler(h);
		}
		log.setLevel(Level.WARNING);

		final ConsoleHandler h = new ConsoleHandler();
		h.setLevel(Level.ALL);
		log.addHandler(h);
	}
	private final MP4InputStream in;
	private final List<Box> boxes;
	private Brand major, minor;
	private Brand[] compatible;
	private FileTypeBox ftyp;
	private ProgressiveDownloadInformationBox pdin;
	private Box moov;
	private String patchFile;
	private TrackFragmentHeaderBox tfhd;
	private Movie movie;

	public MP4Container(InputStream in) throws IOException {
		this.in = new MP4InputStream(in);
		boxes = new ArrayList<Box>();

		readContent();
	}
	
	public MP4Container(RandomAccessFile in) throws IOException {
		this.in = new MP4InputStream(in);
		boxes = new ArrayList<Box>();

		readContent();
	}

	public MP4Container(RandomAccessFile in, String patchFile) throws IOException {
		this.in = new MP4InputStream(in);
		this.patchFile = patchFile;
		boxes = new ArrayList<Box>();

		readContent();
	}

	private void readContent() throws IOException {
		//read all boxes
		Box box = null;
		long type;
		int boxesFixed = 0;
		while(in.hasLeft()) {
			int length = (tfhd != null) ? (int)tfhd.getDefaultSampleSize() : 0;
			box = BoxFactory.parseBox(null, in, length);
			if(boxes.isEmpty()&&box.getType()!=BoxTypes.FILE_TYPE_BOX) throw new MP4Exception("no MP4 signature found");
			boxes.add(box);

			type = box.getType();
			if(type==BoxTypes.FILE_TYPE_BOX) {
				if(ftyp==null) ftyp = (FileTypeBox) box;
			}
			else if(type==BoxTypes.MOVIE_BOX) {
				if(movie==null) moov = box;
			}
			else if(type==BoxTypes.PROGRESSIVE_DOWNLOAD_INFORMATION_BOX) {
				if(pdin==null) pdin = (ProgressiveDownloadInformationBox) box;
			}
			else if(type==BoxTypes.MOVIE_FRAGMENT_BOX) {
				tfhd = (TrackFragmentHeaderBox) box.getChildren(BoxTypes.TRACK_FRAGMENT_BOX).get(0).getChild(BoxTypes.TRACK_FRAGMENT_HEADER_BOX);
			}
			else if(type==BoxTypes.MEDIA_DATA_BOX) {
				// fix the length here
				if (patchFile != null) {
					File f = new File(patchFile);
					if (f.exists() && tfhd != null && tfhd.getDefaultSampleSizeOffset() > 0) {
						int actualLength = (int)box.getSize() - 8;
						if (tfhd.getDefaultSampleSize() != actualLength) {
							System.out.println("INFO: repairing invalid tfhd box length " + tfhd.getDefaultSampleSize() + " at offset " + tfhd.getDefaultSampleSizeOffset() + ", should be " + actualLength);
							RandomAccessFile fil = new RandomAccessFile(patchFile, "rw");
							fil.seek(tfhd.getDefaultSampleSizeOffset());
							fil.writeInt(actualLength);
							fil.close();
							boxesFixed++;
						}
					}
				}
			}
		}
		System.out.println("INFO: Total invalid tfhd boxes found and fixed: " + boxesFixed);
	}

	public Brand getMajorBrand() {
		if(major==null) major = Brand.forID(ftyp.getMajorBrand());
		return major;
	}

	public Brand getMinorBrand() {
		if(minor==null) minor = Brand.forID(ftyp.getMajorBrand());
		return minor;
	}

	public Brand[] getCompatibleBrands() {
		if(compatible==null) {
			final String[] s = ftyp.getCompatibleBrands();
			compatible = new Brand[s.length];
			for(int i = 0; i<s.length; i++) {
				compatible[i] = Brand.forID(s[i]);
			}
		}
		return compatible;
	}

	//TODO: pdin, movie fragments??
	public Movie getMovie() {
		if(moov==null) return null;
		else if(movie==null) movie = new Movie(moov, in);
		return movie;
	}

	public List<Box> getBoxes() {
		return Collections.unmodifiableList(boxes);
	}
}
