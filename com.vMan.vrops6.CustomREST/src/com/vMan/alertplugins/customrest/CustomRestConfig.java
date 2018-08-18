package com.vMan.alertplugins.customrest;

import com.integrien.alive.common.adapter3.config.AlertTransmissionConfig;
import com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

public class CustomRestConfig
{
  private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CustomRestConfig.class);
  private final String url;
  private final String username;
  private final String password;
  private final String certificateThumbprint;
  private final int connectionCount;  
  private final String method;
  private final String contentType;
  private final String request;
  private final String debuglogout;
  
  public CustomRestConfig(String url, String username, String password, String certificateThumbprint, int connectionCount, String method, String contentType, String request, String debuglogout)
  {
    this.url = url;
    this.username = username;
    this.password = password;
    this.certificateThumbprint = certificateThumbprint;
    this.connectionCount = connectionCount;
    this.method = method;
    this.contentType = contentType;
    this.request = request;
    this.debuglogout = debuglogout;
  }
  
  static CustomRestConfig fromTransmissionConfig(AlertTransmissionConfig config)
  {
    String url = null;
    String username = null;
    String password = null;
	String certificateThumbprint = null;
    Integer connectionCount = Integer.valueOf(30);    
    String method = null;
    String contentType = null;
    String request = null;
    String debuglogout = null;

    for (ResourceIdentifierConfig id : config.getResIdentConfigs()) {
      if (id.getKey().equalsIgnoreCase("Url")) {
        url = id.getValue();
      } else if (id.getKey().equalsIgnoreCase("Username")) {
        username = id.getValue();
      } else if (id.getKey().equalsIgnoreCase("Password")) {
        password = id.getValue();
      } else if (id.getKey().equalsIgnoreCase("Certificate")) {
    	certificateThumbprint = id.getValue();
      } 
      else if (id.getKey().equalsIgnoreCase("ConnectionCount")) {
          try {
              connectionCount = Integer.valueOf(Integer.parseInt(id.getValue()));
            }
            catch (NumberFormatException e) {
              logger.warn("Invalid connection count '{}', using default of {}.", id.getValue(), Integer.valueOf(30));
            }
      }      
      	else if (id.getKey().equalsIgnoreCase("Method")) {
        method = id.getValue();
      } else if (id.getKey().equalsIgnoreCase("Content-type")) {
        contentType = id.getValue();
      } else if (id.getKey().equalsIgnoreCase("Request")) {
        request = id.getValue();    
      } else if (id.getKey().equalsIgnoreCase("debuglogout")) {
    	debuglogout = id.getValue();  
      }
    }
    if ((StringUtils.isNotBlank(url)) && (StringUtils.isNotBlank(contentType))) {
      logger.info("Custom Rest plugin  packet info: { url: {}, contentType: {}, method: {} username: {}, password: {}, thumbprint: {}, connectionCount: {}", new Object[] { url, contentType, method, username, Boolean.valueOf(StringUtils.isNotBlank(password)), certificateThumbprint, connectionCount.intValue() });
      return new CustomRestConfig(url, username, password, certificateThumbprint, connectionCount.intValue(), method, contentType, request, debuglogout);
    }
    logger.error("Custom Rest plugin configuration missing required properties: " + (StringUtils.isBlank(url) ? "Url " : "") + (StringUtils.isBlank(contentType) ? "Content-Type " : "") + (StringUtils.isBlank(username) ? "Username " : "") + (StringUtils.isBlank(password) ? "Password " : "") + (StringUtils.isBlank(certificateThumbprint) ? "Certificate" : ""));
    

    return null;
  }
  
  public String getUrl()
  {
    return this.url;
  }
  
  public String getUsername()
  {
    return this.username;
  }
  
  public String getPassword()
  {
    return this.password;
  }

  public String getCertificateThumbprint()
  {
	return this.certificateThumbprint;
  }
  
  public int getConnectionCount()
  {
    return this.connectionCount;
  }  
  
  public String getMethod()
  {
    return this.method;
  }
  
  public String getContentType()
  {
    return this.contentType;
  }
  
  public String getRequest()
  {
    return this.request;
  }
  
  public String getdebuglogout()
  {
    return this.debuglogout;
  }  
}