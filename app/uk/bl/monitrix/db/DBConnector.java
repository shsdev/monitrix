package uk.bl.monitrix.db;

import java.util.Iterator;
import java.util.List;

import uk.bl.monitrix.CrawlLog;
import uk.bl.monitrix.CrawlStatistics;
import uk.bl.monitrix.HostInformation;
import uk.bl.monitrix.heritrix.LogEntry;

/**
 * A minimal DB connection interface.
 * 
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 */
public interface DBConnector {
	
	/**
	 * TODO need to change this, since we want to work in 'tail -f'-like mode
	 * @param iterator
	 */
	public void insert(Iterator<LogEntry> iterator);
	
	/**
	 * Returns DB-backed crawl log.
	 * @return the crawl log
	 */
	public CrawlLog getCrawlLog();
	
	/**
	 * Returns DB-backed crawl statistics.
	 * @return the crawl statistics
	 */
	public CrawlStatistics getCrawlStatistics();
	
	/**
	 * Searches the known hosts list. Supported types of queries depend
	 * on the type of backend!
	 * @param query the query
	 * @return list of host names
	 */
	public List<String> searchHosts(String query);
	
	/**
	 * Returns DB-backed host information
	 * @param hostname the host name
	 * @return the host information
	 */
	public HostInformation getHostInfo(String hostname);
	
	/**
	 * Closes the connection
	 */
	public void close();

}
