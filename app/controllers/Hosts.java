package controllers;

import java.util.List;

import global.Global;
import play.mvc.Result;
import play.mvc.Controller;
import uk.bl.monitrix.HostInformation;
import uk.bl.monitrix.db.DBConnector;

public class Hosts extends Controller {
	
	// String constants
	private static final String QUERY = "query";
	
	private static DBConnector db = Global.getBackend();
	
	public static Result searchHosts() {
		String query = getQueryParam(QUERY);
		if (query == null) {
			// TODO error handling
			return notFound();
		} else {
			List<String> hosts = db.searchHosts(query);
			if (hosts.size() == 1)
				return redirect(routes.Hosts.getHostInfo(hosts.get(0)));
			else
				return ok(views.html.hosts.searchResult.render(db.searchHosts(query)));
		}
	}
	
	public static Result getHostInfo(String hostname) {
		HostInformation hostInfo = db.getHostInfo(hostname);

		if (hostInfo == null)
			return notFound(); // TODO error handling
		else
			return ok(views.html.hosts.hostinfo.render(hostInfo));
	}
	
	private static String getQueryParam(String key) {
		String[] value = request().queryString().get(key);
		if (value == null)
			return null;
		
		if (value.length == 0)
			return null;
		
		return value[0];
	}

}
