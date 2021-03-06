package uk.bl.monitrix.database.cassandra.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import play.Logger;
import uk.bl.monitrix.database.cassandra.CassandraProperties;
import uk.bl.monitrix.model.Alert;
import uk.bl.monitrix.model.Alert.AlertType;
import uk.bl.monitrix.model.AlertLog;
import uk.bl.monitrix.model.CrawlLog;

/**
 * A CassandraDB-backed implementation of {@link AlertLog}.
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 */
public class CassandraAlertLog implements AlertLog {
	
	private static long HOUR_IN_MILLIS = 60 * 60 * 1000;
	
	private final String TABLE_ALERTS = CassandraProperties.KEYSPACE  + "." + CassandraProperties.COLLECTION_ALERT_LOG;
	
	protected Session session;
	
	private CrawlLog crawlLog;
	
	public CassandraAlertLog(Session session, CrawlLog crawlLog) {
		this.session = session;
		this.crawlLog = crawlLog;
	}

	@Override
    public long countAll() {
        long count = 0L;
        try {
            ResultSet results = session.execute("SELECT COUNT(*) FROM " + TABLE_ALERTS + ";");
            count = results.one().getLong("count");
        } catch(NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        return count;
    }

	@Override
	public Iterator<Alert> listAll() {
		return map(session.execute("SELECT * FROM " + TABLE_ALERTS + ";").iterator());
	}
	
	
	private List<Alert> appendAlertsUntil(List<Alert> alerts, int limit, long hour) {
        List<Alert> alertsForThisHour = new ArrayList<Alert>();
        try {
            Iterator<Row> rows = session.execute("SELECT * FROM " + TABLE_ALERTS + " WHERE " + CassandraProperties.FIELD_ALERT_LOG_TIMESTAMP_HR + "=" +
                    hour + " ALLOW FILTERING ;").iterator();
            while (rows.hasNext())
                alertsForThisHour.add(new CassandraAlert(rows.next()));

            Collections.sort(alertsForThisHour, new Comparator<Alert>() {
                @Override
                public int compare(Alert a, Alert b) {
                    return (int) (a.getTimestamp() - b.getTimestamp());
                }
            });

            int counter = 0;
            while (alerts.size() < limit && counter < alertsForThisHour.size()) {
                alerts.add(alertsForThisHour.get(counter));
                counter++;
            }
        } catch (NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        if (alerts.size() != 0 && alerts.size() < limit) {
            long crawlStart = (crawlLog.getCrawlStartTime() / HOUR_IN_MILLIS) * HOUR_IN_MILLIS;
            long previousHour = hour - HOUR_IN_MILLIS;

            if (previousHour >= crawlStart)
                return appendAlertsUntil(alerts, limit, previousHour);
            else
                return alerts;
        } else {
            return alerts;
        }
    }
	
	
	@Override
	public List<Alert> getMostRecent(int n) {
		long hourOflastActivity = (crawlLog.getTimeOfLastCrawlActivity() / HOUR_IN_MILLIS) * HOUR_IN_MILLIS;
		return appendAlertsUntil(new ArrayList<Alert>(), n, hourOflastActivity);
	}

	@Override
	public List<String> getOffendingHosts() {
        List<String> hostList = new ArrayList<String>();
        try {
            Iterator<Row> rows = session.execute("SELECT " + CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST + " FROM " +
                    TABLE_ALERTS + ";").iterator();

            Set<String> hosts = new HashSet<String>();
            while (rows.hasNext()) {
                hosts.add(rows.next().getString(CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST));
            }

            hostList.addAll(hosts);
        } catch (NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        return hostList;
    }

	@Override
	public long countAlertsForHost(String hostname) {
        long count = 0L;
        try {
		    ResultSet results = session.execute("SELECT COUNT(*) FROM " + TABLE_ALERTS + " WHERE " +
				CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST + "='" + hostname + "';");
            count = results.one().getLong("count");
        } catch (NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
		return count;
	}
	
	@Override
	public long countAlertsForHost(String hostname, AlertType type) {
        long count = 0L;
        try {
            ResultSet results = session.execute("SELECT COUNT(*) FROM " + TABLE_ALERTS + " WHERE " +
                    CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST + "='" + hostname + "' AND " +
                    CassandraProperties.FIELD_ALERT_LOG_ALERT_TYPE + "='" + type.name() + "' ALLOW FILTERING;");
            count = results.one().getLong("count");
        } catch (NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        return count;
    }
	
	@Override
	public List<AlertType> getAlertTypesForHost(String hostname) {
        List<AlertType> list = new ArrayList<AlertType>();
        try {
            Iterator<Row> rows = session.execute("SELECT " + CassandraProperties.FIELD_ALERT_LOG_ALERT_TYPE + " FROM " +
                    TABLE_ALERTS + " WHERE " + CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST + "='" + hostname + "';").iterator();

            Set<AlertType> set = new HashSet<AlertType>();
            while (rows.hasNext()) {
                set.add(AlertType.valueOf(rows.next().getString(CassandraProperties.FIELD_ALERT_LOG_ALERT_TYPE)));
            }
            list.addAll(set);
        } catch (NoHostAvailableException ex) {
            Logger.warn("No hosts available ...");
        }
        return list;
    }

	@Override
	public Iterator<Alert> listAlertsForHost(String hostname) {
		return map(session.execute("SELECT * FROM " + TABLE_ALERTS + " WHERE " + CassandraProperties.FIELD_ALERT_LOG_OFFENDING_HOST + "='" +
				hostname + "';").iterator());
	}
	
	/**
	 * Utility method that maps a database cursor to an Iterator of Alert domain objects. 
	 * @param cursor the DB cursor
	 * @return the domain objects
	 */
	private static Iterator<Alert> map(final Iterator<Row> cursor) {
		return new Iterator<Alert>() {		
			@Override
			public boolean hasNext() {
				return cursor.hasNext();
			}

			@Override
			public Alert next() {
				return new CassandraAlert(cursor.next());
			}

			@Override
			public void remove() {
				cursor.remove();
			}
		};
	}
	
}
