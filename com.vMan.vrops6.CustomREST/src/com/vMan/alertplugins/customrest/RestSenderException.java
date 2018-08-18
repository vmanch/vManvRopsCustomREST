package com.vMan.alertplugins.customrest;

class RestSenderException
  extends Throwable
{
  public RestSenderException(String message)
  {
    super(message);
  }
  
  public RestSenderException(String message, Throwable cause)
  {
    super(message, cause);
  }
  
  public RestSenderException(Throwable cause)
  {
    super(cause);
  }
}