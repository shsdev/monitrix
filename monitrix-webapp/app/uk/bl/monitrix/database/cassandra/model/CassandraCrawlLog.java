package uk.bl.monitrix.database.cassandra.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import play.Logger;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import uk.bl.monitrix.database.cassandra.CassandraProperties;
import uk.bl.monitrix.database.cassandra.CassandraDBConnector;
import uk.bl.monitrix.model.CrawlLog;
import uk.bl.monitrix.model.CrawlLogEntry;
import uk.bl.monitrix.model.SearchResult;
import uk.bl.monitrix.model.SearchResultItem;

/**
 * A CassandraDB-backed implementation of {@link CrawlLog}.
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 *
 */
public class CassandraCrawlLog extends CrawlLog {
	
	public class Tuple {
		
		Tuple(int _1, int _2) {
			this._1 = _1;
			this._2 = _2;
		}
	
		public int _1;
		
		public int _2;
		
	}
	
	protected Session session;
	
	protected final String TABLE_CRAWL_LOG = CassandraProperties.KEYSPACE + "." + CassandraProperties.COLLECTION_CRAWL_LOG;
	protected final String TABLE_COMPRESSABILITY_HISTOGRAM = CassandraProperties.KEYSPACE + "." + CassandraProperties.COLLECTION_COMPRESSABILITY_HISTOGRAM;
	protected final String TABLE_INGEST_SCHEDULE = CassandraProperties.KEYSPACE + "." + CassandraProperties.COLLECTION_INGEST_SCHEDULE;
	
	public CassandraCrawlLog(Session session) {
		this.session = session;
	}
	
	protected Date getCoarseTimestamp(Date timestamp) {
		return new Date(CassandraDBConnector.HOUR_AS_MILLIS * (timestamp.getTime() / CassandraDBConnector.HOUR_AS_MILLIS));
	}

	@Override
	public long getCrawlStartTime() {
		long crawlStartTime = Long.MAX_VALUE;
        try {
            Iterator<Row> rows = session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + ";").iterator();

            while (rows.hasNext()) {
                Row r = rows.next();
                long start_ts = r.getLong(CassandraProperties.FIELD_INGEST_START_TS);
                if (start_ts < crawlStartTime)
                    crawlStartTime = start_ts;
            }
            Logger.info("Crawl start time: " + crawlStartTime);

            if (crawlStartTime == 0)
                return -1;
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
            crawlStartTime = 0;
        }
		return crawlStartTime;
	}

	@Override
	public long getTimeOfLastCrawlActivity() {
		long lastCrawlActivity = 0;
        try {
            Iterator<Row> rows = session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + ";").iterator();

            while (rows.hasNext()) {
                Row r = rows.next();
                long end_ts = r.getLong(CassandraProperties.FIELD_INGEST_END_TS);
                if (end_ts > lastCrawlActivity)
                    lastCrawlActivity = end_ts;
            }
            Logger.info("Last crawl activity: " + lastCrawlActivity);

            if (lastCrawlActivity == 0)
                return -1;
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return lastCrawlActivity;
	}

	@Override
	public List<CrawlLogEntry> getMostRecentEntries(int n) {
		long startTime = 0L;
        List<CrawlLogEntry> recent = new ArrayList<CrawlLogEntry>();
        try {
            startTime = getTimeOfLastCrawlActivity();
		
            // Round the time down:
            Date coarse_ts = this.getCoarseTimestamp(new Date(startTime));

            // Search based on KEY, and range (TODO?)
            Iterator<Row> cursor =
                    session.execute("SELECT * FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_COARSE_TIMESTAMP + "=" + coarse_ts.getTime() + " LIMIT 100;")
                    .iterator();

            while(cursor.hasNext())
                recent.add(new CassandraCrawlLogEntry(cursor.next()));

        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return recent;
	}
	
	@Override
	public long countEntries() {
        long grand_total = 0;
        try {
            ResultSet results = session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + ";");
            Iterator<Row> rows = results.iterator();
            while( rows.hasNext() ) {
                grand_total += rows.next().getLong(CassandraProperties.FIELD_INGEST_INGESTED_LINES);
            }
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return grand_total;
	}
	
	@Override
	public long countRevisits() {
        long grand_total = 0;
        try {
            ResultSet results = session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + ";");
            Iterator<Row> rows = results.iterator();
            while( rows.hasNext() ) {
                grand_total += rows.next().getLong(CassandraProperties.FIELD_INGEST_REVISIT_RECORDS);
            }
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return grand_total;
	}
	
	@Override
	public List<String> listLogIds() {
        List<String> collection = new ArrayList<String>();
        try {
            ResultSet results = session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + ";");
            Iterator<Row> rows = results.iterator();
            while (rows.hasNext()) {
                collection.add(rows.next().getString(CassandraProperties.FIELD_INGEST_CRAWL_ID));
            }
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return collection;
	}
	
	private List<String> listLogPaths() {
        List<String> collection = new ArrayList<String>();
        try {
            ResultSet results = session.execute("SELECT " + CassandraProperties.FIELD_INGEST_CRAWLER_PATH + " FROM " + TABLE_INGEST_SCHEDULE + ";");
            Iterator<Row> rows = results.iterator();
            while (rows.hasNext()) {
                collection.add(rows.next().getString(CassandraProperties.FIELD_INGEST_CRAWLER_PATH));
            }
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return collection;
	}

	@Override
	public long countEntriesForLog(String logId) {
        long count = 0L;
        try {
		    ResultSet results =
				session.execute("SELECT * FROM " + TABLE_INGEST_SCHEDULE + " WHERE " + CassandraProperties.FIELD_INGEST_CRAWL_ID + "='" + logId + "';");
            count = results.one().getLong(CassandraProperties.FIELD_INGEST_INGESTED_LINES);
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        return count;
	}
	
	@Override
	public List<CrawlLogEntry> getEntriesForURL(String url) {
        List<CrawlLogEntry> entries = null;
        try {
            ResultSet results = session.execute("SELECT * FROM " + TABLE_CRAWL_LOG +
                    " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_URL + " = '" + url + "';");
            entries = toLogEntries(results);
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return entries;
	}
	
	private List<CrawlLogEntry> toLogEntries(ResultSet results) {
		List<CrawlLogEntry> entries = new ArrayList<CrawlLogEntry>();
		for (Row r : results.all()) {
			entries.add(new CassandraCrawlLogEntry(r));
		}
		return entries;
	}

	// TODO eliminate code duplication
	@Override
	public SearchResult searchByURL(String query, int limit, int offset) {
		long startTime = System.currentTimeMillis();
		
		int off_limit = offset + limit;
		ResultSet results = 
				session.execute("SELECT * FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_URL + " = '" + query + "' LIMIT " + off_limit + ";");
		
		List<CrawlLogEntry> entries = new ArrayList<CrawlLogEntry>();
		// int i = 0;
		Iterator<Row> rows = results.iterator();
		while( rows.hasNext() ) {
			/* Row r = */ rows.next();
			/*
			if( i >= offset ) {
				entries.add( this.getLogEntryForUriResult(query,r) );
			}
			if( i == off_limit ) 
				break;
			i++;
			*/
		}
		
		ResultSet totalResults = session.execute("SELECT COUNT(*) FROM " + TABLE_CRAWL_LOG +
		        " WHERE uri = '" + query + "';");
		long total = totalResults.one().getLong("count");
		
		List<SearchResultItem> urls = new ArrayList<SearchResultItem>();
		for( CrawlLogEntry entry : entries ) {
			urls.add(new SearchResultItem(entry.getURL(), entry.toString()));
		}
	
		return new SearchResult(query, total, urls, limit, offset, System.currentTimeMillis() - startTime);
	}
	
	@Override
	public SearchResult searchByAnnotation(String annotation, int limit, int offset) {
		long startTime = System.currentTimeMillis();
		//DBObject q = new BasicDBObject(CassandraProperties.FIELD_CRAWL_LOG_ANNOTATIONS_TOKENIZED, annotation);
		
		long total = 0;//collection.count(q);
		
		List<SearchResultItem> urls = new ArrayList<SearchResultItem>();
		//DBCursor cursor = collection.find(q).skip(offset).limit(limit);
		//while (cursor.hasNext()) {
		//	CrawlLogEntry entry = new CassandraCrawlLogEntry(cursor.next());
		//	urls.add(new SearchResultItem(entry.getURL(), entry.toString()));
		//}
		
		return new SearchResult(annotation, total, urls, limit, offset, System.currentTimeMillis() - startTime);
	}
	
	@Override
	public SearchResult searchByCompressability(double from, double to, int limit, int offset) {
		Logger.debug("Searching by compressability");
		long startTime = System.currentTimeMillis();
		
		List<SearchResultItem> concatenated = new ArrayList<SearchResultItem>();	
		Long total = 0l;
		for (String logPath : listLogPaths()) {
			// Count total
			String count = "SELECT COUNT(*) FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " > " + from +
					" AND " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " < " + to + " AND " +
					CassandraProperties.FIELD_CRAWL_LOG_LOG_ID + " = '" + logPath + "' ALLOW FILTERING ;";

			ResultSet totalResults = session.execute(count);
			total += totalResults.one().getLong("count");
			
			String query = "SELECT * FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " > " + from +
					" AND " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " < " + to + " AND " +
					CassandraProperties.FIELD_CRAWL_LOG_LOG_ID + " = '" + logPath + "'";
			
			if (limit > 0 && offset > 0) {
				query += " LIMIT " + (limit + offset) + " ALLOW FILTERING ;";
			} else {
				query += " ALLOW FILTERING ;";
			}

			Iterator<Row> results = session.execute(query).iterator();
			for (int i=0; i<offset; i++) {
				if (results.hasNext())
					results.next();
			}
			
			while (results.hasNext()) {
				CrawlLogEntry entry = new CassandraCrawlLogEntry(results.next());
				concatenated.add(new SearchResultItem(entry.getURL(), entry.toString()));
			}
		}

		Logger.debug("Done - took " + (System.currentTimeMillis() - startTime));
		return new SearchResult(null, total, concatenated, limit, offset, System.currentTimeMillis() - startTime);
	}
	
	public List<Tuple> getCompressabilityHistogram() {
		Logger.debug("Retrieving entire compressability histogram");
		String query = "SELECT * FROM " + TABLE_COMPRESSABILITY_HISTOGRAM + " LIMIT 2000 ;";
		List<Row> result = session.execute(query).all();
		
		List<Tuple> tuples = new ArrayList<Tuple>(); 
		for (Row r : result) {
			tuples.add(new Tuple(r.getInt(CassandraProperties.FIELD_COMPRESSABILITY_BUCKET), (int) r.getLong(CassandraProperties.FIELD_COMPRESSABILITY_COUNT)));
		}
		
		Collections.sort(tuples, new Comparator<Tuple>() {
			@Override
			public int compare(Tuple o1, Tuple o2) {
				return o1._1 - o2._1;
			}
		});
 		
		return tuples;		
	}
	
	@Override
	public long countByCompressability(double from, double to) {
		Logger.debug("Counting by compressability");
		long startTime = System.currentTimeMillis();

		long totalCount = 0l;
		int currentBucket = (int) Math.round(from * 1000);
		while (currentBucket < to) {
			String query = "SELECT * FROM " + TABLE_COMPRESSABILITY_HISTOGRAM + " WHERE " + 
					CassandraProperties.FIELD_COMPRESSABILITY_BUCKET + " = " + currentBucket;
			
			Row r = session.execute(query).one();
			totalCount += r.getLong(CassandraProperties.FIELD_COMPRESSABILITY_COUNT);
		}
		
		/*
		long total = 0l;
		for (String logPath : listLogPaths()) {
			// Count total
			String count = "SELECT COUNT(*) FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " > " + from +
					" AND " + CassandraProperties.FIELD_CRAWL_LOG_COMPRESSABILITY + " < " + to + " AND " +
					CassandraProperties.FIELD_CRAWL_LOG_LOG_ID + " = '" + logPath + "' ALLOW FILTERING ;";
			
			Logger.debug(count);
			
			ResultSet totalResults = session.execute(count);
			total += totalResults.one().getLong("count");
		}
		*/

		Logger.debug("Done. Got " + totalCount + " - took " + (System.currentTimeMillis() - startTime));
		return totalCount;
	}
	
	@Override
	public long countEntriesForHost(String hostname) {
		ResultSet totalResults = 
			session.execute("SELECT COUNT(*) FROM " + TABLE_CRAWL_LOG + " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_HOST + 
			" = '" + hostname + "';");
		
		return totalResults.one().getLong("count");
	}

	@Override
	public Iterator<CrawlLogEntry> getEntriesForHost(String hostname) {
		ResultSet results = session.execute("SELECT * FROM " + TABLE_CRAWL_LOG +
		        " WHERE " + CassandraProperties.FIELD_CRAWL_LOG_HOST + "='" + hostname + "';");

		final Iterator<Row> cursor = results.iterator();
		return new Iterator<CrawlLogEntry>() {
			@Override
			public boolean hasNext() {
				return cursor.hasNext();
			}

			@Override
			public CrawlLogEntry next() {
				return new CassandraCrawlLogEntry(cursor.next());	
			}

			@Override
			public void remove() {
				cursor.remove();
			}
		};
	}

	@Override
	public List<String> extractHostsForAnnotation(String annotation) {
		// TODO
		/*
		List<String> hosts = new ArrayList<String>();
		Iterator<Row> rows = session.execute("SELECT host FROM crawl_uris.annotations WHERE annotation='"+annotation+"' LIMIT 1;").iterator();
		String host = null;
		while( rows.hasNext() ) {
			host = rows.next().getString("host");
			hosts.add(host);
			rows = session.execute("SELECT host FROM crawl_uris.annotations WHERE annotation='"+annotation+"' AND host > '"+host+"' LIMIT 1;").iterator();
		}
		return hosts;
		*/ 
		return new ArrayList<String>();
	}

}
