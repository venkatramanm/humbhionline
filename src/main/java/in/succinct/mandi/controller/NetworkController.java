package in.succinct.mandi.controller;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.ForwardedView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.ServerNode;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.owasp.encoder.Encode;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkController extends Controller {
    public NetworkController(Path path) {
        super(path);
    }

    private View collectResponses(String api){
        Object consolidated = null;
        List<ServerNode> nodes  = new Select().from(ServerNode.class).execute();
        if (nodes.size() < 2){
            return new ForwardedView(getPath(), "", api);
        }


        JSONObject input = null ;
        try {
            input = (JSONObject) JSONValue.parse(new InputStreamReader(getPath().getInputStream()));
        }catch (IOException ex){
            throw new RuntimeException(ex);
        }
        Long nodeId = null;
        String p = new Path(api).parameter();
        try {
            if (p != null ){
                Long  id = Long.valueOf(p);
                nodeId = (id  >> 44);
                if (nodeId == 0L){
                    nodeId = 1L;
                }
            }
        }catch (Exception ex){
            //
        }

        ServerNode self = ServerNode.selfNode();
        String token = String.format("%s:%s",self.getClientId(),self.getClientSecret());
        String auth = String.format("Basic %s", Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        Map<String,List<String>> responseHeaders = new IgnoreCaseMap<>();
        Cookie[] cookies = getPath().getRequest().getCookies();
        Map<String,String> headers = new IgnoreCaseMap<>();
        headers.putAll(getPath().getHeaders());
        if (cookies != null && cookies.length > 0){
            for (int i = 0 ; i <cookies.length ; i  ++ ){
                if (ObjectUtil.equals(cookies[i].getName(),"JSESSIONID")){
                    headers.put("Cookie", String.format("JSESSIONID=%s",cookies[i].getValue()));
                    break;
                }
            }
        }


        for (ServerNode node :nodes ){
            if (nodeId != null && node.getNodeId() != nodeId){
                continue;
            }
            Object output ;
            Call<JSONObject> call = new Call<>();

            if (input == null){
                if (!HttpMethod.GET.toString().equalsIgnoreCase(getPath().getRequest().getMethod())){
                    throw new RuntimeException("Don't know how to call api");
                }
                String q = StringUtil.valueOf(getPath().getRequest().getQueryString());
                if (!ObjectUtil.isVoid(q)){
                    q = String.format("?%s",q);
                }


                String url = String.format("%s/%s%s",node.getBaseUrl(),Encode.forUriComponent(api), q);
                output = call.url(url).headers(headers).
                                header("Content-Type", MimeType.APPLICATION_JSON.toString()).
                                header("KeepAlive","Y").
                                header("Authorization", auth).
                                method(HttpMethod.GET).getResponseStream();
            }else {
                output = call.url(node.getBaseUrl() + "/" + Encode.forUriComponent(api)).inputFormat(InputFormat.JSON)
                        .input(input).headers(headers).
                                header("Content-Type", MimeType.APPLICATION_JSON.toString()).
                                header("KeepAlive","Y").
                                header("Authorization", auth).
                        method(HttpMethod.POST).getResponseStream();
            }
            boolean isJsonApi = false;

            if (responseHeaders.isEmpty()) {
                responseHeaders.putAll(call.getResponseHeaders());
                responseHeaders.remove("Set-Cookie");
                List<String>  ce = responseHeaders.get("Content-Encoding");
                if (ce != null && ce.contains("gzip")){
                    responseHeaders.remove("Content-Encoding");
                }
                responseHeaders.remove("Set-Cookie");
            }
            List<String>  ct = responseHeaders.get("Content-Type");
            if (ct != null && ct.contains(MimeType.APPLICATION_JSON.toString())){
                output = JSONValue.parse(new InputStreamReader((InputStream) output));
                isJsonApi = true;
            }

            if (consolidated == null){
                consolidated = output;
            }else {
                merge(consolidated,output);
            }
            if (nodeId != null || consolidated == null || !isJsonApi){
                break;
            }

        }
        if (consolidated == null){
            return new BytesView(getPath(),new byte[]{});
        }else if (consolidated instanceof InputStream){
            responseHeaders.forEach((k,v)->{
                if (v != null && v.size() > 0){
                    getPath().getResponse().addHeader(k,v.get(0));
                }
            });
            String contentType = responseHeaders.get("Content-Type").get(0);
            return new BytesView(getPath(), StringUtil.readBytes((InputStream) consolidated),contentType);
        }else {
            return new BytesView(getPath(), consolidated.toString().getBytes(StandardCharsets.UTF_8), consolidated instanceof  JSONObject ? MimeType.APPLICATION_JSON :MimeType.TEXT_PLAIN);
        }
    }
    private void merge(Object consolidated, Object out){
        if (out  == null){
            return;
        }
        if (consolidated instanceof JSONObject && out instanceof JSONObject){
            merge((JSONObject)consolidated,(JSONObject) out);
        }else if (consolidated instanceof JSONArray && out instanceof JSONArray){
            merge((JSONArray) consolidated,(JSONArray) out);
        }else  {
            if (!ObjectUtil.equals(consolidated,out)){
                throw new RuntimeException("Cannot consolidate");
            }
        }
    }
    private void merge(JSONArray consolidated, JSONArray out){
        consolidated.addAll(out);
    }
    private void merge(JSONObject consolidated, JSONObject out){
        for (Object k : out.keySet()){
            if (!consolidated.containsKey(k)){
                consolidated.put(k,out.get(k));
            }else{
                merge(consolidated.get(k),out.get(k));
            }
        }
    }

    public View call(String api){
        return collectResponses(api);
    }

}
