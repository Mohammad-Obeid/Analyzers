package com.ubs.portal.webServices.entities;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Date;

/**
 * Created by  UBS on 3/5/2017.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SampleDataInfoEntity extends ServiceResponseInfo {
    Long id;
    Long testTypeId;
    Long startPosition;
    Long categoryLength;
    Double factor;
    Long categoryId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTestTypeId() {
        return testTypeId;
    }

    public void setTestTypeId(Long testTypeId) {
        this.testTypeId = testTypeId;
    }

    public Long getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(Long startPosition) {
        this.startPosition = startPosition;
    }

    public Long getCategoryLength() {
        return categoryLength;
    }

    public void setCategoryLength(Long categoryLength) {
        this.categoryLength = categoryLength;
    }

    public Double getFactor() {
        return factor;
    }

    public void setFactor(Double factor) {
        this.factor = factor;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
