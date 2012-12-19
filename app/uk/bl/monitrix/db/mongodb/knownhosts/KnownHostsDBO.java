package uk.bl.monitrix.db.mongodb.knownhosts;

import uk.bl.monitrix.db.mongodb.MongoProperties;

import com.mongodb.DBObject;

/**
 * Wraps the DBObjects stored in the MongoDB 'Known Hosts' collection.
 * 
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 */
public class KnownHostsDBO {
	
	DBObject dbo;
	
	public KnownHostsDBO(DBObject dbo) {
		this.dbo = dbo;
	}
	
	/**
	 * Host name.
	 * @return the host name
	 */
	public String getHostname() {
		return (String) dbo.get(MongoProperties.FIELD_KNOWN_HOSTS_HOSTNAME);
	}
	
	/**
	 * Sets the host name.
	 * @param hostname the host name
	 */
	public void setHostname(String hostname) {
		dbo.put(MongoProperties.FIELD_KNOWN_HOSTS_HOSTNAME, hostname);
	}
	
	/**
	 * UNIX timestamp of the last recorded access to this host.
	 * @return the last recorded access
	 */
	public long getLastAccess() {
		return (Long) dbo.get(MongoProperties.FIELD_KNOWN_HOSTS_LAST_ACCESS);
	}
	
	/**
	 * Sets the UNIX timestamp of the last recorded access to this host.
	 * @param lastAccess the last access timestamp
	 */
	public void setLastAccess(long lastAccess) {
		dbo.put(MongoProperties.FIELD_KNOWN_HOSTS_LAST_ACCESS, lastAccess);
	}

}
