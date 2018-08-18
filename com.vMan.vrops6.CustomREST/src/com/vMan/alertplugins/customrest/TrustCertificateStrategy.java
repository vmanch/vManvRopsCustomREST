package com.vMan.alertplugins.customrest;

import com.integrien.alive.common.security.SecurityCertificateUtil;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

class TrustCertificateStrategy implements org.apache.http.conn.ssl.TrustStrategy
{
  private final String thumbprint;
  
  public TrustCertificateStrategy(String thumbprint)
  {
    if (SecurityCertificateUtil.isThumbprint(thumbprint)) {
      this.thumbprint = SecurityCertificateUtil.formatThumbprint(thumbprint);
    } else {
      throw new IllegalArgumentException("Invalid thumbprint.");
    }
  }
  
  public boolean isTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException
  {
    boolean validCertificateFound = false;
    

    X509Certificate x509Certificate = x509Certificates[0];
    String sha1ThumbPrint = SecurityCertificateUtil.getSHA1ThumbPrint(x509Certificate);
    if (SecurityCertificateUtil.isThumbprint(sha1ThumbPrint)) {
      sha1ThumbPrint = SecurityCertificateUtil.formatThumbprint(sha1ThumbPrint);
      if (this.thumbprint.compareToIgnoreCase(sha1ThumbPrint) == 0)
      {
        validCertificateFound = true;
      }
    }
    
    if (!validCertificateFound)
    {

      throw new CertificateException();
    }
    

    return validCertificateFound;
  }
}