package com.ubs.mlms

import com.ubs.common.tools.Cryptography
import com.ubs.common.tools.NumberToArabicWord
import com.ubs.common.tools.NumberToWord
import com.ubs.mlms.Financial.Invoice
import com.ubs.mlms.Financial.Receipt
import com.ubs.mlms.Financial.claims.FinanceClaims
import com.ubs.mlms.api.JournalVoucher
import com.ubs.mlms.enums.FileDirectory
import com.ubs.mlms.enums.JournalVoucherStatus
import com.ubs.mlms.lookup.InvoiceCancelReason
import com.ubs.mlms.point.PointTransactionService
import com.ubs.mlms.relatedParty.Doctor
import com.ubs.mlms.relatedParty.Institutes
import com.ubs.mlms.relatedParty.MedicalCenter
import com.ubs.mlms.relatedParty.MedicalCenterService
import com.ubs.mlms.relatedParty.Patient
import com.ubs.mlms.reports.CampaignView
import com.ubs.mlms.reports.DoctorSupervisorView
import com.ubs.mlms.reports.FinancialReportView
import com.ubs.mlms.reports.JournalVoucherView
import com.ubs.mlms.reports.SampleTestLateView
import com.ubs.mlms.reports.SampleView
import com.ubs.mlms.reports.TransferredTestsView
import com.ubs.mlms.security.UserLogFile
import com.ubs.mlms.test.Campaign
import com.ubs.mlms.test.Sample
import com.ubs.mlms.test.SampleTest
import com.ubs.mlms.test.TestType
import grails.converters.JSON

import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFFont
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.IndexedColors
import org.codehaus.groovy.grails.plugins.jasper.JasperExportFormat
import org.codehaus.groovy.grails.plugins.jasper.JasperReportDef
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.hibernate.StatelessSession
import org.hibernate.Transaction
import org.springframework.web.servlet.support.RequestContextUtils
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.util.Environment
import grails.orm.PagedResultList

import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Image
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfImportedPage
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import utils.DateUtils
import utils.SystemConstant
import java.sql.Date

class ReportController {


    def jasperService
    def springSecurityService
    def reportService
    def commonService
    def priceQuotationService
    def transferPackageService
    def sampleService
    def exportService
    def grailsApplication
    def sampleTestService
    def financeClaimsService
    def userLogFileService
    def userService
    def plsqlService
    def sessionFactory
    def getResourcesBundleReport(Locale reportLocale) {
        String localString = reportLocale.toString().split("_")[0]
        def bundlePath
        if (Environment.current == Environment.DEVELOPMENT) {
            bundlePath = System.properties['base.dir'] + CH.config.jasper.dir.bundle
        } else if (Environment.current == Environment.PRODUCTION) {
            bundlePath = SCH.servletContext.getRealPath('/') + CH.config.jasper.dir.bundle
        } else {
            bundlePath = System.properties['base.dir'] + CH.config.jasper.dir.bundle
        }
        def messagePath = bundlePath + "messages" + "_" + localString + ".properties"
        BufferedReader myBuffer
        ResourceBundle reportBundle
        try {
            myBuffer = new BufferedReader(new InputStreamReader(new FileInputStream(messagePath), "UTF-8"));
            reportBundle = new PropertyResourceBundle(myBuffer)
        }
        catch (Exception e) {
            def defaultMessagePath = bundlePath + "messages.properties"
            myBuffer = new BufferedReader(new InputStreamReader(new FileInputStream(defaultMessagePath), "UTF-8"));
            reportBundle = new PropertyResourceBundle(myBuffer)
        }
        return reportBundle
    }


    def generateReport(
            def reportPrefix,
            def reportData, boolean isList, Map otherParams, boolean withLocal, List<Map> attachedFilesList = [:] , isMultiReport=false) {
        if (!session.medicalCenterId) return
        def medicalCenter = MedicalCenter.get(session?.medicalCenterId)
        String userName = springSecurityService?.principal?.username
        def reportLocale = RCU.getLocale(request)
        def msgPrefix = reportPrefix + ".report.label"
        def reportTitle = "${message(code: msgPrefix, default: "Report")}"
        def reportName = reportPrefix + "LTR.jasper"
        if (reportLocale.language == "ar" || !withLocal)
            reportName = reportPrefix + ".jasper"

        def reportPath = SCH.servletContext.getRealPath('/') + (CH.config.jasper.dir.reports)
        def resourcesBundle = getResourcesBundleReport(reportLocale)
        def parameters = [
                encoding              : 'UTF-8',
                REPORT_TITLE          : reportTitle,
                REPORT_PATH           : reportPath,
                REPORT_IMAGE_PATH     : reportPath,
                REPORT_RESOURCE_BUNDLE: resourcesBundle,
                REPORT_LOCALE         : reportLocale,
                USER_NAME             : userName,
                IMAGE                 : medicalCenter?.logo?.filePayload,
                PHONE                 : medicalCenter?.telephone,
                HEADER_AR_NAME        : medicalCenter?.arName,
                HEADER_EN_NAME        : medicalCenter?.enName,
                REPORT_NAME           : reportTitle,
                REGISTRATION_NUMBER   : medicalCenter?.registrationNumber,
        ]
        if (otherParams?.size() > 0)
            parameters.putAll(otherParams)


        def jasperReportData = null

        if (!isList) {
            jasperReportData = [reportData]
        } else {
            jasperReportData = reportData
        }

        JasperReportDef reportDef = new JasperReportDef(name: reportName, reportData: jasperReportData, fileFormat: JasperExportFormat.PDF_FORMAT, parameters: parameters, locale: reportLocale)
        if (attachedFilesList?.size() > 0) {
            def report = jasperService.generateReport(reportDef).toByteArray()
            Document document = new Document();
            PdfWriter writer = PdfWriter.getInstance(document, response?.getOutputStream());
            document.open();
            PdfContentByte cb = writer.getDirectContent();
            int currentPageNumber = 0
            int pageOfCurrentReaderPDF = 0

            //add report
            PdfReader reportReader = new PdfReader(report);
            pageOfCurrentReaderPDF = 0
            PdfImportedPage pageReport;
            while (pageOfCurrentReaderPDF < reportReader.getNumberOfPages()) {
                document.newPage()
                pageOfCurrentReaderPDF++
                currentPageNumber++
                pageReport = writer.getImportedPage(reportReader, pageOfCurrentReaderPDF)
                cb.addTemplate(pageReport, 0, 0)
            }
            document.newPage()
            if (attachedFilesList?.size() > 0) {
                attachedFilesList.each { Map documentInstance ->
                    if (documentInstance) {
                        try {
                            if (documentInstance && documentInstance.fileContentType?.contains("image")) {
                                document.newPage()
                                Image image = Image.getInstance(documentInstance?.bytes);
                                image.setAlignment(Element.ALIGN_JUSTIFIED)
                                int indentation = 0;
                                float scaler = ((document.getPageSize().getWidth() - document.leftMargin()
                                        - document.rightMargin() - indentation) / image.getWidth()) * 100;
                                image.scalePercent(scaler);
                                document.newPage()
                                document.add(image);
                                currentPageNumber++
                            } else if (documentInstance.fileContentType?.contains("text")) {
                                document.newPage()
                                InputStream inputStream = new ByteArrayInputStream(documentInstance?.bytes);
                                BufferedReader br = null;
                                try {
                                    BaseFont courier = BaseFont.createFont(reportPath + "arial.ttf", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                                    Font myFont = new Font(courier);
                                    myFont.setStyle(Font.NORMAL);
                                    myFont.setSize(11);
                                    document.add(new Paragraph("\n"));
                                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream)
                                    br = new BufferedReader(inputStreamReader);
                                    String strLine;
                                    while ((strLine = br.readLine()) != null) {
                                        PdfPTable table = new PdfPTable(1);
                                        PdfPCell cell = new PdfPCell(new Phrase(strLine + "\n", myFont));
                                        cell.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                                        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                                        cell.setBorder(Rectangle.NO_BORDER)
                                        table.addCell(cell);
                                        document.add(table);
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    if (br != null)
                                        br.close();
                                }
                                document.newPage()
                                currentPageNumber++
                            } else if (documentInstance.fileContentType?.contains("pdf")) {
                                InputStream inputStream = new ByteArrayInputStream(documentInstance?.bytes);
                                PdfReader pdfReader = new PdfReader(inputStream);
                                pageOfCurrentReaderPDF = 0
                                PdfImportedPage page;
                                while (pageOfCurrentReaderPDF < pdfReader.getNumberOfPages()) {
                                    document.newPage()
                                    pageOfCurrentReaderPDF++
                                    currentPageNumber++
                                    page = writer.getImportedPage(pdfReader, pageOfCurrentReaderPDF)
                                    cb.addTemplate(page, 0, 0)
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace()
                            BaseFont courier = BaseFont.createFont(reportPath + "arial.ttf", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                            Font myFont = new Font(courier);
                            myFont.setStyle(Font.NORMAL);
                            myFont.setSize(11);
                            document.add(new Paragraph("\n"));
                            String strLine = "Fail to print the document (" + documentInstance?.title + ")";
                            PdfPTable table = new PdfPTable(1);
                            PdfPCell cell = new PdfPCell(new Phrase(strLine + "\n", myFont));
                            cell.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                            cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                            cell.setBorder(Rectangle.NO_BORDER)
                            table.addCell(cell);
                            document.add(table);
                        }
                    } else { // render empty page
                        document.newPage()
                        writer.setPageEmpty(false)
                    }
                }
            }
            document.close()
            return document
        } else {
          if(!isMultiReport){
              response.setContentType('application/pdf');
              response.setHeader("Content-disposition", "filename=${reportPrefix}.pdf");
              response.outputStream << jasperService?.generateReport(reportDef)?.toByteArray()

          }else{
              return  reportDef
          }

        }
    }


    def invoiceReport = {
        def reportLocale = RCU.getLocale(request)
        Invoice invoice = Invoice.get(Cryptography.decryptWithDate(SystemConstant.invoiceKey, params["id"]))
        /*
        1.add to report this field if not exist
        (currentStatus.transactionStatus)
        2.then to check any status use
        (currentStatus.transactionStatus == "CANCEL")
         */
        def priceWord
        if (reportLocale.language == "ar") {
            priceWord = NumberToArabicWord.convertNumberToWord(invoice?.sample?.totalPrice?.toString())
        } else {
            priceWord = NumberToWord.convertNumberToWord(invoice?.sample?.totalPrice?.toString())
        }
        Boolean hasReferral = invoice.sample?.testType.findAll{it.isReferral.equals(true)}?.size()>0?true:false
        invoice.sample.sampleTest.sort({it.id})
        generateReport("invoice", invoice, false, [PRICE_WORDS: priceWord , HAS_REFERRAL:hasReferral], true, null)
        if (!invoice.isPrinted) {
            invoice.isPrinted = true
            invoice.save(flush: true)
        }
    }

    def receiptReport = {
        def reportLocale = RCU.getLocale(request)
        def receipt = Receipt.get(Cryptography.decryptWithDate(SystemConstant.receiptKey, params["id"]))
        /*
            1.add to report this field if not exist
            (invoice.currentStatus.transactionStatus)
            2.then to check any status use
            (invoice.currentStatus.transactionStatus == "CANCEL")
        */
        def priceWord
        if (reportLocale.language == "ar") {
            priceWord = NumberToArabicWord.convertNumberToWord(receipt?.transactionAmount?.toString())
        } else {
            priceWord = NumberToWord.convertNumberToWord(receipt?.transactionAmount?.toString())
        }
        generateReport("receipt", receipt, false, [PRICE_WORDS: priceWord], true, null)
    }

    def labSheetReport = {
        Boolean isUrgent
        def sampleInstance
        def clinicalDate
        if(params.id){
            sampleInstance = sampleService.getInstance(params , true)
            isUrgent = sampleInstance?.isUrgent
            clinicalDate = sampleInstance?.clinicalData?.name
            params.testId = sampleInstance?.sampleTest.find {it.id == Cryptography.decryptWithDate(SystemConstant.sampleTestKey, params.sampleTestId)}?.testTypeId
           }

        generateReport("labSheet", reportService.labSheetData(params), true, [IS_URGENT:isUrgent,CLINICAL_DATA:clinicalDate], true, null)
    }

    def resultSheetReport = {
//        List<Map> attachedResult = sampleService.getAttachedResult(params)
        def sampleInstance = Sample.get(Cryptography.decryptWithDate(SystemConstant.sampleKey, params["id"]))
        List<InputStream> inputPdfList = new ArrayList<InputStream>();
        if (sampleInstance) {

            response.setContentType("application/pdf");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader("Content-disposition", "filename=resultSheetReport.pdf");

            File file
            if(sampleInstance?.systemFileName){
                file =commonService.getFile(FileDirectory.ARCHIVE_RESULT, sampleInstance?.systemFileName)
            }

            if(file?.exists())
            inputPdfList.add(new FileInputStream(file));

        }
        JasperReportDef  result =  generateReport("resultSheet", reportService.resultSheetData(params), true, [:], true, null , true)
        InputStream  mainReport = new ByteArrayInputStream(jasperService?.generateReport(result)?.toByteArray());
        inputPdfList.add( mainReport )

        reportService.mergePdfFiles(inputPdfList , response.outputStream )

    }



    def institutesResultSheet = {
        [fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def institutesResultSheetReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
//        List<Map> attachedResult = sampleService.getMultiAttachedResult(params)
        generateReport("instituteResultSheet", reportService.instituteResultSheetData(params), true, [:], true, null)
    }

    def resultPartialReport = {
//        List<Map> attachedResult = sampleService.getAttachedResult(params)
        boolean isDraft = params.boolean("isDraft")
        if (isDraft) {

            generateReport("draftSheet", reportService.resultPartialData(params), true, [:], true, [])
        } else {
            generateReport("resultSheet", reportService.resultPartialData(params), true, [:], true, [])
        }

    }

    def priceQuotationReport = {
        generateReport("priceQuotation", priceQuotationService.search(params), true, [:], true, null)
    }


    def transferPackageReport = {
        generateReport("transferPackage", transferPackageService.getTransferPackageBean(Cryptography.decryptWithDate(SystemConstant.transferPackageKey, params["id"])), false, [:], true, null)
    }
    def financialList = {
        Integer financeReportDays = 0
        if(!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")){
            financeReportDays = (MedicalCenter.get(session.currentBranchId)?.financialReportDays)?:5
        }

        [DOMAIN_COLUMNS: reportService.FINANCIAL_DOMAIN_COLUMNS, fromDate: reportService.currentDate(), toDate: reportService.currentDate() , financeReportDays:financeReportDays]
    }

    def filterFinancial = {
        params.medicalCenterId=session.medicalCenterId
        PagedResultList pagedResultList = new PagedResultList(reportService.searchFinancial(params), reportService.getFinancialCount(params))
        PagedResultList<FinancialReportView> results = pagedResultList
        def dataToRender = commonService.generateDataToRender(reportService.FINANCIAL_DOMAIN_COLUMNS, results, params)
        render dataToRender as JSON
    }

    def financialReport = {

        params.put('max', Integer.MAX_VALUE)
        params.fromDate = params.fromDate ? params.fromDate : reportService.currentDate(true)
        params.toDate = params.toDate ? params.toDate : reportService.currentDate(true)
        if (params.boolean("isExcel")) {
            def data = reportService.searchFinancialExcel(params)
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'financialReport.list'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = ["paymentMethod", "branchName", "sampleCode", "transactionDate", "invoiceNum", "patientName", "onAccount", "subtotal",
                           "discount", "debit", "credit", "balance"]
            Map labels = [:]
            def columnWidth = []
            fields.eachWithIndex { row, index ->
                labels.put(row, "${message(code: 'report.' + row + '.label')}")
                if (index == 5) {
                    columnWidth << 0.5
                } else if (index == 0) {
                    columnWidth << 0.2
                } else {
                    columnWidth << 0.15
                }
            }
            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [transactionDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, data,
                    fields, labels, formatters, parameters)
        }
        else {
            if(params.boolean("isExcel") != null && params.boolean("isExcel") == Boolean.FALSE){
                generateReport("financial",reportService.searchFinancialMap(params),true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
            }else{
                generateReport("financial",reportService.searchFinancial(params),true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
            }
        }
    }


    def claimList = {
        [DOMAIN_COLUMNS: reportService.CLAIM_DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def filterClaim = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        PagedResultList results = reportService.searchClaim(params)
        def dataToRender = commonService.generateDataToRender(reportService.CLAIM_DOMAIN_COLUMNS, results, params)
        render dataToRender as JSON
    }

    def claimReport = {
        params.put('max', Integer.MAX_VALUE)
        params.put('offset', 0)
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

        if (params.boolean("isExcel")) {
            def data = reportService.searchClaimExcel(params)
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = ["institutesName", "beneficiaryName",  "sampleDate", "sampleCode", "patientName", "invoiceNumber", "formNumber", "subtotal",
                           "discount", "extraDiscount", "receipts", "net"]
            Map labels = [:]
            def columnWidth = []
            fields.eachWithIndex { row, index ->
                labels.put(row, "${message(code: 'claim.' + row + '.label')}")
                if (index == 3 || index == 0) {
                    columnWidth << 0.3
                } else {
                    columnWidth << 0.15
                }
            }
            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, data,
                    fields, labels, formatters, parameters)
        } else {
            def branchName = MedicalCenter.get(params.long("branchId"))?.enName
            generateReport("claim", reportService.searchClaim(params)?.list, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, BRANCH_NAME: branchName ?: ''], true, null)
        }
    }

    def claimInvoiceListReport = {
        def reportLocale = RCU.getLocale(request)
        List<Long> lisOfIds = []
        params.isReport =true
        List<FinanceClaims> claimsInstanceList =  financeClaimsService.search(params).list

      List<JasperReportDef> jasperReportDefArrayList =[]
        claimsInstanceList?.eachWithIndex{ claimsInstance, index ->
            def priceWord
            def originalPriceWord
            if (reportLocale.language == "ar") {
                originalPriceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount)).toString())
                priceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.net)).toString())


            } else {
                originalPriceWord = NumberToWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount).round(2)).toString())
                priceWord = NumberToWord.convertNumberToWord(((claimsInstance?.net).round(2)).toString())
            }
            jasperReportDefArrayList.add(generateReport("claimInvoice", claimsInstance, false, [PRICE_WORDS: priceWord, ORGINAL_PRICE_WORDS: originalPriceWord], true, null , true))

        }
             response.outputStream << jasperService.generateReport(jasperReportDefArrayList)?.toByteArray()

      //  session.flush()

    }

    def claimInvoiceReport = {

        def reportLocale = RCU.getLocale(request)
        def claimsInstance = FinanceClaims.get(params.long('id'))
        /*
        1.add to report this field if not exist
        (currentStatus.transactionStatus)
        2.then to check any status use
        (currentStatus.transactionStatus == "CANCEL")
         */
        def priceWord
        def originalPriceWord
        if (reportLocale.language == "ar") {
            originalPriceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount)).toString())
            priceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.net)).toString())


        } else {

            originalPriceWord = NumberToWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount)).toString())
            priceWord = NumberToWord.convertNumberToWord(((claimsInstance?.net)).toString())
        }
//         def testList = SampleTest.findAllBySample(Sample.findBySampleCode(claimsInstance.sampleCode)).testType
//        claimsInstance.setTestType()

        generateReport("claimInvoice", claimsInstance, false, [PRICE_WORDS: priceWord, ORGINAL_PRICE_WORDS: originalPriceWord], true, null)


//        def reportLocale = RCU.getLocale(request)
//
//        List<Long>  ids = []
//
//        def claimsInstance = FinanceClaims.createCriteria().list(){
//
//            and{
//                inList("id",[29682 as Long,29686 as Long])
//            }
//        }
//        /*
//        1.add to report this field if not exist
//        (currentStatus.transactionStatus)
//        2.then to check any status use
//        (currentStatus.transactionStatus == "CANCEL")
//         */
//        def priceWord
//        def originalPriceWord
//        if (reportLocale.language == "ar") {
//            originalPriceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount)).toString())
//            priceWord = NumberToArabicWord.convertNumberToWord(((claimsInstance?.net)).toString())
//
//
//        } else {
//
//            originalPriceWord = NumberToWord.convertNumberToWord(((claimsInstance?.subtotal - claimsInstance.discount)).toString())
//            priceWord = NumberToWord.convertNumberToWord(((claimsInstance?.net)).toString())
//        }
////         def testList = SampleTest.findAllBySample(Sample.findBySampleCode(claimsInstance.sampleCode)).testType
////        claimsInstance.setTestType()
//
//        generateReport("claimInvoice", claimsInstance, false, [PRICE_WORDS: priceWord, ORGINAL_PRICE_WORDS: originalPriceWord], true, null)

    }



    def financeClaimReport = {

        params.put('max', Integer.MAX_VALUE)
        params.put('offset', 0)
        params.put('isReport', true)
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

        if (params.boolean("isExcel")) {
            def data = reportService.searchFinanceClaimExcel(params)
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = ["institutesName", "beneficiaryName" , "sampleDate", "sampleCode", "patientName", "invoiceNumber", "formNumber", "subtotal", "discount", "extraDiscount", "receipts", "net"]
            Map labels = [:]
            def columnWidth = []
            fields.eachWithIndex { row, index ->
                labels.put(row, "${message(code: 'financeClaim.' + row + '.label')}")
                if (index == 3 || index == 0) {
                    columnWidth << 0.3
                } else {
                    columnWidth << 0.15
                }
            }
            def dateFormat = { domain, value ->
                if (value instanceof  java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, data,
                    fields, labels, formatters, parameters)
        } else {
            generateReport("financeClaim",financeClaimsService.search(params)?.list, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
        }

    }

    def advFinanceClaimReport = {

        params.put('max', Integer.MAX_VALUE)
        params.put('offset', 0)
        params.put('isReport', true)
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

        if (params.boolean("isExcel")) {
            def data = reportService.searchFinanceClaimExcel(params)
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = ["institutesName", "beneficiaryName" ,"sampleDate", "sampleCode", "patientName", "invoiceNumber", "formNumber", "clinicalDiagnosis","sharePercentage" , "maximumCoverage" , "subtotal",
                           "discount", "extraDiscount", "receipts", "net"]
            Map labels = [:]
            def columnWidth = []
            fields.eachWithIndex { row, index ->
                labels.put(row, "${message(code: 'financeClaim.' + row + '.label')}")
                if (index == 3 || index == 0) {
                    columnWidth << 0.3
                } else {
                    columnWidth << 0.15
                }
            }
            def dateFormat = { domain, value ->
                if (value instanceof  java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, data,
                    fields, labels, formatters, parameters)
        } else {
            generateReport("advFinanceClaim",financeClaimsService.search(params)?.list, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
        }

    }

    def barCodeReport = {
        generateReport("barCode", reportService.barCodeData(params), true, [:], false, null)
    }

    def downloadTestProcedureFile = {
        def testType
        if (params.boolean("isSampleTest")) {
            def sampleTest = SampleTest.get(Cryptography.decryptWithDate(SystemConstant.sampleTestKey, params["id"]))
            testType = sampleTest?.testType
        } else {
            testType = TestType.get(Cryptography.decryptWithDate(SystemConstant.testTypeKey, params["id"]))
        }
        if (testType) {
//            if (testType?.userFileName?.endsWith("txt")) {
            response.setContentType("application/force-download");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader("Content-Disposition", "attachment; filename=${testType?.userFileName}");
//            }
            response.outputStream << commonService.getFileBytes(FileDirectory.TEST, testType?.systemFileName)
        }
    }


    def downloadTestArchiveResultFile = {

        def sampleInstance = Sample.get(Cryptography.decryptWithDate(SystemConstant.sampleKey, params["id"]))

        if (sampleInstance) {
//            if (sampleInstance?.archiveResultFileName?.endsWith("txt")) {
            response.setContentType("application/force-download");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader("Content-Disposition", "attachment; filename=${sampleInstance?.archiveResultFileName}");
//            }
            response.outputStream << commonService.getFileBytes(FileDirectory.ARCHIVE_RESULT, sampleInstance?.systemFileName)
        }
    }
    def downloadDocumentFile = {

        def sampleInstance = Sample.get(Cryptography.decryptWithDate(SystemConstant.sampleKey, params["id"]))

        if (sampleInstance) {
//            if (sampleInstance?.archiveResultFileName?.endsWith("txt")) {
            response.setContentType("application/force-download");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader("Content-Disposition", "attachment; filename=${sampleInstance?.documentFileName}");
//            }
            response.outputStream << commonService.getFileBytes(FileDirectory.SAMPLE, sampleInstance?.documentSystemFileName)
        }
    }
    def downloadTestCostingStudyFile = {
        def testType
        if (params.boolean("isSampleTest")) {
            def sampleTest = SampleTest.get(Cryptography.decryptWithDate(SystemConstant.sampleTestKey, params["id"]))
            testType = sampleTest?.testType
        } else {
            testType = TestType.get(Cryptography.decryptWithDate(SystemConstant.testTypeKey, params["id"]))
        }
        if (testType) {
//            if (testType?.userFileName?.endsWith("txt")) {
            response.setContentType("application/force-download");
            response.setHeader("Content-Transfer-Encoding", "binary");
            response.setHeader("Content-Disposition", "attachment; filename=${testType?.costingStudyUserFileName}");
//            }
            response.outputStream << commonService.getFileBytes(FileDirectory.COSTING, testType?.costingStudySystemFileName)
        }
    }

    def downloadSampleUploadFile = {
        def sampleTest = SampleTest.get(Cryptography.decryptWithDate(SystemConstant.sampleTestKey, params["id"]))
//        if (sampleTest?.userFileName?.endsWith("txt")) {
        response.setContentType("application/force-download");
        response.setHeader("Content-Transfer-Encoding", "binary");
        response.setHeader("Content-Disposition", "attachment; filename=${sampleTest?.userFileName}");
//        }
        response.outputStream << commonService.getFileBytes(FileDirectory.SAMPLE, sampleTest?.systemFileName)
    }


    def doctorsSamples = {
        [DOMAIN_COLUMNS: sampleService.DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def doctorsSamplesReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def result = reportService.searchSamples(params)

//      if(((params.periodType).toString()).equals('-Q')){//quarters
//          result.each { SampleView sampleView ->
//              if(sampleView.period.contains('-1')) {
//                  sampleView.period += "\n(01/01 - 31/03)"
//              }
//              else if(sampleView.period.contains('-2')) {
//                  sampleView.period += "\n(01/04 - 30/06)"
//              }
//              else if(sampleView.period.contains('-3')) {
//                  sampleView.period += "\n(01/07 - 30/09)"
//              }
//              else if(sampleView.period.contains('-4')) {
//                  sampleView.period += "\n(01/10 - 31/12)"
//              }
//          }
//      }

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def result2 = []
            def result3 = []
            def columnWidth = []
            result.eachWithIndex { row, index ->
                labels.put(row, "'${row.period}'")
                fields.add("'${row.period}'")
            }
            result.clear()
//             to remove all duplicate value from fields array
            fields = fields.unique().sort()


            result3 = reportService.searchDoctorSamplesExcel(params,fields)
            fields.add(0,"doctor_name")
            fields.add(1,"Branch")
            fields.add("Total")

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result3.toList(), fields, labels, formatters, parameters)
        } else {

            generateReport("doctorSamples", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate,
                               BRANCH_NAME: MedicalCenter.findAllByIdInList(params.list("branchId")*.toLong())?.arName?.toString()?.replace('[','').replace(']','')],
                    true, null)
        }

    }


    def institutesSamples = {
        [DOMAIN_COLUMNS: sampleService.DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def institutesSamplesReport = {

        params.periodType = params.periodType ?: '-Q'
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def  result = reportService.searchInstitutesSamples(params)

        if (((params.periodType).toString()).equals('-Q')) {//quarters
//           result.each { SampleView sampleView ->
//             if(sampleView.period.contains('-1')) {
//                 sampleView.period += "\n(01/01 - 31/03)"
//             }
//              else if(sampleView.period.contains('-2')) {
//                 sampleView.period += "\n(01/04 - 30/06)"
//             }
//              else if(sampleView.period.contains('-3')) {
//                 sampleView.period += "\n(01/07 - 30/09)"
//             }
//             else if(sampleView.period.contains('-4')) {
//                 sampleView.period += "\n(01/10 - 31/12)"
//             }
//           }
        }
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def result2 = []
            def result3 = []
            def columnWidth = []
            result.eachWithIndex { row, index ->
                labels.put(row, "'${row.period}'")
                fields.add("'${row.period}'")
            }
            result.clear()
//             to remove all duplicate value from fields array
            fields = fields.unique().sort()

            fields.add(0, "institute_Name")

            result3 = reportService.searchInstitutesSamplesExcel(params, fields)
            fields.add(1,"branch")
            fields.add("Total")

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result3.toList(), fields, labels, formatters, parameters)
        } else {
            generateReport("instituteSamples", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, BRANCH_NAME: MedicalCenter.findAllByIdInList(params.list("branchId")*.toLong())?.arName?.toString()?.replace('[','').replace(']','')], true, null)
        }


    }

    def testsSamples = {
        [DOMAIN_COLUMNS: sampleTestService.DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    

    def testsSamplesReport = {
        params.periodType = params.periodType ?: '-Q'
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def result = reportService.searchTestsSamples(params)

        if (params.boolean("isExcel")) {
            def format = "excel"

            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def result3 = []
            def columnWidth = []
            result.eachWithIndex { row, index ->
                labels.put(row, "'${row.period}'")
                fields.add("'${row.period}'")
            }
            result.clear()
//             to remove all duplicate value from fields array
            if(fields?.size()>0)
            assert fields?.unique()?.sort()
            fields.add(0, "Test_Name")

            result3 = reportService.searchTestsSamplesExcel(params, fields)
            fields.add(1, "Branch")
            fields.add("Total")

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result3.toList(), fields, labels, formatters, parameters)
        }
        else {
            generateReport("sampleTest", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate , DOCTOR_NAME : params.doctorName?:""], true, null)
        }
    }


    def branchTestSamples = {
        [DOMAIN_COLUMNS: sampleTestService.DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def branchTestsSamplesReportHTML = {
        params.periodType = params.periodType ?: '-Q'
        def result = reportService.searchBranchTestsSamples(params)
        if (((params.periodType).toString()).equals('-Q')) {//quarters
            result.each { SampleView sampleView ->
                if (sampleView.period.contains('-1')) {
                    sampleView.period += " (01/01 - 31/03) "
                } else if (sampleView.period.contains('-2')) {
                    sampleView.period += " (01/04 - 30/06) "
                } else if (sampleView.period.contains('-3')) {
                    sampleView.period += " (01/07 - 30/09) "
                } else if (sampleView.period.contains('-4')) {
                    sampleView.period += " (01/10 - 31/12) "
                }

            }
        }
        render result as JSON

    }

    def branchTestsSamplesReport = {
        params.periodType = params.periodType ?: '-Q'
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def result = reportService.searchBranchTestsSamples(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            ArrayList<String> fields = []
            Map labels = [:]
            def result2 = []
            def result3 = []
            def columnWidth = []
            result.eachWithIndex { row, index ->
                labels.put(row, "'${row.period}'")
                fields.add("'${row.period}'")
            }
            result.clear()
//             to remove all duplicate value from fields array
            assert fields.unique().sort()
            fields.add(0, "branch_Name")
            result3 = reportService.searchBranchTestsSamplesExcel(params, fields)
            fields.add("Total")

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result3.toList(), fields, labels, formatters, parameters)
        } else {
            generateReport("branchSampleTest", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
        }
    }

    def branchSamples = {
        [DOMAIN_COLUMNS: sampleService.DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def branchSamplesReport = {
        params.periodType = params.periodType ?: '-Q'
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def organizationName
        if (params.long("institutes.id"))
            organizationName = Institutes.get(params.long("institutes.id"))?.arName
        def result = reportService.searchBranchSamples(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'claim.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def result2 = []
            def result3 = []
            def columnWidth = []
            result.eachWithIndex { row, index ->
                labels.put(row, "'${row.period}'")
                fields.add("'${row.period}'")
            }
            result.clear()
//             to remove all duplicate value from fields array
            assert fields.unique().sort()
            fields.add(0, "branch_Name")
            result3 = reportService.searchBranchSamplesExcel(params, fields)
            fields.add("Total")

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result3.toList(), fields, labels, formatters, parameters)
        } else {
            generateReport("branchSample", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, INSTITUTE_NAME: organizationName], true, null)
        }
    }

    /// for doctor super approval
    def doctorSupervisorPercentage = {
        [DOMAIN_COLUMNS: reportService.DOCTOR_SUPERVISOR_VIEW_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def filterDoctorSupervisorPercentage = {
        params.max = params.parseMax()
        params.offset = params.parseOffset()
//        params["fromDate"]=params["fromDate"]?:reportService.getFirstDateOfMonth(true)
//        params["toDate"]=params["toDate"]?:reportService.getLastDateOfMonth(true)
        PagedResultList<DoctorSupervisorView> results = reportService.doctorSupervisorPercentageSearch(params)
        def dataToRender = commonService.generateDataToRender(reportService.DOCTOR_SUPERVISOR_VIEW_COLUMNS, results, params, true)
        render dataToRender as JSON


    }

    def doctorSupervisorPercentageReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def result = reportService.doctorSupervisorPercentageReport(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'doctor.supervisor.report.monthly'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "sampleCode")
            fields.add(1, "arabicName")
            fields.add(2, "sampleDate")
            fields.add(3, "name")
            fields.add(4, "price")
            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }

            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }

            Map formatters = [sampleDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result?.testList, fields, labels, formatters, parameters)


        } else {
            generateReport("doctorSupervisorPercentage", result, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, REPORT_NAME: g.message(code: 'doctor.supervisor.report.monthly')], true, null)
        }
    }

    def innerTransfer = {

        [fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }
    def innerTransferReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        def result = reportService.innerTransferReport(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'innerTransfer.report', default: 'Transfer sample test between branches'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "branch")
            fields.add(1, "medicalCenterCode")
            fields.add(2, "tests")
            fields.add(3, "charge")
            fields.add(4, "loss")
            fields.add(5, "tests2")
            fields.add(6, "charge2")
            fields.add(7, "gain")
            fields.add(8, "balance")
            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result?.transferList, fields, labels, null, parameters)


        } else {
            generateReport("innerTransfer", result, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, REPORT_NAME: g.message(code: 'report.inner.transfer.name')], true, null)
        }

    }

    def patientUserReport = {
        def patientInstance = new Patient()
        if (params["id"]) {
            patientInstance = Patient.get(Cryptography.decryptWithDate(SystemConstant.patientKey, params["id"]))
        }
        generateReport("patientPortalAccount", patientInstance, false, [REPORT_NAME: g.message(code: 'patient.portal.account.information.label')], true, null)

    }

    def transferredTests = {

        [DOMAIN_COLUMNS: reportService.TRANSFERRED_TESTS_VIEW_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]


    }
    def transferredTestsFilter = {

        params.max = params.parseMax()
        params.offset = params.parseOffset()
        params.isPagination = Boolean.TRUE

        PagedResultList<TransferredTestsView> results = reportService.transferredTestsViewSearch(params)
        def dataToRender = commonService.generateDataToRender(reportService.TRANSFERRED_TESTS_VIEW_COLUMNS, results, params, true)
        render dataToRender as JSON

    }
    def transferredTestsReport = {

        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

        def result = reportService.transferredTestsSearch(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'transferredTests.report', default: 'Transferred Tests to Branch'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "seq")
            fields.add(1, "sampleCode")
            fields.add(2, "arName")
            fields.add(3, "patientAge")
            fields.add(4, "gender")
            fields.add(5, "testTypeName")

            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }
            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result?.transferTestsList, fields, labels, null, parameters)

        } else {
            generateReport("transferredTests", result, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate,FROM_BRANCH: MedicalCenter.get(params.long("fromBranchId"))?.enName ?: '-', TO_BRANCH: MedicalCenter.get(params.long("toBranchId"))?.enName ?: '-', REPORT_NAME: g.message(code: 'transferredTests.report', default: 'Transferred Tests to Branch')], true, null)
        }
    }


    def lateTestReport = {

        [DOMAIN_COLUMNS: sampleTestService.DOMAIN_COLUMNS_LATE_VIEW ,fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def searchTATReport = {
        params.isReportSearch = true
        if (!SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN,ROLE_SUPER_ADMIN")) {
            params.branchId = session.currentBranchId
        }
        PagedResultList<SampleTestLateView> results = sampleTestService.searchLateTest(params)
        def dataToRender = commonService.generateDataToRender(sampleTestService.DOMAIN_COLUMNS_LATE_VIEW, results, params)
        render dataToRender as JSON

    }

    def getLateTestsReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        params.isReport = true
        params.isReportSearch = true
        if (!SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN,ROLE_SUPER_ADMIN")) {
            params.branchId = session.currentBranchId
        }
        def result = sampleTestService.searchLateTest(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'Late.sample.test.report', default: 'Late tests'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "serial")
            fields.add(1, "sampleCode")
            fields.add(2, "patientName")
            fields.add(3, "medicalCenterCode")
            fields.add(4, "sampleDate")
            fields.add(5, "abbreviation")
            fields.add(6, "turnAroundTime")
            fields.add(7, "unit")
            fields.add(8, "consumedTime")
            fields.add(9, "consumedUnit")


            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }

            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]


            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, formatters, parameters)
        } else {
            LinkedHashMap resultMap =[:]
            resultMap.resultList = result
            generateReport("lateSampleTest", resultMap, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, BRANCH_NAME:MedicalCenter.get(params.branchId)?.arName,  REPORT_NAME: g.message(code: 'report.late.sampleTest.name')], true, null)
        }
    }

    def lateSampleReport = {

        [DOMAIN_COLUMNS: sampleTestService.DOMAIN_COLUMNS_LATE_VIEW ,fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def lateSampleFilter = {
        params.isReportSearch = true
        if (!SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN,ROLE_SUPER_ADMIN")) {
            params.branchId = session.currentBranchId
        }
        PagedResultList<SampleTestLateView> results = sampleService.searchLateSample(params)
        def dataToRender = commonService.generateDataToRender(sampleTestService.DOMAIN_COLUMNS_LATE_VIEW, results, params)
        render dataToRender as JSON

    }

    def getLateSampleReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        params.isReport = true
        params.isReportSearch = true
        if (!SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN,ROLE_SUPER_ADMIN")) {
            params.branchId = session.currentBranchId
        }
        def result = sampleService.searchLateSample(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'Late.sample.test.report', default: 'Late tests'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "serial")
            fields.add(1, "sampleCode")
            fields.add(2, "patientName")
            fields.add(3, "medicalCenterCode")
            fields.add(4, "sampleDate")
            fields.add(5, "abbreviation")
            fields.add(6, "turnAroundTime")
            fields.add(7, "unit")
            fields.add(8, "consumedTime")
            fields.add(9, "consumedUnit")


            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }

            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [sampleDate: dateFormat]


            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, formatters, parameters)
        } else {
            LinkedHashMap resultMap =[:]
            resultMap.resultList = result
            generateReport("lateSample", resultMap, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, BRANCH_NAME:MedicalCenter.get(params.branchId)?.arName,  REPORT_NAME: g.message(code: 'report.late.sample.name')], true, null)
        }
    }



    def userLogFileList={
          [DOMAIN_COLUMNS:userLogFileService.DOMAIN_COLUMNS,fromDate: reportService.getFirstDateOfMonth(false) , toDate: reportService.getLastDateOfMonth(false)]
    }
    def userLogFileFilter={
        params.fromDate= params.fromDate?:reportService.getFirstDateOfMonth(true)
        params.toDate =params.toDate?:reportService.getLastDateOfMonth(true)
        PagedResultList<UserLogFile> results = userLogFileService.search(params)
        def dataToRender = commonService.generateDataToRender(userLogFileService.DOMAIN_COLUMNS, results, params,true)
        render dataToRender as JSON
     }

 def userLogFileReport={
     params["medicalCenterId"]=session.medicalCenterId
     params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
     params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

     def result = reportService.searchUserLogFile(params)

     if (params.boolean("isExcel")) {
         def format = "excel"
         response.contentType = grailsApplication.config.grails.mime.types[format]
         response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'Late.userLogFile.report', default: 'User log file'))?.toString()?.replaceAll(" ", "_")}.xls")
         List fields = []
         Map labels = [:]
         def columnWidth = []
         fields.add(0, "serial")
         fields.add(1, "userName")
         fields.add(2, "dateCreated")
         fields.add(3, "actionType")
         fields.add(4, "ipAddress")
         fields.add(5, "hostName")
         fields?.each { it1 ->
             labels.put(it1, "'${it1}'")
         }
         Map parameters = ["column.widths": columnWidth]
         exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
     } else {
         LinkedHashMap resultMap =[:]
         resultMap.resultList = result
         generateReport("userLogFile", resultMap, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate ,  REPORT_NAME: g.message(code: 'report.userLogFile.name')], true, null)
     }
    }


    def campaignList={

        def campaignList = Campaign.findAllByMedicalCenterId(session.medicalCenterId)

        [campaignList:campaignList,DOMAIN_COLUMNS:reportService.CAMPAIGN_COLUMNS , fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]
    }

    def campaignFilter={
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)
        params.medicalCenterId=session.medicalCenterId
       def results = reportService.getCampaignList(params)
        List<CampaignView> resultList = []
        results?.each {
            CampaignView campaignView = new CampaignView()
            campaignView.branchId = it["branchId"] as Long
            campaignView.medicalCenterCode = it["medicalCenterCode"]
            campaignView.total = it["Total"]as Long
            resultList << campaignView
            campaignView = null
        }

        def dataToRender = commonService.generateDataToRender(reportService.CAMPAIGN_COLUMNS, new PagedResultList(resultList, resultList.size()), params)
        render dataToRender as JSON

    }

    def campaignReport={

        params["medicalCenterId"]=session.medicalCenterId
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

//        def result = reportService.getCampaignList(params)
        def result = null

        if (params.boolean("isExcel")) {
             result = reportService.getCampaignList(params)

            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'campaign.report', default: 'campaign'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
//            fields.add(0, "serial")
            fields.add(0, "medicalCenterCode")
            fields.add(1, "total")

            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }
            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
        }else if (params.boolean("isCampaignExcel")) {
            result = reportService.getCampaignList1(params)

            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'campaign.report', default: 'campaign'))?.toString()?.replaceAll(" ", "_")}.xls")

            List fields = []
            Map labels = [:]
            def columnWidth = []

            // Add medical center code field
            fields.add("medicalCenterCode")
            labels.put("medicalCenterCode", "Medical Center Code")

            // Sort and iterate through result to gather dynamic code columns
            def codeColumns = result.collect { it.code }.unique() // Get unique codes

            codeColumns.each { code ->
                String fieldName = "code_$code".replaceAll(" ", "_") // Create a field name
                fields.add(fieldName)
                labels.put(fieldName, "${code}") // Use the original code as the label
                columnWidth.add(35) // Set a default width for each code column
            }

            // Prepare a new result list with the correct structure for Excel
            List<Map<String, Object>> formattedResult = []

            result.each { row ->
                def formattedRow = [:]
                formattedRow.medicalCenterCode = row.medicalCenterCode

                // Fill in code values
                codeColumns.each { code ->
                    formattedRow["code_$code".replaceAll(" ", "_")] = row.code == code ? row.Total : 0 // Assign total or 0
                }

                formattedResult.add(formattedRow)
            }

            // Prepare parameters for export
            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, formattedResult, fields, labels, null, parameters)
        }
//         else if (params.boolean("isCampaignExcel")) {
//            result = reportService.getCampaignList1(params)
//
//            def format = "excel"
//            response.contentType = grailsApplication.config.grails.mime.types[format]
//            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'campaign.report', default: 'campaign'))?.toString()?.replaceAll(" ", "_")}.xls")
//            List fields = []
//            Map labels = [:]
//            def columnWidth = []
////            fields.add(0, "serial")
//            fields.add(0, "medicalCenterCode")
//            fields.add(1, "code")
//            fields.add(2, "total")
//
//            fields?.each { it1 ->
//                labels.put(it1, "'${it1}'")
//            }
//            Map parameters = ["column.widths": columnWidth]
//            exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
//        }
        else {

            result = reportService.getCampaignList(params)
            LinkedHashMap resultMap =[:]
            resultMap.resultList = result
            generateReport("campaign", resultMap, false, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, CAMPAIGN_NAME:Campaign.get(params.long("campaign.id"))?.code, REPORT_NAME: g.message(code: 'report.campaign.name')], true, null)
        }

    }

    def doctorUserReport={
        def doctorInstance = new Doctor()
        if (params["id"]) {
            doctorInstance = Doctor.get(Cryptography.decryptWithDate(SystemConstant.doctorKey, params["id"]))
        }
        generateReport("doctorPortalAccount", doctorInstance, false, [REPORT_NAME: g.message(code: 'doctor.portal.account.information.label')], true, null)

    }

    def instituteUserReport={
        def instituteInstance = new Institutes()
        if (params["id"]) {
            instituteInstance = Institutes.get(Cryptography.decryptWithDate(SystemConstant.institutesKey, params["id"]))
        }
        generateReport("institutePortalAccount", instituteInstance, false, [REPORT_NAME: g.message(code: 'institute.portal.account.information.label')], true, null)

    }

    def priceList={

        [DOMAIN_COLUMNS: reportService.PRICE_LIST_COLUMNS]

    }

    def priceListFilter={
        params["medicalCenterId"]=session.medicalCenterId
        def results =  reportService.searchPriceList(params)
        def dataToRender = commonService.generateDataToRender(reportService.PRICE_LIST_COLUMNS, results, params)
        render dataToRender as JSON
    }
    def priceListReport={

        params["medicalCenterId"]=session.medicalCenterId
        params.put("isReport",true)
        def result = reportService.searchPriceList(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'Late.userLogFile.report', default: 'User log file'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "serial")
            fields.add(1, "testName")
            fields.add(2, "chargePrice")
            fields.add(3, "turnAroundTime")
            fields.add(4, "unit")
            fields.add(5, "labSection")
            fields.add(6, "sampleType")
            fields.add(7, "sampleVolume")
            fields.add(8, "transportCondition")
            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }
            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
        } else {
            LinkedHashMap resultMap =[:]
            resultMap.result = result?.toList()
            generateReport("priceList", resultMap, false, [ REPORT_NAME: g.message(code: 'report.priceList.name')], true, null)
        }




    }


    def patientInvoice ={
        [DOMAIN_COLUMNS: reportService.PATIENT_INVOICE_VIEW_DOMAIN_COLUMNS ]

    }

    def patientInvoiceFilter ={
        params["medicalCenterId"]=session.medicalCenterId
        if (!SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN,ROLE_SUPER_ADMIN")) {
            params.branchId = session.currentBranchId
        }
        def results =  reportService.searchPatientInvoiceList(params)
        def dataToRender = commonService.generateDataToRender(reportService.PATIENT_INVOICE_VIEW_DOMAIN_COLUMNS, results, params)
        render dataToRender as JSON

    }

    def patientInvoiceReport ={

        params["medicalCenterId"]=session.medicalCenterId
        params.put("isReport",true)
        def result = reportService.searchPatientInvoiceList(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'Late.userLogFile.report', default: 'User log file'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "serail")
            fields.add(1, "sampleCode")
            fields.add(2, "transactionType")
            fields.add(3, "transactionDate")
            fields.add(4, "transactionAmount")

            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }
            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
        } else {
            LinkedHashMap resultMap =[:]
            resultMap.result = result?.toList()
            generateReport("patientFinStat", resultMap, false, [ REPORT_NAME: g.message(code: 'report.patientInvoice.name'),PATIENT_NAME: params.patientName], true, null)
        }

    }

  def insNotAllowedTest  ={

      [DOMAIN_COLUMNS: reportService.INS_DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

  }

     def insNotAllowedTestReportHTML = {

     def result = reportService.searchInsNotAllowedTest(params)

     def dataToRender = commonService.generateDataToRender(reportService.INS_DOMAIN_COLUMNS, result, params)
     render dataToRender as JSON

    }

    def insNotAllowedTestReport = {

             params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'
             params.isReport= true
        def result = reportService.searchInsNotAllowedTest(params)
         if (params.boolean("isExcel")) {
             def format = "excel"
             response.contentType = grailsApplication.config.grails.mime.types[format]
             response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'insNotAllowedTest.report.labelEnglish', default: 'institute not allowed test'))?.toString()?.replaceAll(" ", "_")}.xls")
             List fields = []
             Map labels = [:]
             def columnWidth = []
             fields.add(0, "id")
             fields.add(1, "instituteName")
             fields.add(2, "testName")

             fields?.each { it1 ->
                 labels.put(it1, g.message(code:"notAllowedTestRecord.${it1}.label" ))
             }
             Map parameters = ["column.widths": columnWidth]
             exportService.export(format, response.outputStream, result, fields, labels, null, parameters)
        } else {

              generateReport("instituteNotAllowedTest",result?.list, true, [REPORT_NAME:g.message(code: 'insNotAllowedTest.report.label',default: 'insNotAllowedTest.report.label')], true, null)

         }
    }



     def  proceedNotAllowedTests   ={

      [DOMAIN_COLUMNS: reportService.NOT_ALLOWED_TEST_DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

       }

    def proceedNotAllowedTestsHTML = {

        def result = reportService.searchProceedNotAllowedTests(params)

        def dataToRender = commonService.generateDataToRender(reportService.NOT_ALLOWED_TEST_DOMAIN_COLUMNS, result, params)
        render dataToRender as JSON

        }

    def proceedNotAllowedTestsReport = {

        params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'
        params.isReport= true
        def result = reportService.searchProceedNotAllowedTests(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'proceedNotAllowedTests.report.labelEnglish', default: 'proceedNotAllowedTests'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "id")
            fields.add(1, "instituteName")
            fields.add(2, "sampleCode")
            fields.add(3, "testName")
            fields.add(4, "reasonInsertionDate")
            fields.add(5, "reason")

            fields?.each { it1 ->
                labels.put(it1, g.message(code:"notAllowedTestRecord.${it1}.label" ))
            }
            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [reasonInsertionDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, formatters , parameters)
        } else {

            generateReport("proceedNotAllowedTests",result?.list, true, [REPORT_NAME:g.message(code: 'proceedNotAllowedTests.report.label',default: 'proceedNotAllowedTests.report.label')], true, null)

        }
    }


    def  auditTestType ={

        [DOMAIN_COLUMNS: reportService.AUDIT_DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def auditTestTypeHTML = {
        params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'

        def result = reportService.searchAuditTestType(params)

        def dataToRender = commonService.generateDataToRender(reportService.AUDIT_DOMAIN_COLUMNS, result, params)
        render dataToRender as JSON

    }

    def auditTestTypeReport = {

        params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'
        params.isReport= true
        def result = reportService.searchAuditTestType(params)

        generateReport("auditTestType",result?.list, true, [TEST_TYPE:params.testTypeName, FROM_DATE: params.fromDate, TO_DATE: params.toDate , REPORT_NAME:g.message(code: 'auditTestTypeReport.report.label',default: 'auditTestTypeReport.report.label')], true, null)

    }

    def  invoiceCancelRequest ={

        [DOMAIN_COLUMNS: reportService.INVOICE_CANCEL_REQUEST_DOMAIN_COLUMNS, fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }

    def invoiceCancelRequestHTML = {
        params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'

        def result = reportService.searchInvoiceCancelRequest(params)

        def dataToRender = commonService.generateDataToRender(reportService.INVOICE_CANCEL_REQUEST_DOMAIN_COLUMNS, result, params)
        render dataToRender as JSON

    }

    def invoiceCancelRequestReport = {

        params.lang =RequestContextUtils.getLocale(request).language.toString().startsWith('ar') ? 'ar' : 'en'
        params.isReport= true
        def result = reportService.searchInvoiceCancelRequest(params)

        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'proceedNotAllowedTests.report.labelEnglish', default: 'proceedNotAllowedTests'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "serialNumber")
            fields.add(1, "sampleCode")
            fields.add(2, "reason")
            fields.add(3, "status")
            fields.add(4, "fullName")
            fields.add(5, "requestBy")
            fields.add(6, "requestDate")

            fields?.each { it1 ->
                labels.put(it1, g.message(code:"invoiceCancelRequestView.${it1}.label" ))
            }
            def dateFormat = { domain, value ->
                if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [requestDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result, fields, labels, formatters , parameters)
        }
        else
        {
            String searchSettings = ""

            if(params.fromDate){
                searchSettings += message(code: 'default.fromDate.label')+" : "+params["fromDate"]+", "
            }
            if(params.toDate){
                searchSettings += message(code: 'default.toDate.label')+" : "+params["toDate"]+", "
            }

            if(params.serialNumber){
                searchSettings += message(code: 'invoiceCancelRequestView.serialNumber.label')+" : "+params["serialNumber"]+", "
            }
            if(params.sampleCode){
                searchSettings += message(code: 'invoiceCancelRequestView.sampleCode.label')+" : "+params["sampleCode"]+", "
            }
            if(params.status){
                searchSettings += message(code: 'invoiceCancelRequestView.status.label')+" : "+message(code: 'ValueStatus.'+params.status)+", "
            }
            if(params.long("reason.id")){
                searchSettings += message(code: 'invoiceCancelRequestView.reason.label')+" : "+InvoiceCancelReason.get(params.long("reason.id"))?.name+", "
            }
             if(searchSettings.size()>0){
                 searchSettings = searchSettings.substring(0,(searchSettings.size()-2) )
             }


            generateReport("invoiceCancelRequest",result?.list, true, [ SEARCH_SETTING:searchSettings , REPORT_NAME:g.message(code: 'invoiceCancelRequest.report.label',default: 'invoiceCancelRequest')], true, null)

        }

    }


    def howYouKnowAboutUs = {

        [fromDate: reportService.getFirstDateOfMonth(), toDate: reportService.getLastDateOfMonth()]

    }
    def howYouKnowAboutUsReport = {
        params["fromDate"] = params["fromDate"] ?: reportService.getFirstDateOfMonth(true)
        params["toDate"] = params["toDate"] ?: reportService.getLastDateOfMonth(true)

        def result = reportService.howYouKnowAboutUsReport(params)
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'howYouKnowAboutUs.report', default: 'howYouKnowAboutUs.report'))?.toString()?.replaceAll(" ", "_")}.xls")
            List fields = []
            Map labels = [:]
            def columnWidth = []
            fields.add(0, "answer")
            fields.add(1, "count")

            fields?.each { it1 ->
                labels.put(it1, "'${it1}'")
            }

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result , fields, labels, null, parameters)


        } else {
            generateReport("howYouKnowAboutUs", result, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate, REPORT_NAME: g.message(code: 'howYouKnowAboutUs.report')], true, null)
        }

    }



    def pointTransaction = {

        [DOMAIN_COLUMNS: PointTransactionService.DOMAIN_COLUMNS_REPORT]

    }

    def pointTransactionReport = {

        def result = reportService.pointTransactionReportData(params)

        String reportName = message(code: 'pointTransactionReport.label')
        if (params.boolean("isExcel")) {
            def format = "excel"
            response.contentType = grailsApplication.config.grails.mime.types[format]
            response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'samplesPerHour.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")
            ArrayList<String> fields = [ 'transactionDate' ,'sampleCode' , 'invoiceNumber' , 'transactionType' , 'invoiceAmount'  ,'pointValue' ]
            Map labels = [:]

            def columnWidth = []
            fields.each {
                labels.put(it, message(code:  it+".label"))

            }


            def dateFormat = { domain, value ->
                if (value instanceof Date) {
                    return new java.text.SimpleDateFormat("dd/MM/yyyy").format(value)
                }
                return value
            }
            Map formatters = [transactionDate: dateFormat]

            Map parameters = ["column.widths": columnWidth]
            exportService.export(format, response.outputStream, result.finalList.toList(), fields, labels, formatters, parameters)
        } else {
            generateReport("pointTransaction", result.finalList, true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate , PATIENT_NAME:params.patientName , REPORT_NAME:reportName , TOTAL_POINTS:result.totalPoints ], true, null)
        }

    }

    def journalVoucherReport = {

        params.put('max', Integer.MAX_VALUE)
        params.fromDate = params.fromDate ? params.fromDate : reportService.currentDate(true)
        params.toDate = params.toDate ? params.toDate : reportService.currentDate(true)
        if(params.boolean("isExcel") != null && params.boolean("isExcel") == Boolean.FALSE){
            generateReport("journalVoucher",reportService.searchJournalVoucher(params),true, [FROM_DATE: params.fromDate, TO_DATE: params.toDate], true, null)
         }
    }







    def postJVoucher = {
        params.isExcel = false
        Map json=[:]
        println " ---------------------------------------------------------- "
        println(" Search Journal Voucher Data , Start Date :  " + new java.util.Date())
        List<JournalVoucherView> journalVoucherViewList = reportService.searchJournalVoucher(params)
        println(" Search Journal Voucher Data , End Date :  " + new java.util.Date())
        println " ---------------------------------------------------------- "
        println(" Save Journal Voucher Data on saleem , Start Date :  " +new java.util.Date())
        Map map = saveJournalVoucherData(params,journalVoucherViewList)
        println(" Save Journal Voucher Data on saleem , End Date :  " + new java.util.Date())
        println " ---------------------------------------------------------- "
       if(map.success){

           Map <String,Object> postJV = reportService.postJV(params,map)
           if(postJV != null){
               def successMessage = "${message(code: 'default.postJV.success.message', args: [message(code: 'journalVoucher.label', default: 'Journal Voucher')])}"
               def failMessage = "${message(code: 'default.postJV.fail.message', args: [message(code: 'journalVoucher.label', default: 'Journal Voucher')])}"
               if (postJV.get("status") == 200) {
                   String pJvMSG = (postJV.get("rows"))?.getAt("message")
                   if(pJvMSG != null){
                       json.success = false
                       json.message =  flashy.error(failMessage + " " + pJvMSG)
                       reportService.updateJournalVoucherStatusToFailed(map)

                   }
                   else{
                       json.success = true
                       json.message = flashy.success(successMessage)
                       params.transactionId = postJV.get("transactionId")
                       params.comment = postJV.get("comment")
                       params.code = (postJV.get("rows")).getAt("code")
                       reportService.updateJournalVoucherStatusToSaved(map,params)
                   }
               }
               else{
                   json.success = false
                   json.message = flashy.error(failMessage + " " + postJV?.get("message"))
                   reportService.updateJournalVoucherStatusToFailed(map)
               }
           }
           else{
               def failMessage = "${message(code: 'bisan.login.fail', args: [message(code: 'bisan.login.fail', default: 'Failed connection with bisan !')])}"
               json.success = false
               json.message = flashy.error(failMessage)
               reportService.updateJournalVoucherStatusToFailed(map)
           }
       }else{
           def failMessage = "${message(code: 'journalVoucher.save.error', args: [message(code: 'journalVoucher.save.error', default: 'Save journal voucher on saleem failed !')])}"
           json.success = false
           json.message = flashy.error(failMessage)
       }
        render text: (json as JSON), contentType: "application/json"
    }

    Map saveJournalVoucherData(GrailsParameterMap params,List<JournalVoucherView> journalVoucherViewList){
    Map result = [:]
    result.params = []
    Random random = new Random()
    Long nextTransactionId = null
    JournalVoucher journalVoucher = null
    StatelessSession session = sessionFactory.openStatelessSession()
    JournalVoucher.withTransaction { status ->
        journalVoucher = new JournalVoucher()
        nextTransactionId = random.nextInt()
        journalVoucher.transactionId = nextTransactionId
        journalVoucher.fromDate = DateUtils.parseDate(params.fromDate as String)
        journalVoucher.toDate = DateUtils.parseDate(params.toDate as String)
        MedicalCenter medicalCenter = MedicalCenter.findById(params.long("branchId")?params.long("branchId"):params.long("currentBranchId"))
        journalVoucher.currentBranch = medicalCenter
        journalVoucher.branchCode = medicalCenter?.branchAccountNo
        journalVoucher.dateCreated = DateUtils.trim(new java.util.Date())
        journalVoucher.createdBy = springSecurityService.principal?.username
        journalVoucher.isActive = true
        journalVoucher.journalVoucherStatus = JournalVoucherStatus.PENDING
        journalVoucher.save(flush: true, failOnError: true)
        Transaction tx = session.beginTransaction()
        params.session = session
        params.journalVoucher = journalVoucher
        if(journalVoucherViewList){
            result.foundData = true
            result = reportService.readRawData(params,journalVoucherViewList)
        }else{
            result.success = false
            result.foundData = false
        }
        if (result.success) {
            tx.commit()

        } else {
            result.success = false
            return result
//            tx.rollback()
//            throw new Exception()
        }

        session.close()
    }
        result.transactionId = nextTransactionId
        result.journalVoucherID = journalVoucher?.id
        return  result
}


//    boolean foundSameCodeOfJournalVoucher(String code){
//        JournalVoucher journalVoucher = JournalVoucher.findByCode(code)
//        if(journalVoucher){
//            return true
//        }else{
//            return false
//        }
//    }


    def checkDuplicateRecord={
        Map json = [:]
       boolean found =  reportService.findDuplicateJV(params)
        if(found){
            json.foundDuplicate = true
        }else{
            json.foundDuplicate = false
        }

        render text: (json as JSON), contentType: "application/json"
    }

    def discountPercentage = {

        if (!params.viewReport) {
            def result = reportService.getDiscountPercentage(params)

            if (params.boolean("isExcel")) {
                response.contentType = grailsApplication.config.grails.mime.types['excel']
                response.setHeader("Content-disposition", "attachment; filename=${(message(code: 'discountPercentage.report.label'))?.toString()?.replaceAll(" ", "_")}.xls")

                def workbook = new HSSFWorkbook()
                def sheet = workbook.createSheet("Discount Percentage")

                // Create a font for headers and data
                HSSFFont font = workbook.createFont()
                font.setFontName("Calibri")

                // Create a single cell style for headers
                def headerStyle = workbook.createCellStyle()
                headerStyle.setFont(font)
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex())
                headerStyle.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND)
                headerStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER)
                setBorders(headerStyle)

                // Create a single cell style for data
                def dataStyle = workbook.createCellStyle()
                dataStyle.setFont(font)
                setBorders(dataStyle)



                // Create header row
                def fields = ['branch', 'payment_method','discount','subtotal','percentage']
                def labels = fields.collect { message(code: "discountPercentage.${it}.label", default: it) }

                def headerRow = sheet.createRow(0)
                fields.eachWithIndex { field, index ->
                    def cell = headerRow.createCell(index)
                    cell.setCellValue(labels[index])
                    cell.setCellStyle(headerStyle)
                    sheet.setColumnWidth(index, 20 * 256) // Set column width
                }

                // Populate data rows
                result.eachWithIndex { entry, rowIndex ->
                    def dataRow = sheet.createRow(rowIndex + 1) // Start from row 1
                    fields.eachWithIndex { field, index ->
                        def cell = dataRow.createCell(index)
                        cell.setCellValue(entry[field]?.toString() ?: "")
                        cell.setCellStyle(dataStyle)
                    }
                }

                // Write the output to the response's output stream
                workbook.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
            } else {
                generateReport("discountPercentage", result, true, [:], true, null, false, params.boolean('isPdfViewer'))
            }
        }
    }

    private void setBorders(def style) {
        style.setBorderBottom(HSSFCellStyle.BORDER_THIN)
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex())
        style.setBorderTop(HSSFCellStyle.BORDER_THIN)
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex())
        style.setBorderLeft(HSSFCellStyle.BORDER_THIN)
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex())
        style.setBorderRight(HSSFCellStyle.BORDER_THIN)
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex())
    }

}
