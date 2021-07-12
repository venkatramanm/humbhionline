package in.succinct.mandi.agents.beckn;


import com.venky.core.math.DoubleUtils;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.routing.Config;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.Order;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.QuantitySummary;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Select extends BecknAsyncTask {
    public Select(Request request){
        super(request);
    }
    @Override
    public void executeInternal() {
        Request request = getRequest();
        OnSelect onSelect = new OnSelect();
        onSelect.setContext(request.getContext());
        onSelect.getContext().setAction("on_select");

        Message outMessage = new Message();
        onSelect.setMessage(outMessage);

        Order selected = request.getMessage().getSelected();
        Order outSelected = new Order();
        outMessage.setSelected(outSelected);

        in.succinct.mandi.db.model.Order order = in.succinct.mandi.db.model.Order.find(getRequest().getContext().getTransactionId());
        if (order != null){
            if (order.isOnHold() && ObjectUtil.equals(order.getFulfillmentStatus(), in.succinct.mandi.db.model.Order.FULFILLMENT_STATUS_DOWNLOADED)){
                order.setOnHold(false);
                order.setExternalTransactionReference("");
                order.save();
                order.cancel("Cart Dropped");
            }else {
                throw new RuntimeException("Transaction already confirmed!");
            }
        }


        Provider provider = selected.getProvider();
        outSelected.setProvider(provider);

        Location location = provider.getLocations().get(0);
        outSelected.setProviderLocation(location);

        Items outItems = new Items();
        outSelected.setItems(outItems);

        Long facilityId = Long.valueOf(BecknUtil.getLocalUniqueId(String.valueOf(location.getId()),Entity.provider_location));

        Items items = selected.getItems();

        Bucket itemPrice = new Bucket();
        Bucket listedPrice = new Bucket();

        for (int i = 0 ; i < items.size() ; i ++ ){
            Item item = items.get(i);
            Item outItem = new Item();
            outItem.setId(item.getId());

            Long skuId = Long.valueOf(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
            Quantity quantity = item.get(Quantity.class,"quantity");


            QuantitySummary outQuantity = new QuantitySummary();
            outItem.set("quantity",outQuantity);

            outQuantity.setSelected(quantity);

            Inventory inventory = Inventory.find(facilityId,skuId);
            if (inventory == null || inventory.getRawRecord().isNewRecord()){
                throw new RuntimeException("No inventory with provider.");
            }
            itemPrice.increment(inventory.getSellingPrice() * quantity.getCount());
            listedPrice.increment(inventory.getMaxRetailPrice() * quantity.getCount());

            Price price = new Price();
            outItem.setPrice(price);
            price.setCurrency("INR");
            price.setListedValue(inventory.getMaxRetailPrice() * quantity.getCount());
            price.setOfferedValue(inventory.getSellingPrice() * quantity.getCount());
            price.setValue(inventory.getSellingPrice() * quantity.getCount());

            outItems.add(outItem);
        }

        Quote quote = new Quote();
        outSelected.setQuote(quote);

        Price price = new Price();
        quote.setPrice(price);
        price.setListedValue(listedPrice.value());
        price.setValue(listedPrice.value());
        price.setCurrency("INR");
        if (DoubleUtils.compareTo(listedPrice.value(), itemPrice.value() ,2)>0){
            price.setOfferedValue(itemPrice.value());
        }
        quote.setTtl(15L*60L); //15 minutes.

        BreakUp breakUp = new BreakUp();
        BreakUpElement element = breakUp.createElement("item","Total Product",price);
        breakUp.add(element);
        quote.setBreakUp(breakUp);

        send(onSelect);

    }


}
