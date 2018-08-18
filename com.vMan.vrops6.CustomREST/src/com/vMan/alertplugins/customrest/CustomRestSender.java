package com.vMan.alertplugins.customrest;

import com.integrien.alive.alertplugins.util.AlertUtil;
import com.integrien.analytics.plugins.alertplugins.NotificationAlertBase;
import com.vmware.vcops.analytics.alertdef.AlertDefinitionCache;
import com.vmware.vcops.platform.api.model.alertdefinition.dataobject.AlertDefinition;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CustomRestSender
{
  private static final Logger logger = LoggerFactory.getLogger(CustomRestSender.class);
  public final CustomRestConfig config;
  private final HttpHost host;
  private final HttpClient client;
  private final ExecutorService executor;
  
  public CustomRestSender(CustomRestConfig config)
    throws RestSenderException
  {
    this.config = config;
    try {
      URL url = new URL(config.getUrl());
      
      int port = url.getPort();
      if (port == -1) {
        port = url.getDefaultPort();
      }
      this.host = new HttpHost(url.getHost(), port, url.getProtocol());
      this.client = createHttpClient();
      this.executor = Executors.newFixedThreadPool(config.getConnectionCount());
    }
    catch (MalformedURLException|UnrecoverableKeyException|NoSuchAlgorithmException|KeyStoreException|CertificateException|KeyManagementException e) {
      throw new RestSenderException("Failed to construct CustomRestSender.", e);
    }
  }
  
  private HttpClient createHttpClient() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException
  {
    assert (this.host != null);
    SchemeRegistry schemeRegistry = new SchemeRegistry();
    if (StringUtils.isNotBlank(this.config.getCertificateThumbprint())) {
      TrustStrategy trustStrategy = new TrustCertificateStrategy(this.config.getCertificateThumbprint());
      X509HostnameVerifier hostnameVerifier = new AllowAllHostnameVerifier();
      SSLSocketFactory sslSf = new SSLSocketFactory(trustStrategy, hostnameVerifier);
      
      Scheme https = new Scheme("https", this.host.getPort(), sslSf);
      schemeRegistry.register(https);
    }
    else {
      schemeRegistry.register(new Scheme("http", this.host.getPort(), PlainSocketFactory.getSocketFactory()));
    }
    
    ThreadSafeClientConnManager connectionManager = new ThreadSafeClientConnManager(schemeRegistry);
    connectionManager.setMaxTotal(this.config.getConnectionCount());
    connectionManager.setDefaultMaxPerRoute(this.config.getConnectionCount());
    
    DefaultHttpClient httpClient = new DefaultHttpClient(connectionManager);
    if ((StringUtils.isNotBlank(this.config.getUsername())) && (StringUtils.isNotBlank(this.config.getPassword()))) {
      httpClient.getCredentialsProvider().setCredentials(new AuthScope(this.host.getHostName(), this.host.getPort()), new UsernamePasswordCredentials(this.config.getUsername(), this.config.getPassword()));
    }
    

    return httpClient;
  }
  
  public Future<RestSenderResult> postAsync(NotificationAlertBase alertBase, String state)
  {
    String uri = this.config.getUrl();
    String debugout = this.config.getdebuglogout();   
    String postBody = this.config.getRequest();
    logger.debug("Queuing: POST {}", uri);
    HttpPost request = new HttpPost();
    String body = null;
    if (alertBase != null) {
      body = integrateInputs(postBody, alertBase, state);
      
      if (debugout.equals("ENABLED")){
	      
		      try{
		          PrintWriter writer = new PrintWriter("/storage/log/vcops/log/POST-CustomRestOutputNotNull.xml", "UTF-8");
		          writer.println(body);
		          writer.close();
		      } catch (IOException e) {
		    	  logger.warn("Custom Rest Integration error Error posting to /storage/log/vcops/log/POST-CustomRestOutputNotNull.xml", e);
		      }
	     }
      
    } else {
      body = postBody;

	      if (debugout.equals("ENABLED")){      
	      
		      try{
		          PrintWriter writer = new PrintWriter("/storage/log/vcops/log/POST-CustomRestOutputNull.xml", "UTF-8");
		          writer.println(body);
		          writer.close();
		      } catch (IOException e) {
		    	  logger.warn("Custom Rest Integration error Error posting to /storage/log/vcops/log/POST-CustomRestOutputNull.xml", e);
		      }
	      } 
    }
    RestJob job = createJob(request, uri, body, 201);
    if (job == null) {
      logger.error("Failed to queue: POST {}", uri);
      return null;
    }
    return this.executor.submit(job);
  }
  
  public Future<RestSenderResult> putAsync(NotificationAlertBase alertBase, String state)
  {
    String uri = this.config.getUrl();
    String debugout = this.config.getdebuglogout();
    String putBody = this.config.getRequest();
    logger.debug("Queuing: PUT {}", uri);
    HttpPut request = new HttpPut();
    String body = null;
    if (alertBase != null) {
      body = integrateInputs(putBody, alertBase, state);

      if (debugout.equals("ENABLED")){
		      
		      try{
		          PrintWriter writer = new PrintWriter("/storage/log/vcops/log/PUT-CustomRestOutputNotNull.xml", "UTF-8");
		          writer.println(body);
		          writer.close();
		      } catch (IOException e) {
		    	  logger.warn("Custom Rest Integration error Error posting to /storage/log/vcops/log/PUT-CustomRestOutputNotNull.xml", e);
		      }
	     }
	     
    } else {
      body = putBody;
      
      if (debugout.equals("ENABLED")){      
	      
	      try{
	          PrintWriter writer = new PrintWriter("/storage/log/vcops/log/PUT-CustomRestOutputNull.xml", "UTF-8");
	          writer.println(body);
	          writer.close();
	      } catch (IOException e) {
	    	  logger.warn("Custom Rest Integration error Error posting to /storage/log/vcops/log/PUT-CustomRestOutputNull.xml", e);
	      }
      } 
    }
    RestJob job = createJob(request, uri, body, 202);
    if (job == null)
    {
      logger.error("Failed to queue: PUT {}", uri);
      return null;
    }
    return this.executor.submit(job);
  }
  
  public String integrateInputs(String body, NotificationAlertBase alertBase, String state)
  {

	   String alertType = AlertUtil.getAlertTypeName(alertBase.getType());
	    String alertSubType = AlertUtil.getAlertSubTypeName(alertBase.getSubtype().intValue());
	    String alertCriticality = AlertUtil.getAlertCriticalityName(alertBase.getCriticality());
	    String resourceKind = alertBase.getResourceKind();
	    String resourceName = alertBase.getResourceName();
	    
	    String alertDefName = "";
	    String alertDefDesc = "";

	    String alertDefId = alertBase.getAlertDefinitionId();
	    if (alertDefId != null) {
	      Map<String, AlertDefinition> result = AlertDefinitionCache.getInstance().getLocalizedAlertDefinitions(Collections.singleton(alertDefId));
	      

	      if (result != null) {
	        AlertDefinition ad = (AlertDefinition)result.get(alertDefId);
	        if (ad != null) {
	          alertDefName = ad.getName();
	          alertDefDesc = ad.getDescription();
	        }
	      }
	      String eventMsg = alertBase.getEventMsg();
	      if ((eventMsg != null) && (!eventMsg.isEmpty())) {
	        alertDefName = alertDefName + " - " + eventMsg;
	      }
	    }	  

	    
	    
	    if (body.contains("#alertstate#")) {
	    		body = body.replaceAll("#alertstate#", state);
	    		}   
	    
	    if (body.contains("#alertadapterkind#")) {
	    		body = body.replaceAll("#alertadapterkind#}", alertBase.getAdapterKind());
	    		}
	    
		if (body.contains("#alertdefinitionid#")) {
				body = body.replaceAll("#alertdefinitionid#", alertBase.getAlertDefinitionId());
				}

		if (body.contains("#alertname#")) {
			body = body.replaceAll("#alertname#", alertDefName);
			}

		if (body.contains("#alertdescription#")) {
			body = body.replaceAll("#alertdescription#", alertDefDesc);
			}				
		
		if (body.contains("#alertcriticality#")) {
				body = body.replaceAll("#alertcriticality#", alertCriticality);
				}	
		
		if (body.contains("#alertefficiency#")) {
				body = body.replaceAll("#alertefficiency#", AlertUtil.convertHREvalue(alertBase.getEfficiency()));
				}
		
		if (body.contains("#alerthealth#")) {
				body = body.replaceAll("#alerthealth#", AlertUtil.convertHREvalue(alertBase.getHealth()));
				}
		
		if (body.contains("#alertimpact#")) {
				body = body.replaceAll("#alertimpact#", alertBase.getImpact() == null ? "" : alertBase.getImpact());
				}

		if (body.contains("#alertvropservername#")) {
				body = body.replaceAll("#alertvropservername#", AlertUtil.getHostName(alertBase.getAlertInfo()));
				}
		
		if (body.contains("#alertvropserver#")) {
				body = body.replaceAll("#alertvropserver#", alertBase.getAlertInfo().getHostAddress());
				}
		
		if (body.contains("#alertresourcekind#")) {
				body = body.replaceAll("#alertresourcekind#", resourceKind);
				}
		
		if (body.contains("#alertresourcename#")) {
				body = body.replaceAll("#alertresourcename#", resourceName);
				}
		
		if (body.contains("#alertresourceuuid#")) {
				body = body.replaceAll("#alertresourceuuid#", alertBase.getResourceUUID());
				}
		
		if (body.contains("#alertrisk#")) {
				body = body.replaceAll("#alertrisk#", AlertUtil.convertHREvalue(alertBase.getRisk()));
				}
		
		if (body.contains("#alertstatus#")) {
				body = body.replaceAll("#alertstatus#", Integer.toString(alertBase.getStatus()));
				}
		
		if (body.contains("#alerttype#")) {
				body = body.replaceAll("#alerttype#", alertType);
				}

		if (body.contains("#alertsubtype#")) {
			body = body.replaceAll("#alertsubtype#", alertSubType);
			}
		
		if (body.contains("#alertid#")) {
				body = body.replaceAll("#alertid#", alertBase.getAlertID().getUUID());
				}
		
		if (body.contains("#alerturl#")) {
				body = body.replaceAll("#alerturl#", alertBase.getAlertInfo().getAlertDetailURL());
				}			
		
		if (body.contains("#startdate#")) {
			Date date = new Date(alertBase.getStartDate());
	        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	        String starttime = format.format(date);
			body = body.replaceAll("#startdate#", starttime);
			}
		
		if (body.contains("#updatedate#")) {
			Date date = new Date(alertBase.getUpdateDate());
	        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	        String updatedate = format.format(date);
			body = body.replaceAll("#updatedate#", updatedate);
			}
		
		if (body.contains("#canceldate#")) {
			Date date = new Date(alertBase.getCancelDate());
	        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	        String canceldate = format.format(date);
			body = body.replaceAll("#canceldate#", canceldate);
			}
		return body;
  }
  
  public class RestSenderResult
  {
    private final int statusCode;
    private final int expectedStatusCode;
    
    public RestSenderResult(int statusCode, int expectedStatusCode) {
      this.statusCode = statusCode;
      this.expectedStatusCode = expectedStatusCode;
    }
    
	public boolean isSuccess()
    {
      if ((this.statusCode >= 200) && (this.statusCode < 300)) {
        return true;
      }
      return false;
    }
    
    public int getStatusCode() {
      return this.statusCode;
    }
    
    public int getExpectedStatusCode() {
      return this.expectedStatusCode;
    }
  }
  
  private RestJob createJob(HttpEntityEnclosingRequestBase request, String uri, String body, int statusCode) {
    assert (request != null);
    assert (StringUtils.isNotBlank(uri));
    assert (StringUtils.isNotBlank(body));
    StringEntity entity;
    try {
      entity = new StringEntity(body, this.config.getContentType(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      logger.error("Failed to construct request body.", e);
      return null;
    }
    URI requestUri;
    try {
      requestUri = new URI(uri);
    } catch (URISyntaxException e) {
      logger.error("Failed to construct URI.", e);
      return null;
    }
    request.setURI(requestUri);
    request.addHeader("Content-Type", this.config.getContentType());
    request.setEntity(entity);
    return new RestJob(request, statusCode);
  }
  
  private class RestJob implements Callable<CustomRestSender.RestSenderResult>
  {
    final HttpUriRequest request;
    final int successCode;
    
    RestJob(HttpUriRequest request, int successCode) {
      this.request = request;
      this.successCode = successCode;
    }
    
    public CustomRestSender.RestSenderResult call()
    {
      CustomRestSender.logger.debug("Sending: {} {}", this.request.getMethod(), this.request.getURI());
      try {
        ResponseHandler<Integer> handler = new ResponseHandler()
        {
          public Integer handleResponse(HttpResponse httpResponse) throws IOException {
            return Integer.valueOf(httpResponse.getStatusLine().getStatusCode());
          }
        };
        Integer statusCode = (Integer)CustomRestSender.this.client.execute(CustomRestSender.this.host, this.request, handler, new BasicHttpContext());
        if (statusCode.intValue() != this.successCode) {
          CustomRestSender.logger.error("Failed to complete {}; status code: {}", this.request.getMethod(), statusCode);
        } else {
          CustomRestSender.logger.debug("Successfully sent {} request.", this.request.getMethod());
        }
        return new CustomRestSender.RestSenderResult(statusCode.intValue(), this.successCode);
      } catch (SSLPeerUnverifiedException e) {
    	  CustomRestSender.logger.error("Failed verify endpoint certificate: {}", this.request.getMethod());
        return null;
      } catch (IOException e) {
    	  CustomRestSender.logger.error("Failed to complete " + this.request.getMethod(), e); }
      return null;
    }
  }
}