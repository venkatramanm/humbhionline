package in.succinct.mandi.agents;

import com.venky.cache.Cache;
import com.venky.core.math.DoubleHolder;
import com.venky.core.math.DoubleUtils;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class SyncPrice implements Task {
    long orderId = -1;
    public SyncPrice(long orderId){
        this.orderId = orderId;
    }
    public SyncPrice(){

    }
    String[] LINE_FIELDS_TO_SYNC = new String[] {"PRODUCT_SELLING_PRICE","PRODUCT_PRICE","C_GST", "I_GST", "S_GST"};

    public void syncOrder(Set<Long> skusWithPriceZero){
        Order order = Database.getTable(Order.class).get(orderId);
        Map<String, Bucket> buckets = new Cache<String, Bucket>(0,0) {

            @Override
            protected Bucket getValue(String field) {
                return new Bucket();
            }
        };
        ObjectHolder<Boolean> shippingWithinSameState = new ObjectHolder<>(null);
        Optional<OrderAddress> addressOptional = order.getAddresses().stream().filter(a-> ObjectUtil.equals(OrderAddress.ADDRESS_TYPE_SHIP_TO,a.getAddressType())).findFirst();
        OrderAddress shipTo = addressOptional.get();
        order.getOrderLines().forEach(orderLine -> {
            Facility shipFrom = orderLine.getShipFrom().getRawRecord().getAsProxy(Facility.class);
            if (shippingWithinSameState.get() == null) {
                shippingWithinSameState.set(ObjectUtil.equals(orderLine.getShipFrom().getStateId(), shipTo.getStateId()));
            }

            if (orderLine.getMaxRetailPrice() < orderLine.getSellingPrice()){
                orderLine.setMaxRetailPrice(orderLine.getSellingPrice());
            }
            Sku sku = orderLine.getSku();
            if (sku.getMaxRetailPrice() > 0){
                orderLine.setMaxRetailPrice(sku.getMaxRetailPrice() * orderLine.getOrderedQuantity());
            }else {
                skusWithPriceZero.add(sku.getId());
            }

            Inventory inventory = orderLine.getInventory().getRawRecord().getAsProxy(Inventory.class);

            if (inventory != null ){
                if (inventory.getReflector().isVoid(inventory.getMaxRetailPrice())){
                    inventory.setMaxRetailPrice(new DoubleHolder(orderLine.getMaxRetailPrice()/orderLine.getOrderedQuantity(),2).getHeldDouble().doubleValue());
                }else if (orderLine.getReflector().isVoid(orderLine.getMaxRetailPrice())){
                    orderLine.setMaxRetailPrice(new DoubleHolder(inventory.getMaxRetailPrice() * orderLine.getOrderedQuantity(),2).getHeldDouble().doubleValue() );
                }
                if (inventory.getReflector().isVoid(inventory.getSellingPrice())) {
                    inventory.setSellingPrice(new DoubleHolder(orderLine.getSellingPrice() / orderLine.getOrderedQuantity(), 2).getHeldDouble().doubleValue());
                }else if (orderLine.getReflector().isVoid(orderLine.getSellingPrice())){
                    orderLine.setSellingPrice(new DoubleHolder(inventory.getSellingPrice() * orderLine.getOrderedQuantity(),2).getHeldDouble().doubleValue());
                }
                inventory.save(); // Could propagate to sku!
            }
            double taxRate = sku.getTaxRate(); //May come from item /asset_code after sku save.

            if (ObjectUtil.isVoid(shipFrom.getGSTIN())){
                taxRate = 0.0;
            }
            orderLine.setPrice(new DoubleHolder(orderLine.getSellingPrice()/(1.0 + taxRate/100.0),2).getHeldDouble().doubleValue());

            double tax = new DoubleHolder((taxRate/100.0)*orderLine.getPrice(),2).getHeldDouble().doubleValue();
            if (shippingWithinSameState.get()){
                orderLine.setCGst(tax/2.0);
                orderLine.setSGst(tax/2.0);
                orderLine.setIGst(0.0);
            }else{
                orderLine.setIGst(tax);
                orderLine.setCGst(0.0);
                orderLine.setSGst(0.0);
            }
            orderLine.save();

            for (String field  : LINE_FIELDS_TO_SYNC) {
                buckets.get(field).increment(orderLine.getReflector().get(orderLine,field));
            }
        });
        if (order.getShippingSellingPrice() > 0){
            double defaultGSTPct = 18.0;
            if (order.getShippingPrice() == 0){
                order.setShippingPrice(new DoubleHolder(order.getShippingSellingPrice()/(1+defaultGSTPct/100.0) , 2).getHeldDouble().doubleValue());
            }
            double shippingTax= order.getShippingSellingPrice() - order.getShippingPrice();
            if (shippingWithinSameState.get()){
                buckets.get("C_GST").increment(shippingTax/2.0);
                buckets.get("S_GST").increment(shippingTax/2.0);
                buckets.get("I_GST").increment(0);
            }else {
                buckets.get("C_GST").increment(0);
                buckets.get("S_GST").increment(0);
                buckets.get("I_GST").increment(shippingTax);
            }
        }


        for (String priceField : LINE_FIELDS_TO_SYNC) {
            order.getReflector().set(order,priceField,buckets.get(priceField).doubleValue());
        }
        order.setSellingPrice(order.getProductSellingPrice() + order.getShippingSellingPrice());
        order.setPrice(order.getProductPrice() + order.getShippingPrice());
        if (order.isOnHold() && ObjectUtil.equals(order.getHoldReason(),Order.HOLD_REASON_CATALOG_INCOMPLETE)) {
            order.setHoldReason(null);
            order.setOnHold(false);
        }
        order.save();

    }
    @Override
    public void execute() {
        Set<Long> skusWithPriceZero = new HashSet<>();
        Database.getInstance().getCurrentTransaction().setAttribute("SyncPriceInProgress",true);
        syncOrder(skusWithPriceZero);

        List<OrderLine> lines = new Select().from(OrderLine.class).where(new Expression(ModelReflector.instance(OrderLine.class).getPool(),"SKU_ID", Operator.IN,skusWithPriceZero.toArray())).execute();
        SortedSet<Long> orderIds = lines.stream().map(OrderLine::getOrderId).collect(Collectors.toCollection(TreeSet::new));
        orderIds.remove(orderId); //Avoid recurrsion.
        for (long id : orderIds){
            new SyncPrice(id).syncOrder(new HashSet<>()); //Syncronize other orders too.
        }
    }
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString(){
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName());
        builder.append("|").append(orderId);
        return builder.toString();
    }
}
