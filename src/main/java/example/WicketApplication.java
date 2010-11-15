package example;

import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebApplication;

/**
 * Application object for your web application. If you want to run this application without deploying, run the Start class.
 */
public class WicketApplication extends WebApplication {
    /**
     * Constructor
     */
	public WicketApplication() {
	}
	
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	public Class<Userline> getHomePage() {
		return Userline.class;
	}

    @Override
    public Session newSession(Request request, Response response) {
        return new TwissSession(request);
    }
}