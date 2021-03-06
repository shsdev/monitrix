package uk.bl.monitrix.util;

import java.io.IOException;

import uk.bl.monitrix.heritrix.SimpleLogfileReader;
import uk.bl.monitrix.database.mongodb.ingest.MongoDBIngestConnector;

/**
 * A utility class that pre-initializes MongoDB from a Heretrix log - for test/dev purposes only!
 * 
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 */
public class BatchLogProcessor {
	
	// private static final String LOG_FILE = "test/sample-log-1E3.txt";
	// private static final String LOG_FILE = "/home/simonr/Downloads/sample-log-2E6.log";
	private static final String LOG_FILE = "/home/simonr/Downloads/crawl.log.20120914182409";
	// private static final String LOG_FILE = "/media/My Passport/crawl.log";
	
	public static void main(String[] args) throws IOException {
		MongoDBIngestConnector mongo = new MongoDBIngestConnector("localhost", "monitrix", 27017);
		SimpleLogfileReader reader = new SimpleLogfileReader(LOG_FILE);
		
		mongo.getIngestSchedule().addLog(LOG_FILE, "crawler_1", false);
		String id = mongo.getIngestSchedule().getLogForPath(LOG_FILE).getId();
		mongo.insert(id, reader.iterator());
	}

}
