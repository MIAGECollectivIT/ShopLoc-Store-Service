package com.shoploc.store_service.storage;

public class ImageResource {

  private final byte[] bytes;
  private final String contentType;

  public ImageResource(byte[] bytes, String contentType) {
    this.bytes = bytes;
    this.contentType = contentType;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public String getContentType() {
    return contentType;
  }
}
