package com.redhat.example.jmx;

public class CacheControllerException extends RuntimeException {

	private static final long serialVersionUID = -2162038647649572066L;

	public CacheControllerException() {
		super();
	}

	public CacheControllerException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CacheControllerException(String message, Throwable cause) {
		super(message, cause);
	}

	public CacheControllerException(String message) {
		super(message);
	}

	public CacheControllerException(Throwable cause) {
		super(cause);
	}

}
