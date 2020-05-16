package com.vMan.alertplugins.customrest;

import com.integrien.alive.common.adapter3.config.AlertTransmissionConfig;
import com.integrien.alive.common.adapter3.describe.AlertTransmissionDescribe;
import com.integrien.alive.common.dataobject.events.AlertBase;
import com.integrien.alive.common.plugins.generic.Configurable;
import com.integrien.alive.common.plugins.generic.MetadataParser;
import com.integrien.alive.common.plugins.generic.PluginMetadata;
import com.integrien.alive.common.plugins.generic.PropertiesParser;
import com.integrien.alive.common.plugins.generic.SyntaxException;
import com.integrien.alive.common.plugins.AlertPluginTestReply;
import com.integrien.analytics.plugins.alertplugins.AlertPluginBase;
import com.integrien.analytics.plugins.alertplugins.NotificationAlertBase;
import com.vmware.statsplatform.persistence.plugin.NotificationRuleData;
import com.vmware.vcops.common.l10n.LocalizedMsg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomRestPlugin
  extends AlertPluginBase
{
  private static final Logger logger = LoggerFactory.getLogger(CustomRestPlugin.class);
  private final AtomicReference<String> pluginInstanceId = new AtomicReference();
  private final AtomicBoolean sendJson = new AtomicBoolean(true);
  private final AtomicReference<CustomRestSender> sender = new AtomicReference();
  private String configFolderPath;
  
  public CustomRestPlugin(Attributes attributes, String auxFolder)
  {
    super(attributes, auxFolder);
  }
  
  public synchronized PluginMetadata describe() {
	    try (BufferedReader metadataBR = new BufferedReader(new InputStreamReader(new FileInputStream(
	              getMetadataFilePath()), StandardCharsets.UTF_8))) {
/*	Not needed for now
	    	PluginMetadata ruleProperties; 
*/
	    	String content = metadataBR.lines().collect(Collectors.joining(System.lineSeparator()));
	      
	      PluginMetadata metadata = (new MetadataParser(content, "metadata")).parse();

/*	Not needed for now	      
	      try (BufferedReader confBR = new BufferedReader(new InputStreamReader(new FileInputStream(
	                getConfigFilePath()), StandardCharsets.UTF_8))) {
	        String confContent = confBR.lines().collect(Collectors.joining(System.lineSeparator()));
	        ruleProperties = (new PropertiesParser(confContent, "properties")).parse();
	        
	      }
 
	      
	      metadata.setNotificationRuleProperties(ruleProperties.getNotificationRuleProperties());
*/	      
	      return metadata;
	    } catch (SyntaxException|java.io.FileNotFoundException e) {
	      logger.error(String.format("Unable to describe plugin %s", new Object[] { e.getMessage() }));
	      return null;
	    }
	    catch (Exception e) {
	      logger.error(String.format("Assertion: unable to describe plugin on %s: %s", new Object[] { this.configFolderPath, e
	              .getMessage() }));
	      return null;
	    } 
	  }

  public boolean configure(AlertTransmissionConfig rs)
  {
    CustomRestConfig config = CustomRestConfig.fromTransmissionConfig(rs);
    if (config == null)
    {
      logger.error("Failed to configure rest plugin: {}", rs.getInstanceId());
      return false;
    }
    try
    {
      this.sendJson.set(config.getContentType().equalsIgnoreCase("application/json"));
      this.sender.set(new CustomRestSender(config));
      this.pluginInstanceId.set(rs.getInstanceId());
      logger.debug("Successfully configured rest plugin: {}", rs.getInstanceId());
      return true;
    }
    catch (RestSenderException e)
    {
      logger.error("Failed to configure rest plugin: " + rs.getInstanceId(), e);
    }
    return false;
  }  
  
  public void addAlert(NotificationAlertBase alertBase, Collection<NotificationRuleData> arg1)
  {
    assert (this.sender.get() != null);
    if (((CustomRestSender)this.sender.get()).config.getMethod().contains("POST")) {
      ((CustomRestSender)this.sender.get()).postAsync(alertBase, "add");
    } else if (((CustomRestSender)this.sender.get()).config.getMethod().contains("PUT")) {
      ((CustomRestSender)this.sender.get()).putAsync(alertBase, "add");
    	}
  }


  public void cancelAlert(NotificationAlertBase alertBase, Collection<NotificationRuleData> arg1)
  {
    assert (this.sender.get() != null);
    if (((CustomRestSender)this.sender.get()).config.getMethod().contains("POST")) {
      ((CustomRestSender)this.sender.get()).postAsync(alertBase, "cancel");
    } else if (((CustomRestSender)this.sender.get()).config.getMethod().contains("PUT")) {
      ((CustomRestSender)this.sender.get()).putAsync(alertBase, "cancel");
    }
  }
  
  
  public AlertPluginTestReply test(AlertTransmissionConfig rs)
  {
    CustomRestConfig config = CustomRestConfig.fromTransmissionConfig(rs);
    if (config == null) 
    {
      logger.error("Rest plugin test failed due to invalid configuration.");
      return new AlertPluginTestReply(false, LocalizedMsg._tr("Invalid configuration."), "Invalid configuration.");
    }
    try 
    {
      CustomRestSender testSender = new CustomRestSender(config);
      String body = config.getRequest();
      String method = config.getMethod();
  
      String errorMessage = "";
      
      logger.debug("start testing: " + config.getMethod() + " " + config.getUrl());
      
      if (method.contains("POST")){
          Future<CustomRestSender.RestSenderResult> post = testSender.postAsync(null, body);
    	  CustomRestSender.RestSenderResult postResult = (CustomRestSender.RestSenderResult)post.get();
      if (postResult == null) {
        errorMessage = errorMessage + "Unable to establish a connection to the server for HTTP POST. ";
      } else if ((postResult == null) || (!postResult.isSuccess())) {
        errorMessage = errorMessage + String.format("Expected POST HTTP status code %s but received %s. ", new Object[] { Integer.valueOf(postResult.getExpectedStatusCode()), Integer.valueOf(postResult.getStatusCode()) });
      }
      }
      
      if (method.contains("PUT")){
    	  Future<CustomRestSender.RestSenderResult> put = testSender.putAsync(null, body);
    	  CustomRestSender.RestSenderResult putResult = (CustomRestSender.RestSenderResult)put.get();
      if (putResult == null) {
        errorMessage = errorMessage + "Unable to establish a connection to the server for HTTP PUT. ";
      } else if ((putResult == null) || (!putResult.isSuccess())) {
          errorMessage = errorMessage + String.format("Expected PUT HTTP status code %s but received %s. ", new Object[] { Integer.valueOf(putResult.getExpectedStatusCode()), Integer.valueOf(putResult.getStatusCode()) });
      }
      }

      if (StringUtils.isNotEmpty(errorMessage)) 
      {
        logger.error("Rest plugin test failed - {}", errorMessage);
        return new AlertPluginTestReply(false, LocalizedMsg._tr("Failed to post or put to the server."), errorMessage);
      }
      
      logger.debug("Rest plugin test successful.");
      return new AlertPluginTestReply(true, null);
      } 
      catch (RestSenderException|InterruptedException|ExecutionException e)
      {
      logger.error("Rest plugin test failed due to sender exception: {}", e.getMessage());
      return new AlertPluginTestReply(false, LocalizedMsg._tr("Rest plugin test failed: %1", new Object[] { e.getMessage() }), e.getMessage());
    }
  }
  

  public void updateAlert(NotificationAlertBase alertBase, Collection<NotificationRuleData> arg1)
  {
    assert (this.sender.get() != null);
    if (((CustomRestSender)this.sender.get()).config.getMethod().contains("POST")) {
      ((CustomRestSender)this.sender.get()).postAsync(alertBase, "update");
    } else if (((CustomRestSender)this.sender.get()).config.getMethod().contains("PUT")) {
      ((CustomRestSender)this.sender.get()).putAsync(alertBase, "update");
    }
  }
  

  public boolean init()
  {
    return true;
  }
  
  public void clear() {}
  
  /*	Not needed for now  
	  public String getConfigFolderPath() {
	    return this.configFolderPath;
	  }
*/
	  
/*	Not needed for now
	private String getConfigFilePath() {
	return this.configFolderPath + File.separator + "config.json";
	}
*/	  
	  private String getMetadataFilePath() {
	    return "/usr/lib/vmware-vcops/user/plugins/outbound/vman-custom-rest-plugin/metadata.json";
	  }  
}