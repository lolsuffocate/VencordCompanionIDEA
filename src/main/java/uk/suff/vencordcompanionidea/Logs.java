package uk.suff.vencordcompanionidea;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;

public class Logs{
	private static final Logger LOG = Logger.getInstance("Vencord Companion");

	public static void info(Object message){
		LOG.info(String.valueOf(message));
	}

	public static void error(Object message){
		if(!(message instanceof ProcessCanceledException)) LOG.error(String.valueOf(message));
	}

	public static void error(Object message, Throwable t){
		if(!(t instanceof ProcessCanceledException)) LOG.error(String.valueOf(message), t);
	}

	public static void warn(Object message){
		LOG.warn(String.valueOf(message));
	}

	public static void warn(Object message, Throwable t){
		LOG.warn(String.valueOf(message), t);
	}

	public static void debug(Object message){
		LOG.debug(String.valueOf(message));
	}

	public static void debug(Object message, Throwable t){
		LOG.debug(String.valueOf(message), t);
	}

	public static void trace(Object message){
		LOG.trace(String.valueOf(message));
	}

	public static void trace(Object message, Throwable t){
		LOG.trace(String.valueOf(message));
		LOG.trace(t);
	}
}
