package com.ubs.portal.enums

/**
 * Created by PC on 11/15/2014.
 */
public enum IntegrationServicesResponse {
    /*
    Error Code	        Error Type
        2XX	             Warning
        3XX	             Missing Parameter/Element
        4XX	             Invalid Value
        5XX	            General Error
        6XX	            Information

     */

    SMS_NOT_SENT('201','SMS_NOT_SENT'),
    FAIL_TO_SEND_SMS('202','FAIL_TO_SEND_SMS'),

    NOT_VALID_DATA('400','NOT_VALID_DATA'),
    LOGIN_FAILED('401','LOGIN_FAILED'),
    CUSTOMER_NOT_VALID('402','CUSTOMER_NOT_VALID'),
    CLIENT_NOT_VALID('403','CLIENT_NOT_VALID'),
    SYSTEM_INTEGRATION_USER_NOT_ACTIVE('404','SYSTEM_INTEGRATION_USER_NOT_ACTIVE'),
    USER_NOT_VALID('405','USER_NOT_VALID'),
    SYSTEM_INTEGRATION_USER_NOT_VALID('406','USER_NOT_VALID'),
    SYSTEM_INTEGRATION_ACCOUNT_LOCKED('407','ACCOUNT_LOCKED'),
    SYSTEM_INTEGRATION_PASSWORD_EXPIRED('408','PASSWORD_EXPIRED'),
    NO_DATA_FOUND('409','NO_DATA_FOUND'),
    NOT_VALID_MACHINE('410','NOT_VALID_MACHINE'),
    NOT_VALID_SAMPLE('411','NOT_VALID_SAMPLE'),
    NOT_VALID_SAMPLE_BARCODE('412','NOT_VALID_SAMPLE_BARCODE'),
    NOT_VALID_SAMPLE_RESULT('413','NOT_VALID_SAMPLE_RESULT'),
    FAILED_TO_TRANSFER_SAMPLE_RESULT('414','FAILED_TO_TRANSFER_SAMPLE_RESULT'),

    FAILED('500','FAILED'),
    DATABASE_ERROR('501','DATABASE_ERROR'),
    USER_DOES_NOT_HAVE_PERMISSION('502','USER_DOES_NOT_HAVE_PERMISSION'),

    SUCCESS('600','SUCCESS')

    String errorCode
    String errormessage

    public IntegrationServicesResponse(String errorCode,String errormessage){
        this.errormessage=errormessage
        this.errorCode=errorCode
    }

    String getErrorCode() {
        return errorCode
    }

    void setErrorCode(String errorCode) {
        this.errorCode = errorCode
    }

    String getErrormessage() {
        return errormessage
    }

    void setErrormessage(String errormessage) {
        this.errormessage = errormessage
    }
}