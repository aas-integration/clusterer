/*
 * jimple2boogie - Translates Jimple (or Java) Programs to Boogie
 * Copyright (C) 2013 Martin Schaeaeaeaeaeaeaeaeaef and Stephan Arlt
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package clusterer;

import java.util.LinkedList;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

/**
 * Options
 * 
 * @author schaef
 */
public class Options {
	
	@Option(name = "-classinfo", usage = "Print out a json file that contains info about all classes including fields and subtypes.")
	public String classInfoFileName = "class_info.json";
	
	
	@Option(name = "-dirs", handler = StringArrayOptionHandler.class, required = true,
			usage = "List of directories of classdirs to be passed to soot.")
	public List<String> classDirList = new LinkedList<String>();

	@Option(name = "-out", usage = "Output file name. Default: clusters.json")
	public String outFileName = "clusters.json";

	
	@Option(name = "-cp", usage = "Classpath (optional)")
	private String classpath=null;

	@Option(name = "-cs", usage = "Clustering strategy between 1 and 4. Default is 3.")
	public int clusteringStrategy = 3;

	@Option(name = "-cfm", usage = "Produce json map from class name to list of fields of that type (experimental).")
	public String classFieldMapFileName = null;

	@Option(name = "-wfm", usage = "Produce json map from relevant word to list of field names (must be used with -cfm option).")
	public String wordFieldMapFileName = null;

	@Option(name = "-v", usage = "Logging enabled.")
	public boolean verbose = false;

	
	//================ singleton stuff =================
	private static Options options;

	public static void resetInstance() {
		options = null;
	}

	public static Options v() {
		if (null == options) {
			options = new Options();
		}
		return options;
	}

	private Options() {
	}

}
