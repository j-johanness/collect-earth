package org.openforis.collect.earth.grid;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuerySigrid {

	JDBCStore database = new JDBCStore();
	CSVStore csv = new CSVStore();
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public ResultSet getSigridForShapefile( File shapefile, Integer gridDistance, Integer grid ){
		return null;
	}

	public ResultSet getSigridForBoundingBox( Double[] boundingBox, Integer gridDistance, Integer grid ){
		return database.getPlots(grid, boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3], gridDistance);
	}

	// Bounding box array : Double maxX, Double maxY, Double minX, Double minY
	public void writeCsvFromBoundingBox( Double[] boundingBox, Integer gridDistance, Integer grid, String prefix  ) {


		ResultSet results = getSigridForBoundingBox(boundingBox, gridDistance, grid);
		csv.initializeStore(gridDistance, prefix);
		try {
			while(results.next()) {
				csv.savePlot(
						results.getInt("ycoordinate") * 1d /JDBCStore.SCALING_FACTOR *1d, 
						results.getInt("xcoordinate") * 1d  /JDBCStore.SCALING_FACTOR * 1d , 
						results.getInt("row"), 
						results.getInt("col")
						);
			}
		} catch (SQLException e) {
			logger.error("Error readig results from DB", e);
		} finally {
			csv.closeStore();
			database.closeStore();
		}
	}

	public static void main(String[] args) {
		QuerySigrid querySigrid = new QuerySigrid();
		//Double maxX, Double maxY, Double minX, Double minY
		//querySigrid.writeCsvFromBoundingBox( new Double[] {38d,38d,-17d,14d}, 1000, 10, "NorthAfrica");
		//querySigrid.writeCsvFromBoundingBox( new Double[] {60d,38d,-17d,-35d}, 1000, 10, "AllAfricaAfrica");
		//querySigrid.writeCsvFromBoundingBox( new Double[] {11.7d, 37.5d, 7.2d, 30d}, 1000, 1, "Tunisia_1000");
		//querySigrid.writeCsvFromBoundingBox( new Double[] {6.034, 14.862, 1.687, 12.725}, 1000, 1, "FFEM_1000");
		//querySigrid.writeCsvFromBoundingBox( new Double[] {34.434541, -7.869966, 21.344595, -18.697429}, 1000, 1, "Zambia");
		querySigrid.writeCsvFromBoundingBox( new Double[] {30.88, -2.3, 28.98, -4.5}, 1000, 1, "Burundi");
		System.exit(0);
	}
}
