package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.db.annotations.column.validations.Enumeration;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.participation.PreferredCarrier;

public interface Order extends in.succinct.plugins.ecommerce.db.model.order.Order {

    @COLUMN_DEF(StandardDefault.NULL)
    public String getPreferredCarrierName();

    @PARTICIPANT
    @Index
    public long getFacilityId();
    public void setFacilityId(long facilityId);
    public Facility getFacility();

    @COLUMN_SIZE(2048)
    public String  getUpiResponse();
    public void setUpiResponse(String upiResponse);


    @Override
    @PARTICIPANT
    @Index
    Long getCreatorUserId();


    void completePayment(boolean save);
    void completeRefund(boolean save);


    @COLUMN_DEF(StandardDefault.ZERO)
    double getAmountPaid();
    void setAmountPaid(double amountPaid);

    @COLUMN_DEF(StandardDefault.ZERO)
    double getAmountRefunded();
    void setAmountRefunded(double amountRefunded);

    @IS_VIRTUAL
    double getAmountPendingPayment();

    @IS_VIRTUAL
    double getAmountToRefund();



    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isPaymentInitialized();
    void setPaymentInitialized(boolean initialized);

    public void initializePayment();
    public void resetPayment(boolean save);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isRefundInitialized();
    void setRefundInitialized(boolean initialized);

    public void initializeRefund();
    public void resetRefund(boolean save);



    @HIDDEN
    public Long getParentOrderId();
    public void setParentOrderId(Long id);
    public Order getParentOrder();

    @IS_VIRTUAL
    public Long getRefOrderId();
    public void setRefOrderId(Long id);
    @IS_VIRTUAL
    public RefOrder getRefOrder();

    @IS_VIRTUAL
    @Index
    public boolean isDeliveryPlanned();

    @IS_VIRTUAL
    @Index
    public boolean isOpen();

    @IS_VIRTUAL
    @Index
    public boolean isCancelled();

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isCustomerPickup();
    public void setCustomerPickup(boolean customerPickup);
    //In case of customer pickup , customer may choose courier, or else the seller may choose courier and bear the cost.

    @IS_VIRTUAL
    public Order getTransportOrder();



    public Double getWeight();
    public void setWeight(Double weight);

    public Long getWeightUomId();
    public void setWeightUomId(Long id);
    public UnitOfMeasure getWeightUom();

}
