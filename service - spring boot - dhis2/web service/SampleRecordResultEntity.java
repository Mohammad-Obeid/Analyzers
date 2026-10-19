package com.ubs.portal.webServices.entities;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * Created by  UBS on 15/12/2017.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SampleRecordResultEntity extends ServiceResponseInfo {

    String sampleId;
    String testTypeId;
    String categoryId;
    String newVal;
    String originalVal;

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public String getTestTypeId() {
        return testTypeId;
    }

    public void setTestTypeId(String testTypeId) {
        this.testTypeId = testTypeId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getNewVal() {
        return newVal;
    }

    public void setNewVal(String newVal) {
        this.newVal = newVal;
    }

    public String getOriginalVal() {
        return originalVal;
    }

    public void setOriginalVal(String originalVal) {
        this.originalVal = originalVal;
    }
}
