package com.ubs.portal.webServices.entities;


import com.ubs.portal.webServices.constants.WebservicesResponse;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Created by ubs on 6/27/14.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceResponseInfo  implements Serializable {

    private String processId;
    private WebservicesResponse status;
    private String statusCode;
    private String message;
    private long date;
    private long seq;
    private Integer totalCount;
    private String imei;

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }


    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }



    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public WebservicesResponse getStatus() {
        return status;
    }

    public void setStatus(WebservicesResponse status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }



}
