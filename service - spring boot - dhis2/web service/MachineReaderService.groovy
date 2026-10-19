package com.ubs.portal.webService

import com.ubs.portal.enums.IntegrationServicesResponse
import com.ubs.portal.lookup.CategoryValueFromMachine
import com.ubs.portal.webServices.constants.WebservicesResponse
import com.ubs.portal.webServices.entities.OrderInfo
import com.ubs.portal.webServices.entities.SampleDataInfoEntity
import com.ubs.portal.webServices.entities.SampleRecordResultEntity
import com.ubs.portal.webServices.entities.ServiceResponseInfo
import com.ubs.portal.webServices.entities.TestInfo

import javax.jws.WebMethod
import javax.jws.WebParam
import javax.jws.WebResult
import javax.jws.WebService


@WebService(serviceName = "MachineReaderService")
class MachineReaderService {

    static expose = ['cxf']

    static transactional = true;

    def commonService
    def sampleService

    @WebMethod(operationName = "getSampleTests")
    @WebResult(name = "getSampleData")
    List<SampleDataInfoEntity> getSampleData(
            @WebParam(name = "machineId") String machineId,
            @WebParam(name = "sampleId") String sampleId,
            @WebParam(name = "requestTime") String requestTime,
            @WebParam(name = "key") String key) {

        List<SampleDataInfoEntity> retData = []

        println 'Receive data from Arch ' + machineId + ', sample id ' + sampleId

        try {
            if (machineId) {
                if (sampleId) {
                    retData = sampleService.getSampleData(new Long(machineId), new Long(sampleId))
                    if (retData) {
                        retData.add(0, new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: commonService.getMessage("WebservicesResponse.SUCCESS"),
                                date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0))
                    } else {
                        retData << new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: commonService.getMessage("WebservicesResponse.NO_DATA_FOUND"), date: new Date().getTime(), seq: 0)
                    }
                } else {
                    retData << new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE"), date: new Date().getTime(), seq: 0)
                }
            } else {
                retData << new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_MACHINE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_MACHINE"), date: new Date().getTime(), seq: 0)
            }
        } catch (Exception e) {
            e.printStackTrace()
            retData << new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0)
        }

        if (retData?.size() == 0) {
            retData << new SampleDataInfoEntity(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_DATA.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_DATA"), date: new Date().getTime(), seq: 0)
        }
        retData
    }


    @WebMethod(operationName = "transferSampleResults")
    @WebResult(name = "transferSampleResults")
    List<ServiceResponseInfo> transferSampleResults(
            @WebParam(name = "machineId") String machineId,
            @WebParam(name = "sampleId") String sampleId,
            @WebParam(name = "sampleId") String barcode,
            @WebParam(name = "sampleRecordResultsList") SampleRecordResultEntity[] sampleRecordResultsList,
            @WebParam(name = "requestTime") String requestTime,
            @WebParam(name = "key") String key) {

        List<ServiceResponseInfo> retData = []
        try {
            if (machineId) {
                if (sampleId) {
                    if (sampleRecordResultsList && sampleRecordResultsList.length > 0) {

                        boolean isRecordsSaved = sampleService.recordSerialData(new Long(machineId), new Long(sampleId), barcode, sampleRecordResultsList)

                        if (isRecordsSaved) {
                            retData.add(0, new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: commonService.getMessage("WebservicesResponse.SUCCESS"),
                                    date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0))
                        } else {
                            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.FAILED_TO_TRANSFER_SAMPLE_RESULT.errorCode, message: commonService.getMessage("WebservicesResponse.FAILED_TO_TRANSFER_SAMPLE_RESULT"), date: new Date().getTime(), seq: 0)
                        }
                    } else {
                        retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE_RESULT.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE_RESULT"), date: new Date().getTime(), seq: 0)
                    }
                } else {
                    retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE"), date: new Date().getTime(), seq: 0)
                }
            } else {
                retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_MACHINE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_MACHINE"), date: new Date().getTime(), seq: 0)
            }
        } catch (Exception e) {
            e.printStackTrace()
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0)
        }

        if (retData?.size() == 0) {
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_DATA.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_DATA"), date: new Date().getTime(), seq: 0)
        }
        retData
    }

    @WebMethod(operationName = "transferSampleResultsBySerial")
    @WebResult(name = "transferSampleResultsBySerial")
    List<ServiceResponseInfo> transferSampleResultsBySerial(
            @WebParam(name = "machineId") String machineId,
            @WebParam(name = "barcodeLocation") String barcodeLocation,
            @WebParam(name = "serialData") String serialData,
            @WebParam(name = "requestTime") String requestTime,
            @WebParam(name = "key") String key) {

        List<ServiceResponseInfo> retData = []
        try {
            if (machineId) {
                if (barcodeLocation) {
                    if (serialData) {
                        retData = sampleService.recordSerialData(machineId, barcodeLocation, serialData)
                        if (retData) {
                            retData.add(0, new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: "WebservicesResponse.SUCCESS",
                                    date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0))
                        } else {
                            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: "WebservicesResponse.NO_DATA_FOUND", date: new Date().getTime(), seq: 0)
                        }
                    } else {
                        retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE_BARCODE.errorCode, message: "WebservicesResponse.NOT_VALID_SAMPLE_BARCODE", date: new Date().getTime(), seq: 0)
                    }
                } else {
                    retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE.errorCode, message: "WebservicesResponse.NOT_VALID_SAMPLE", date: new Date().getTime(), seq: 0)
                }
            } else {
                retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_MACHINE.errorCode, message: "WebservicesResponse.NOT_VALID_MACHINE", date: new Date().getTime(), seq: 0)
            }
        } catch (Exception e) {
            e.printStackTrace()
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0)
        }

        if (retData?.size() == 0) {
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_DATA.errorCode, message: "WebservicesResponse.NOT_VALID_DATA", date: new Date().getTime(), seq: 0)
        }
        retData
    }



    @WebMethod(operationName = "transferSampleTestsToArchitect")
    @WebResult(name = "transferSampleTestsToArchitect")
    List<OrderInfo> transferSampleTestsToArchitect(
            @WebParam(name = "machineBrand") String machineBrand,
            @WebParam(name = "sampleId") String sampleId
    ) {

        List<OrderInfo> retData = []


        try {
            if (sampleId && sampleId.isNumber()) {
                retData = sampleService.transferArchitectSampleTests(machineBrand, sampleId)
                if (retData && retData?.size() > 0) {
                    retData.add(0, new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: commonService.getMessage("WebservicesResponse.SUCCESS"),
                            date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0, status: WebservicesResponse.SUCCESS))
                } else {
                    retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: commonService.getMessage("WebservicesResponse.NO_DATA_FOUND"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NO_RESULT_FOUND)
                }
            } else {
                retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NOT_VALID_DATA)
            }

        } catch (Exception e) {
            e.printStackTrace()
            retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0, status: WebservicesResponse.DATABASE_ERROR)
        }

        if (retData?.size() == 0) {
            retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: commonService.getMessage("WebservicesResponse.NO_DATA_FOUND"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NO_RESULT_FOUND)
        }
        retData
    }





    @WebMethod(operationName = "transferSampleTestsToArchitectByBranch")
    @WebResult(name = "transferSampleTestsToArchitectByBranch")
    List<OrderInfo> transferSampleTestsToArchitectByBranch(
            @WebParam(name = "machineBrand") String machineBrand,
            @WebParam(name = "sampleId") String sampleId,
            @WebParam(name = "branchId") String branchId
    ) {

        List<OrderInfo> retData = []


        try {
            if (sampleId && sampleId.isNumber()) {
                retData = sampleService.transferArchitectSampleTests(machineBrand, sampleId,branchId)
                if (retData && retData?.size() > 0) {
                    retData.add(0, new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: commonService.getMessage("WebservicesResponse.SUCCESS"),
                            date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0, status: WebservicesResponse.SUCCESS))
                } else {
                    retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: commonService.getMessage("WebservicesResponse.NO_DATA_FOUND"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NO_RESULT_FOUND)
                }
            } else {
                retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NOT_VALID_DATA)
            }

        } catch (Exception e) {
            e.printStackTrace()
            retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0, status: WebservicesResponse.DATABASE_ERROR)
        }

        if (retData?.size() == 0) {
            retData << new OrderInfo(processId: 0, statusCode: IntegrationServicesResponse.NO_DATA_FOUND.errorCode, message: commonService.getMessage("WebservicesResponse.NO_DATA_FOUND"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NO_RESULT_FOUND)
        }
        retData
    }


    @WebMethod(operationName = "transferSampleResultsFromArchitect")
    @WebResult(name = "transferSampleResultsFromArchitect")
    List<ServiceResponseInfo> transferSampleResultsFromArchitect(
            @WebParam(name = "machineBrand") String machineBrand,
            @WebParam(name = "resultList") List<TestInfo> resultList
    ) {

        List<ServiceResponseInfo> retData = []
        try {
            if (resultList && resultList.size() > 0) {

                boolean isRecordsSaved = sampleService.transferDataFromMachine(resultList, machineBrand)

                if (isRecordsSaved) {
                    retData.add(0, new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.SUCCESS.errorCode, message: commonService.getMessage("WebservicesResponse.SUCCESS"),
                            date: new Date().getTime(), seq: 0, totalCount: retData.size() ?: 0, status: WebservicesResponse.SUCCESS))
                } else {
                    retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.FAILED_TO_TRANSFER_SAMPLE_RESULT.errorCode, message: commonService.getMessage("WebservicesResponse.FAILED_TO_TRANSFER_SAMPLE_RESULT"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.FAILED)
                }
            } else {
                retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_SAMPLE_RESULT.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_SAMPLE_RESULT"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NOT_VALID_DATA)
            }


        } catch (Exception e) {
            e.printStackTrace()
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.DATABASE_ERROR.errorCode, message: e.getMessage(), date: new Date().getTime(), seq: 0, status: WebservicesResponse.DATABASE_ERROR)
        }

        if (retData?.size() == 0) {
            retData << new ServiceResponseInfo(processId: 0, statusCode: IntegrationServicesResponse.NOT_VALID_DATA.errorCode, message: commonService.getMessage("WebservicesResponse.NOT_VALID_DATA"), date: new Date().getTime(), seq: 0, status: WebservicesResponse.NO_RESULT_FOUND)
        }
        retData
    }

}
