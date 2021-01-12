package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import in.succinct.mandi.db.model.Order;

import java.io.IOException;

public class BeforeOrderSave extends BeforeModelSaveExtension<Order> {
    static {
        registerExtension(new BeforeOrderSave());
    }
    @Override
    public void beforeSave(Order model) {
        Order refOrder = model.getRefOrder();
        if (refOrder == null){
            return;
        }
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_DELIVERED)){
            refOrder.deliver();
        }
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_SHIPPED)){
            refOrder.ship();
        }
        if (model.getRawRecord().isNewRecord()){
            try {
                LuceneIndexer.instance(Order.class).updateDocument(refOrder.getRawRecord());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


    }
}
