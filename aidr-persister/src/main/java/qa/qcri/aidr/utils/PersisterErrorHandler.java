package qa.qcri.aidr.utils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.glassfish.jersey.jackson.JacksonFeature;

/**
 * @author Kushal
 *
 */
public class PersisterErrorHandler {
	
	private static Logger logger = Logger.getLogger(PersisterErrorHandler.class);
	
	public static void sendErrorMail(String code, String errorMsg) {
		Response clientResponse = null;
		Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
		try {
			WebTarget webResource = client.target(PersisterConfigurator.getInstance().getProperty(PersisterConfigurationProperty.TAGGER_MAIN_URL)
					+ "/misc/sendErrorEmail");
			
			Form form = new Form();
			form.param("module", "AIDRPersister");
			form.param("code", code);
			form.param("description", errorMsg);
			
			clientResponse = webResource.request().post(
					Entity.entity(form,MediaType.APPLICATION_FORM_URLENCODED),Response.class);
			if (clientResponse.getStatus() != 200) {
				logger.warn("Couldn't contact AIDRTaggerAPI for sending error message");
			}
		} catch (Exception e) {
			logger.error("Error in contacting AIDRTaggerAPI: " + clientResponse);
		}
	}
}
