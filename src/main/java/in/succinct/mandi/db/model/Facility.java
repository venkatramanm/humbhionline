package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.HOUSEKEEPING;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.db.annotations.column.ui.PROTECTION;

import java.util.List;

public interface Facility extends EncryptedAddress , in.succinct.plugins.ecommerce.db.model.participation.Facility {
    @COLUMN_NAME("ID")
    @PROTECTION
    @HIDDEN
    @HOUSEKEEPING
    @PARTICIPANT(redundant = true)
    public long getSelfFacilityId();
    public void setSelfFacilityId(long id);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isPublished();
    public void setPublished(boolean published);



    @IS_VIRTUAL
    public Facility getSelfFacility();


    @IS_VIRTUAL
    public Double getDistance();
    public void setDistance(Double distance);

    public String getGSTIN();
    public void setGSTIN(String gstin);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isDeliveryProvided();
    public void setDeliveryProvided(boolean deliveryProvided);

    @COLUMN_DEF(StandardDefault.ZERO)
    public double getDeliveryRadius();
    public void setDeliveryRadius(double deliveryRadius);

    @COLUMN_DEF(StandardDefault.ZERO)
    public double getMinFixedDistance();
    public void setMinFixedDistance(double fixedDistance);

    @COLUMN_DEF(StandardDefault.ZERO)
    public double getFixedDeliveryCharges();
    public void setFixedDeliveryCharges(double charges);

    @IS_VIRTUAL
    public double getDeliveryCharges(double distance);

    @IS_VIRTUAL
    public Inventory getDeliveryRule();

    public void publish();
    public void unpublish();


    @IS_VIRTUAL
    boolean isCurrentlyAtLocation();
    void setCurrentlyAtLocation(boolean currentlyAtLocation);

    @Index
    public Long getCreatorUserId();


    public List<Order> getOrders();

    @IS_VIRTUAL
    public int getNumSkus();
}
