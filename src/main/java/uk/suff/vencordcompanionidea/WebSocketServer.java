package uk.suff.vencordcompanionidea;

import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBLabel;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.server.*;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.json.*;
import uk.suff.vencordcompanionidea.actions.Reporter;
import uk.suff.vencordcompanionidea.config.AppSettings;
import uk.suff.vencordcompanionidea.providers.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@WebSocket
public class WebSocketServer{

	// todo: when a project closes, check if any other open projects are Vencord projects and if not, stop the server
	private static int port = 8485;
	public static Server server;
	public static final Set<WebSocketServer> sockets = new HashSet<>();
	private Session session;

	public static void startWebSocketServer() throws Exception{
		if(server != null && !server.isStopped()){
			Logs.info("Server already running");
			return;
		}else if(server == null){
			initServer();
		}

		if(server == null){
			throw new Exception("Could not initialise server");
		}

		new Thread(()->{
			try{
				server.start();
				server.join();
			}catch(Exception e){
				Logs.error(e);
				if(e instanceof IOException){
					Utils.notify("Port already in use", "Port " + port + " is already in use. Please close the other application using this port and try again", NotificationType.ERROR);
					if(server != null){
						try{
							server.stop();
						}catch(Exception ex){
							Logs.error(ex);
						}
					}
					server = null;
				}
			}
		}).start();
	}

	public static void initServer() throws Exception{
		Logs.info("Starting websocketserver");
		server = new Server(port);
		ServerConnector connector = new ServerConnector(server);
		server.addConnector(connector);

		// set timeout
		connector.setIdleTimeout(1000 * 60 * 60);

		JettyWebSocketServlet servlet = new JettyWebSocketServlet(){
			@Override
			protected void configure(JettyWebSocketServletFactory factory){
				factory.addMapping("/", (req, res)->new WebSocketServer());
			}
		};

		ServletContextHandler handler = new ServletContextHandler();
		handler.addServlet(new ServletHolder(servlet), "/");

		JettyWebSocketServletContainerInitializer.configure(handler, null);

		server.setHandler(handler);
		server.setStopAtShutdown(true);
	}

	public static void stopWebSocketServer() throws Exception{
		Logs.info("Stopping websocketserver");
		server.stop();
	}

	@OnWebSocketConnect
	public void onConnect(Session session) throws IOException{
		session.setMaxTextMessageSize(Integer.MAX_VALUE);
		session.setIdleTimeout(Duration.ofMinutes(60));

		Logs.info("Connected to: " + session.getRemoteAddress());

		this.session = session;
		sockets.add(this);
		// notify of successful connection in IDEA
		Utils.notify("Connected to Companion", "Connected to Companion at " + session.getRemoteAddress(), NotificationType.INFORMATION);
	}

	public static ArrayList<Integer> moduleList = new ArrayList<>();
	public static HashMap<String, Consumer<JSONObject>> callbacks = new HashMap<>();
	public static HashMap<Integer, PsiFile> literallyEveryWebpackModule = new HashMap<>();
	public static long cacheSizeBytes = 0;

	@OnWebSocketMessage
	public void onMessage(String message){
		try{
			JSONObject json = new JSONObject(message);

			if(json.has("nonce")){
				String nonce = json.getString("nonce");
				if(callbacks.containsKey(nonce)){
					callbacks.get(nonce).accept(json);
					callbacks.remove(nonce);
				}
			}else if(json.has("type") && json.getString("type").equals("report")){
				Reporter.handleReporterResults(json);
			}else if(json.has("type") && json.getString("type").equals("moduleList")){
				moduleList = new ArrayList<>();
				JSONArray modules = json.getJSONArray("data");
				for(int i = 0; i < modules.length(); i++){
					moduleList.add(modules.getInt(i));
				}
				Logs.info("Received module list: " + moduleList.size() + " modules");
				// this has to be awful
				if(AppSettings.cacheModulesOnConnection()){
					refreshCache();
				}
			}else{
				Logs.info("Received unknown message: " + message);
			}
		}catch(Exception e){
			Logs.info("Error parsing message: " + message);
			Logs.error(e);
		}
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason){
		Logs.info("Closed: " + statusCode + " - " + reason);
		Utils.notify("Disconnected from Companion", "Disconnected from Companion", NotificationType.INFORMATION);
		sockets.remove(this);
	}

	@OnWebSocketError
	public void onError(Throwable t){
		Logs.error(t);
	}

	public static void sendToSockets(JSONObject json){
		if(sockets.isEmpty()){
			Utils.notify("No Companion connected", "No Companion connected. Reconnect via the Vencord Toolbox", NotificationType.WARNING);
			return;
		}
		sockets.forEach(socket->{
			try{
				if(socket.session.isOpen()) socket.session.getRemote().sendString(json.toString());
			}catch(IOException e){
				Logs.error(e);
			}
		});
	}


	public static void refreshCache(JBLabel... component){
		literallyEveryWebpackModule = new HashMap<>();
		cacheSizeBytes = 0;
		Logs.info("Modules: " + moduleList.size());
		for(int moduleId : moduleList){
			extractModuleById(moduleId)
					.thenAccept(json->{
						if(json != null && json.has("data")){
							ApplicationManager.getApplication().runReadAction(()->{
								try{
									String module = json.getString("data");
									// PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(Utils.project);
									// PsiFile fileFromText = psiFileFactory.createFileFromText("module" + moduleId + ".js", JavascriptLanguage.INSTANCE, module);

									// testing indexing psi structure of cached modules, will probably go back
									LightVirtualFile virtualFile = new LightVirtualFile("module" + moduleId + ".js", JavascriptLanguage.INSTANCE, module);
									PsiFile fileFromText = PsiManager.getInstance(Utils.project).findFile(virtualFile);


									if(moduleId == moduleList.getLast()){
										Logs.info("Refreshing Virtual File System after last module: " + moduleId);
										VirtualFileManager.getInstance().asyncRefresh();
									}

									if(fileFromText == null){
										Logs.error("Couldn't create PsiFile for module " + moduleId);
										return;
									}

									literallyEveryWebpackModule.put(moduleId, fileFromText);
									cacheSizeBytes += module.getBytes().length;
									if(component.length > 0){
										component[0].setText(getCachedInfo());
									}

								}catch(Exception e){
									Logs.error(e);
								}
							});
						}
					});
		}
	}

	public static String getCachedInfo(){
		double cacheConveters = Math.round((cacheSizeBytes > 1024 ? cacheSizeBytes > 1024 * 1024 ? ((float) cacheSizeBytes) / (1024f * 1024f) : ((float) cacheSizeBytes) / 1024f : cacheSizeBytes) * 100.0) / 100.0;
		String unit = cacheSizeBytes > 1024 ? cacheSizeBytes > 1024 * 1024 ? "MB" : "KB" : "B";
		return "Cached " + literallyEveryWebpackModule.size() + " modules (" + cacheConveters + unit + ")";
	}

	public static void clearCache(){
		literallyEveryWebpackModule.clear();
		cacheSizeBytes = 0;
	}

	public static CompletableFuture<JSONObject> sendToSocketsAndAwaitResponse(JSONObject json){
		if(sockets.isEmpty()){
			Utils.notify("No Companion connected", "No Companion connected. Reconnect via the Vencord Toolbox", NotificationType.WARNING);
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<JSONObject> future = new CompletableFuture<>();
		String nonce = UUID.randomUUID().toString();
		callbacks.put(nonce, future::complete);
		json.put("nonce", nonce);

		sockets.forEach(socket->{
			try{
				if(socket.session.isOpen()){
					socket.session.getRemote().sendString(json.toString());
				}
			}catch(IOException e){
				Logs.error(e);
			}
		});

		return future;
	}

	public static CompletableFuture<JSONObject> extractModuleById(int moduleId){
		JSONObject request = new JSONObject();
		request.put("type", "extract");
		JSONObject data = new JSONObject();
		data.put("extractType", "id");
		data.put("idOrSearch", moduleId);
		data.put("applyPatch", AppSettings.applyPatchWhenExtractingByDefault());
		request.put("data", data);
		return sendToSocketsAndAwaitResponse(request);
	}

	public static CompletableFuture<JSONObject> extractModuleByFind(String find){
		return extractModuleByFind(find, false);
	}

	public static CompletableFuture<JSONObject> extractModuleByFind(ParsedFind find){
		return sendToSocketsAndAwaitResponse(find.toExtractData());
	}

	public static CompletableFuture<JSONObject> extractModuleByFind(String find, boolean asRegex){
		JSONObject outerData = new JSONObject();
		outerData.put("type", "extract");
		JSONObject data = new JSONObject();
		data.put("extractType", "search");

		data.put("findType", asRegex ? Utils.FindType.REGEX.ordinal() : Utils.FindType.STRING.ordinal());
		data.put("idOrSearch", asRegex ? ("/" + find + "/") : find);
		data.put("pluginName", "");
		data.put("applyPatch", AppSettings.applyPatchWhenExtractingByDefault());

		JSONArray replacements = new JSONArray();

		data.put("replacement", replacements);

		outerData.put("data", data);

		return sendToSocketsAndAwaitResponse(outerData);
	}

	public static CompletableFuture<JSONObject> extractModuleByPatch(ParsedPatch patch, boolean applyPatch){
		JSONObject json = patch.toExtractData(applyPatch);
		return sendToSocketsAndAwaitResponse(json);
	}

	public static CompletableFuture<JSONObject> testModuleByPatch(ParsedPatch patch, boolean applyPatch){
		JSONObject json = patch.toTestData(applyPatch);
		return sendToSocketsAndAwaitResponse(json);
	}

	public static CompletableFuture<JSONObject> diffModuleByPatch(ParsedPatch patch, boolean applyPatch){
		JSONObject json = patch.toDiffData(applyPatch);
		return sendToSocketsAndAwaitResponse(json);
	}

	public static CompletableFuture<JSONObject> testModuleByFind(ParsedFind find){
		return sendToSocketsAndAwaitResponse(find.toFindData());
	}


}
