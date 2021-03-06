package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Iterator;
import java.util.List;

public class BecknPublicKeyFinder implements Extension {
    static {
        Registry.instance().registerExtension("beckn.public.key.get",new BecknPublicKeyFinder());
    }

    @Override
    public void invoke(Object... context) {
        String subscriber_id = (String)context[0];
        String uniqueKeyId = (String)context[1];
        ObjectHolder<String> publicKeyHolder = (ObjectHolder<String>) context[2];

        JSONObject object = new JSONObject();
        object.put("subscriber_id",subscriber_id);
        //object.put("pub_key_id",uniqueKeyId);
        object.put("unique_key_id",uniqueKeyId);


        JSONArray responses = lookup(object);

        if (responses != null && !responses.isEmpty()){
            JSONObject response = (JSONObject) responses.get(0);
            publicKeyHolder.set((String)(response.get("signing_public_key")));
        }
    }

    public static JSONArray lookup(String subscriber_id) {
        JSONObject object = new JSONObject();
        object.put("subscriber_id",subscriber_id);
        return lookup(object);
    }
    public static JSONArray lookup(BecknNetwork network,JSONObject object){
        JSONArray responses = new Call<JSONObject>().method(HttpMethod.POST).url(network.getRegistryUrl() +"/lookup").input(object).inputFormat(InputFormat.JSON)
                .header("content-type", MimeType.APPLICATION_JSON.toString())
                .header("accept",MimeType.APPLICATION_JSON.toString()).getResponseAsJson();
        return responses;

    }
    public static JSONArray lookup(JSONObject object){
        List<BecknNetwork> allNetworks = BecknNetwork.all();
        JSONArray responses = null;
        for (BecknNetwork network : allNetworks){
            responses = lookup(network,object);
            if (responses != null &&  !responses.isEmpty()){
                break;
            }
        }

        if (responses == null) {
            responses = new JSONArray();
        }
        for (Iterator<?> i = responses.iterator(); i.hasNext(); ) {
            JSONObject object1 = (JSONObject) i.next();
            if (!ObjectUtil.equals(object1.get("status"),"SUBSCRIBED")){
                i.remove();
            }
        }

        return responses;

    }
}
