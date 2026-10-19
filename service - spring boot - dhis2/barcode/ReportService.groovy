package com.ubs.mlms.report

import org.apache.http.entity.StringEntity
import groovy.json.JsonOutput
import com.ubs.common.tools.Cryptography
import com.ubs.mlms.Financial.claims.FinanceClaims
import com.ubs.mlms.api.JournalVoucherData
import com.ubs.mlms.enums.JournalVoucherStatus
import com.ubs.mlms.lookup.PointTransactionStatus
import com.ubs.mlms.relatedParty.DoctorSupervisor
import com.ubs.mlms.relatedParty.MedicalCenter
import com.ubs.mlms.reports.AuditTestTypeView
import com.ubs.mlms.reports.ClaimView
import com.ubs.mlms.reports.DoctorSupervisorView
import com.ubs.mlms.reports.FinancialReportView
import com.ubs.mlms.reports.ForeignPaidAmountReportView
import com.ubs.mlms.reports.ForeignPaidAmountView
import com.ubs.mlms.reports.ForeignPaidByVisaOrWalletReportView
import com.ubs.mlms.reports.ForeignPaidByVisaOrWalletView
import com.ubs.mlms.reports.InnerTransferView
import com.ubs.mlms.reports.InstituteTestView
import com.ubs.mlms.reports.InvoiceCancelRequestView
import com.ubs.mlms.reports.JournalVoucherReportView
import com.ubs.mlms.reports.JournalVoucherView
import com.ubs.mlms.reports.PatientInvoiceView
import com.ubs.mlms.reports.PriceListView
import com.ubs.mlms.reports.ProceedNotAllowedTestView
import com.ubs.mlms.reports.SampleBarcodeView
import com.ubs.mlms.reports.SampleTestCompletedView
import com.ubs.mlms.reports.SampleTestDetailsView
import com.ubs.mlms.reports.SampleView
import com.ubs.mlms.reports.TransferredTestsView
import com.ubs.mlms.test.Sample
import com.ubs.mlms.test.SampleTest
import grails.orm.PagedResultList
import grails.validation.ValidationException
import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.hibernate.SessionFactory
import utils.DateUtils
import utils.SystemConstant
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter
import java.sql.Blob
import java.sql.Date
import java.text.SimpleDateFormat

class ReportService {
    static transactional = true
    def financeClaimsService
    def messageSource
    def sampleService
    def userService
    def dataSource
    /*  Generic search.
    * @param params.
    * @return a paged result List which is aware of the "totalCount" for the paged result
    */


    public static final List<String> FINANCIAL_DOMAIN_COLUMNS = [
            [sort: true, search: true, hidden: true, name: "id", type: "long", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "branchName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "sampleCode", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "transactionDate", type: "date", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "patientName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "onAccount", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "subtotal", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "discount", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "debit", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "credit", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "balance", type: "string", source: 'domain'],
    ]
    public static final List<String> CLAIM_DOMAIN_COLUMNS = [
            [sort: false, search: false, hidden: true, name: "id", type: "long", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "sampleDate", type: "date", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "patientName", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "formNumber", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "invoiceNumber", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "subtotal", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "discount", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "extraDiscount", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "receipts", type: "string", source: 'domain', messagePrefix: 'claim'],
            [sort: false, search: false, hidden: false, name: "net", type: "string", source: 'domain', messagePrefix: 'claim'],
    ]


    public static final List<String> DOCTOR_SUPERVISOR_VIEW_COLUMNS = [

            [sort: false, search: false, hidden: true, name: "id", type: "long", source: 'domain', messagePrefix: 'doctorSupervisor'],
            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain', messagePrefix: 'doctorSupervisor'],
            [sort: false, search: false, hidden: false, name: "arabicName", type: "string", source: 'domain', messagePrefix: 'doctorSupervisor'],
            [sort: false, search: false, hidden: false, name: "sampleDate", type: "date", source: 'domain', messagePrefix: 'doctorSupervisor'],
            [sort: false, search: false, hidden: false, name: "name", type: "string", source: 'domain', messagePrefix: 'doctorSupervisor'],
            [sort: false, search: false, hidden: false, name: "price", type: "string", source: 'domain', messagePrefix: 'doctorSupervisor'],
    ]


    public static final List<String> TRANSFERRED_TESTS_VIEW_COLUMNS = [

            [sort: false, search: false, hidden: true, name: "seq", type: "long", source: 'domain', messagePrefix: 'transferredTests'],
            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain', messagePrefix: 'transferredTests'],
            [sort: false, search: false, hidden: false, name: "arName", type: "string", source: 'domain', messagePrefix: 'transferredTests'],
            [sort: false, search: false, hidden: false, name: "patientAge", type: "string", source: 'domain', messagePrefix: 'transferredTests'],
            [sort: false, search: false, hidden: false, name: "gender", type: "string", source: 'domain', messagePrefix: 'transferredTests'],
            [sort: false, search: false, hidden: false, name: "testTypeName", type: "string", source: 'domain', messagePrefix: 'transferredTests'],

    ]

    public static final List<String> CAMPAIGN_COLUMNS = [

            [sort: false, search: false, hidden: true, name: "branchId", type: "long", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "medicalCenterCode", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "total", type: "string", source: 'domain'],

    ]


    public static final List<String> PRICE_LIST_COLUMNS = [

            [sort: false, search: false, hidden: false, name: "serial", type: "long", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "testName", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "chargePrice", type: "float", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "turnAroundTime", type: "long", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "unit", type: "enum", messagePrefix: "TimeUnit", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "labSection", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "sampleType", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "sampleVolume", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "transportCondition", type: "string", source: 'domain'],

    ]
    public static final List<String> PATIENT_INVOICE_VIEW_DOMAIN_COLUMNS = [

            [sort: false, search: false, hidden: false, name: "id", type: "long", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "transactionType", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "transactionAmount", type: "float", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "transactionDate", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain'],

    ]

    public static final List<String> INS_DOMAIN_COLUMNS = [

            [sort: false, search: false, hidden: true, name: "id", type: "long", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "instituteName", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "testName", type: "string", source: 'domain']

    ]


    public static final List<String> NOT_ALLOWED_TEST_DOMAIN_COLUMNS = [

            [sort: false, search: false, hidden: true, name: "id", type: "long", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "instituteName", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "reason", type: "string", source: 'domain', width: '35%'],
            [sort: false, search: false, hidden: false, name: "reasonInsertionDate", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "testName", type: "string", source: 'domain', width: '35%']

    ]

    public static final List<String> AUDIT_DOMAIN_COLUMNS = [

            [sort: false, search: false, hidden: false, name: "testName", type: "long", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "className", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "propertyName", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "oldValue", type: "string", source: 'domain', width: '35%'],
            [sort: false, search: false, hidden: false, name: "newValue", type: "string", source: 'domain', width: '35%'],
            [sort: false, search: false, hidden: false, name: "dateCreated", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "createdBy", type: "string", source: 'domain', width: '35%']

    ]


    public static getSerialNumber = { cService, rec, object, params ->
        return object.toString()
    }

    public static final List<String> INVOICE_CANCEL_REQUEST_DOMAIN_COLUMNS = [

            [sort: false, search: false, hidden: false, name: "sampleCode", type: "string", source: 'domain',],
            [sort: false, search: false, hidden: false, name: "serialNumber", type: getSerialNumber, source: 'closure',],
//            [sort: true, search: true, hidden: false, name: "status", type: "enum", messagePrefix: "ValueStatus", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "status", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "reason", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "requestBy", type: "string", source: 'domain'],
            [sort: false, search: false, hidden: false, name: "requestDate", type: "string", source: 'domain'],

    ]


    String generateCashPaidAmountForeignQueryWithoutGroup(GrailsParameterMap params) {

        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }

        //if the user not admin just allow to search for specific period
        if (params["fromDate"]) {

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {

                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)

                def today = new java.util.Date()?.plus(-financeReportDays)
                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
                if (fromSelectedDate < allowingDate) {
                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
                }
            }
        }

        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"


        String query = """
                 select 
                 round(sum(a.amount_in_ils),2)   as ILS
                 from amount_paid_foreign_currency a
                 where is_active=1
                 and a.payment_method='CASH' 
              
      """

        if (fromDate) {
            query += "  and  a.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  a.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.current_branch_id=${branchId} "
        }

        return query
    }


    String generateVisaOrWalletPaidAmountForeignQuery(GrailsParameterMap params) {

        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }


        //if the user not admin just allow to search for specific period
        if (params["fromDate"]) {

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {

                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)

                def today = new java.util.Date()?.plus(-financeReportDays)
                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
                if (fromSelectedDate < allowingDate) {
                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
                }
            }
        }

        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"


        String query = """
               select b.name as visa_wallet_company , a.card_number, a.currency_type,
 case a.currency_type
  when  'ILS' then 0
    else round(sum(a.paid_amount),2)
    end as foreign ,round(sum(a.amount_in_ils),2) as ILS
from amount_paid_foreign_currency a , visa_wallet_company b
where a.is_active = 1 and a.payment_method = 'BY_VISA_OR_E_WALLET'
      and a.visa_wallet_id = b.id
               """

        if (fromDate) {
            query += "  and  a.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  a.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += "  and  a.current_branch_id = ${branchId} "
        }

        query += """ group by b.name,a.card_number,a.currency_type """


        query += """  union all 
        

       select b.name as visa_wallet_company ,  r.card_number, 'ILS' as currency_type,
       0 as foreign,round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2) as ILS
       from
       sample s 
       , visa_wallet_company b ,receipt r ,
        financial d, financial_status fs
                        where  s.id = d.sample_id2
                        and  d.id=fs.financial_id 
                        and  fs.financial_id=r.id
                        and  r.visa_wallet_id=b.id
                        and  d.transaction_type = 'RECEIPT'
                        and  fs.payment_method = 'BY_VISA_OR_E_WALLET'   
                        and not (
      fs.transaction_status = 'CANCEL'
      and exists (
          select 1
          from financial_status fs_paid
          where fs_paid.financial_id = d.id
            and fs_paid.transaction_status = 'PAID'
            and fs_paid.payment_method = 'CASH'
            and fs_paid.financial_status_idx = (
                select max(fs_paid2.financial_status_idx)
                from financial_status fs_paid2
                where fs_paid2.financial_id = d.id
                  and fs_paid2.transaction_status = 'PAID'
                  and fs_paid2.financial_status_idx < fs.financial_status_idx
            )
      )
)
             and  s.id not in
                             (select sample_id from amount_paid_foreign_currency where is_active=1)   
        """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += "  and  d.current_branch_id = ${branchId} "
        }

        query += """ group by   b.name,  r.card_number """


        query += """ union all  
       select   b.name as visa_wallet_company ,  r.card_number, 'ILS' as currency_type,
       0 as foreign,  round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount ,0-d.transaction_amount)),2) as ILS
       from sample s , financial d, financial_status fs, visa_wallet_company b, medical_center m,
       receipt r
       where  s.id = d.sample_id2
         and  m.id = d.current_branch_id
         and  fs.financial_id = d.id
         and  r.id=d.id
         and  r.visa_wallet_id = b.id
         and  d.transaction_type = 'RECEIPT'
         and  r.payment_method='BY_VISA_OR_E_WALLET'
         and  fs.payment_method = 'BY_CREDIT_Institutes'
         and  s.id not in
            ( select sample_id from amount_paid_foreign_currency where is_active=1 
             and trunc(d.transaction_amount)= trunc(amount_in_ils)
			 and payment_method='BY_VISA_OR_E_WALLET')
            """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += "  and  d.current_branch_id=${branchId} "
        }

        query += """ group by  b.name,r.card_number """




        return query
    }

    String generateCashPaidAmountForeignQuery(GrailsParameterMap params) {

        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }

        //if the user not admin just allow to search for specific period
        if (params["fromDate"]) {

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {

                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)

                def today = new java.util.Date()?.plus(-financeReportDays)
                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
                if (fromSelectedDate < allowingDate) {
                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
                }
            }
        }

        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"


        String query = """
                 select 
                 a.currency_type, 
                 round(sum(a.paid_amount),2) as foreign ,
                 round(sum(a.amount_in_ils),2)   as ILS
                 from amount_paid_foreign_currency a
                 where is_active=1
                 and a.payment_method='CASH' 
              
      """

        if (fromDate) {
            query += "  and  a.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  a.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.current_branch_id=${branchId} "
        }
        query += """ group by a.currency_type """

        return query
    }

    String generateFinancialQuery(GrailsParameterMap params) {
        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }
        def medicalCenterId = params.long("medicalCenterId")
        def isCount = params.boolean("isCount")
        //if the user not admin just allow to search for specific period
        if (params["fromDate"]) {

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {

                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)

                def today = new java.util.Date()?.plus(-financeReportDays)
                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
                if (fromSelectedDate < allowingDate) {
                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
                }
            }
        }
        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"

        def max = params.parseMax()
        def offset = params.parseOffset()
        String query = ""
        if (isCount) {
            query += " select count(1) as countNum "
        } else {
            query += """
                    select * from (
                    select rownum as id, payment_method,print_order, medical_center_id,  branch_id, 
                     branch_name , sample_code, trunc(date_created) as date_created,
                    invoice_num, patient_id, Patient_name,on_account,
                    round(subtotal,2) as subtotal, round(discount,2) as discount,round(debit,2) as debit  ,
                    round(credit,2) as credit,round(new_credit,2) as new_credit,round(balance,2) as balance """
        }
        query += """
                        from
                        (SELECT a.id, fs.payment_method, 1 as print_order,a.medical_center_id,  f.current_branch_id branch_id, m.ar_name as branch_name , a.sample_code, fs.date_created ,
                                c.serial_number as invoice_num, a.patient_id, b.ar_name as Patient_name, '' as on_account,
                                        decode(fs.transaction_status,'PAID',fs.subtotal,-1*fs.subtotal) as subtotal,
                                          decode(fs.transaction_status,'PAID',fs.discount,-1*fs.discount) as discount ,
                                 decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit,
                                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal)  as credit,
                                           decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal)  as new_credit,
                                0 as balance
                          FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m
                          where a.patient_id=b.id
                          and a.id=c.sample_id
                          and (
      fs.payment_method = 'CASH'
      or (
           fs.transaction_status = 'CANCEL'
           and exists (
               select 1
               from financial_status fs_paid
               where fs_paid.financial_id = f.id
                 and fs_paid.transaction_status = 'PAID'
                 and fs_paid.payment_method = 'CASH'
                 and fs_paid.financial_status_idx = (
                     select max(fs_paid2.financial_status_idx)
                     from financial_status fs_paid2
                     where fs_paid2.financial_id = f.id
                       and fs_paid2.transaction_status = 'PAID'
                       and fs_paid2.financial_status_idx < fs.financial_status_idx
                 )
           )
      )
)
                          and f.current_branch_id=m.id
                          and c.id=f.id
                          and a.id=f.sample_id2
                          and f.id=fs.financial_id"""

        if (fromDate) {
            query += "  and fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        if (medicalCenterId) {
            query += " and a.medical_center_id=${medicalCenterId} "
        }
        query += """
                        union
                        SELECT a.id, fs.payment_method, 3 as print_order,a.medical_center_id,  f.current_branch_id branch_id, m.ar_name as branch_name , a.sample_code, fs.date_created ,
                                c.serial_number as invoice_num, a.patient_id, b.ar_name as Patient_name, '' as on_account,
                                        decode(fs.transaction_status,'PAID',fs.subtotal,-1*fs.subtotal) as subtotal,
                                          decode(fs.transaction_status,'PAID',fs.discount,-1*fs.discount) as discount ,
                                 decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit,
                                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal)  as credit,
                                          0 as new_credit,
                                0 as balance
                          from sample a , patient b , invoice c, financial f, financial_status fs, medical_center m
                          where a.patient_id=b.id
                          and a.id=c.sample_id
                          and fs.payment_method='BY_VISA_OR_E_WALLET'
                          and not (
      fs.transaction_status = 'CANCEL'
      and exists (
          select 1
          from financial_status fs_paid
          where fs_paid.financial_id = f.id
            and fs_paid.transaction_status = 'PAID'
            and fs_paid.payment_method = 'CASH'
            and fs_paid.financial_status_idx = (
                select max(fs_paid2.financial_status_idx)
                from financial_status fs_paid2
                where fs_paid2.financial_id = f.id
                  and fs_paid2.transaction_status = 'PAID'
                  and fs_paid2.financial_status_idx < fs.financial_status_idx
            )
      )
)
                          and f.current_branch_id=m.id
                          and c.id=f.id
                          and a.id=f.sample_id2
                          and f.id=fs.financial_id"""

        if (fromDate) {
            query += "  and fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and a.medical_center_id=${medicalCenterId} "
        }

        query += """
                UNION
                SELECT a.id, fs.payment_method, 2, a.medical_center_id,  f.current_branch_id, m.ar_name as branch_name , a.sample_code, fs.DATE_CREATED  ,
                        c.serial_number as invoice_num, a.patient_id, b.ar_name as Patient_name, s.ar_name as on_account,
                        decode(fs.transaction_status,'PAID',fs.subtotal,-1*fs.subtotal),
                        decode(fs.transaction_status,'PAID',fs.discount,-1*fs.discount),
                        decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount ),-1*(fs.subtotal-fs.discount)) as debit,
                        0 as credit,
                        0 as   new_credit,
                        decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount),-1*(fs.subtotal-fs.discount))  as balance
                  FROM sample a , patient b , invoice c, financial f, medical_center m, institutes S, financial_status fs
                  where a.patient_id=b.id
                  and a.id=c.sample_id
                  and fs.payment_method='BY_CREDIT_Institutes'
                  and f.current_branch_id=m.id
                  AND fs.institutes_id=S.id
                  and c.id=f.id and f.id=fs.financial_id"""

        if (fromDate) {
            query += "  and fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and a.medical_center_id=${medicalCenterId} "
        }
        query += """
                 union
                 SELECT a.id, fs.payment_method, 2, a.medical_center_id,  f.current_branch_id, m.ar_name as branch_name , a.sample_code, fs.DATE_CREATED ,
                        r.serial_number as invoice_num, a.patient_id, b.ar_name||decode(r.payment_method,'BY_VISA_OR_E_WALLET','VISA','') as Patient_name, s.ar_name as on_account,  0 as subtotal, 0 as discount ,
                        0 as debit,
                                decode(fs.transaction_status,'PAID',f.transaction_amount,-1*f.transaction_amount) as credit,
                                decode(r.payment_method,'BY_VISA_OR_E_WALLET', 0 , decode(fs.transaction_status,'PAID',f.transaction_amount,-1*f.transaction_amount)) as new_credit,
                                decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount)  as balance
                  FROM sample a , patient b , invoice c, receipt r, financial f, medical_center m, institutes S, financial_status fs
                  where a.patient_id=b.id
                  and a.id=c.sample_id
                  and fs.payment_method='BY_CREDIT_Institutes'
                  and f.current_branch_id=m.id
                  AND fs.institutes_id=S.id
                  and c.id=r.invoice_id
                  and r.id=f.id
                  and f.id=fs.financial_id
                  and f.transaction_amount<>0 """

        if (fromDate) {
            query += "  and fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and a.medical_center_id=${medicalCenterId} "
        }

        query += """
                 union
                 SELECT a.id, fs.payment_method, 2, a.medical_center_id,  f.current_branch_id, m.ar_name as branch_name , a.sample_code,  fs.date_created DATE_CREATED ,
                        0 as invoice_num, a.patient_id, b.ar_name||' Extra discount' as Patient_name, s.ar_name as on_account,  0 as subtotal, decode(fs.transaction_status,'PAID',f.transaction_amount,-1*f.transaction_amount) as discount ,
                         decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) as debit,
                        0 as credit,
                        0 as new_credit,
                        decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount) as balance
                  FROM sample a , patient b ,   financial f, medical_center m, institutes S, financial_status fs
                  where a.patient_id=b.id
                  and fs.payment_method='BY_CREDIT_Institutes'
                  and f.current_branch_id=m.id
                  AND fs.institutes_id=S.id
                  and f.transaction_type='ExtraDiscount'
                  and f.sample_id2=a.id and f.id=fs.financial_id
                  and fs.transaction_status<>'PENDING'  and
                   fs.transaction_status<>'REJECTED'
                   and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID')
                    and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')
                   OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID'))
                    """


        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and a.medical_center_id=${medicalCenterId} "
        }

        query += """ union      SELECT  a.id, 'EXPENSES', 3 , 1,  a.from_medical_center_id, m.ar_name ,
  m.medical_center_code, fs.date_created ,
       a.id as invoice_num, null, TO_CHAR(a.expenses_description)  ,  m3.medical_center_code   as on_account, 0, 0,
  0 ,
    decode(fs.transaction_status,'PAID',-1*NVL(a.transaction_amount_to_transfer, f.transaction_amount),f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as credit,
        decode(fs.transaction_status,'PAID',-1*NVL(a.transaction_amount_to_transfer, f.transaction_amount),f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as new_credit,0

FROM expenses a , medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
where  a.from_medical_center_id=m.id
       and (a.to_medical_center_id=m2.id)
       and ( (a.id =emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
       and a.id=f.id
       and fs.financial_id=f.id

 """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and f.medical_center_id=${medicalCenterId} "
        }

        query += """ union
SELECT  a.id, 'EXPENSES', 3 , 1,  a.from_medical_center_id, m.ar_name ,
  m.medical_center_code, fs.date_created ,
  a.id as invoice_num, null, TO_CHAR(a.expenses_description)  ,m2.medical_center_code  as on_account,  0, 0,
  0 ,
  decode(fs.transaction_status,'PAID',-1*NVL(a.transaction_amount_to_transfer, f.transaction_amount),f.transaction_amount)   as credit,
    decode(fs.transaction_status,'PAID',-1*NVL(a.transaction_amount_to_transfer, f.transaction_amount),f.transaction_amount)   as new_credit,0

FROM expenses a , medical_center m, medical_center m2,  financial f , financial_status fs
where  a.from_medical_center_id=m.id
       and (a.to_medical_center_id=m2.id)
  and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
       and a.id=f.id
       and fs.financial_id=f.id   """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }
        if (medicalCenterId) {
            query += " and f.medical_center_id=${medicalCenterId} "
        }


        query += """) view_fin

        """

        if (!isCount) {
            query += " order by 3,1) IDD  where idd.id> ${offset} and idd.id <= ${max + offset} "
        }
//        print("------------------------")
//        println(query)
//        print("------------------------")
        return query
    }


    Integer getFinancialCount(GrailsParameterMap params) {

        params.put("isCount", "true")
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(this.generateFinancialQuery(params))
        return data["countNum"][0] as int
    }

    def searchFinancialMap(GrailsParameterMap params) {

        def list = []
        Map map = [:]
        def paidInForeignCurrencyData = null
        def paidInForeignCurrencyDataWithoutGroup = null
        def paidInForeignCurrencyByVisaWalletData = null
        Double signAmountByMap
        List<ForeignPaidAmountView> foreignPaidAmountViewList = []
        ForeignPaidAmountReportView foreignPaidAmountReportView
        List<ForeignPaidByVisaOrWalletView> foreignPaidByVisaOrWalletList = []
        ForeignPaidByVisaOrWalletReportView foreignPaidByVisaOrWalletReportView

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def data = sql.rows(this.generateFinancialQuery(params))

        if (params.boolean("isExcel") != null && params.boolean("isExcel") == Boolean.FALSE) {
            paidInForeignCurrencyData = sql.rows(this.generateCashPaidAmountForeignQuery(params))
            paidInForeignCurrencyDataWithoutGroup = sql.rows(this.generateCashPaidAmountForeignQueryWithoutGroup(params))
            Double amountInILS = paidInForeignCurrencyDataWithoutGroup[0]?.ILS
            signAmountByMap = amountInILS
            paidInForeignCurrencyData.each {
                foreignPaidAmountReportView = new ForeignPaidAmountReportView()
                foreignPaidAmountReportView.currencyType = it["currency_type"]
                foreignPaidAmountReportView.foreign = it["foreign"] as double
                foreignPaidAmountReportView.amountInILS = it["ILS"] as double
                foreignPaidAmountViewList << foreignPaidAmountReportView
                foreignPaidAmountReportView = null
            }
            paidInForeignCurrencyByVisaWalletData = sql.rows(this.generateVisaOrWalletPaidAmountForeignQuery(params))
            paidInForeignCurrencyByVisaWalletData.each {
                foreignPaidByVisaOrWalletReportView = new ForeignPaidByVisaOrWalletReportView()
                foreignPaidByVisaOrWalletReportView.visaWalletCompany = it["visa_wallet_company"]
                foreignPaidByVisaOrWalletReportView.cardNumber = it["card_number"]
                foreignPaidByVisaOrWalletReportView.currencyType = it["currency_type"]
                foreignPaidByVisaOrWalletReportView.foreign = it["foreign"] as double
                foreignPaidByVisaOrWalletReportView.amountInILS = it["ILS"] as double
                foreignPaidByVisaOrWalletList << foreignPaidByVisaOrWalletReportView
                foreignPaidByVisaOrWalletReportView = null
            }


        }

        data.each {
            map.id = it["id"] as long
            map.paymentMethod = it["payment_method"]
            map.printOrder = it["print_order"]
            map.medicalCenterId = it["medical_center_id"] as long
            map.branchId = it["branch_id"] as long
            map.branchName = it["branch_name"]
            map.sampleCode = it["sample_code"]
            map.transactionDate = it["DATE_CREATED"] //as Date
            map.invoiceNum = it["invoice_num"]
            map.patientId = it["patient_id"] as long
            map.patientName = it["Patient_name"]
            map.onAccount = it["on_account"]
            map.subtotal = it["subtotal"] as double
            map.discount = it["discount"] as double
            map.debit = it["debit"] as double
            map.credit = it["credit"] as double
            map.newCredit = it["new_credit"] as double
            map.balance = it["balance"] as double
            map.put("foreignPaidAmountViewList", foreignPaidAmountViewList)
            map.put("foreignPaidByVisaOrWalletList", foreignPaidByVisaOrWalletList)
            map.totalAmountILS = signAmountByMap
            list << map
            map = [:]

        }

        return list
    }

    PagedResultList searchFinancial(GrailsParameterMap params) {
        def paidInForeignCurrencyData = null
        List<ForeignPaidAmountView> foreignPaidAmountViewList = []
        ForeignPaidAmountReportView foreignPaidAmountReportView

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def data = sql.rows(this.generateFinancialQuery(params))

        List<ClaimView> dataList = []
        FinancialReportView financialReportView

        data.each {
            financialReportView = new FinancialReportView()
            financialReportView.id = it["id"] as long
            financialReportView.paymentMethod = it["payment_method"]
            financialReportView.printOrder = it["print_order"]
            financialReportView.medicalCenterId = it["medical_center_id"] as long
            financialReportView.branchId = it["branch_id"] as long
            financialReportView.branchName = it["branch_name"]
            financialReportView.sampleCode = it["sample_code"]
            financialReportView.transactionDate = it["DATE_CREATED"] //as Date
            financialReportView.invoiceNum = it["invoice_num"]
            financialReportView.patientId = it["patient_id"] as long
            financialReportView.patientName = it["Patient_name"]
            financialReportView.onAccount = it["on_account"]
            financialReportView.subtotal = it["subtotal"] as double
            financialReportView.discount = it["discount"] as double
            financialReportView.debit = it["debit"] as double
            financialReportView.credit = it["credit"] as double
            financialReportView.newCredit = it["new_credit"] as double
            financialReportView.balance = it["balance"] as double
            dataList << financialReportView
            financialReportView = null
        }


        PagedResultList pagedResultList = new PagedResultList(dataList, getFinancialCount(params))
        return pagedResultList
    }

    def searchFinancialExcel(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(this.generateFinancialQuery(params))
        List<ClaimView> dataList = []
        FinancialReportView financialReportView
        data.each {
            financialReportView = new FinancialReportView()
            financialReportView.id = it["id"] as long
            financialReportView.paymentMethod = it["payment_method"]
            financialReportView.printOrder = it["print_order"]
            financialReportView.medicalCenterId = it["medical_center_id"] as long
            financialReportView.branchId = it["branch_id"] as long
            financialReportView.branchName = it["branch_name"]
            financialReportView.sampleCode = it["sample_code"]
            financialReportView.transactionDate = it["date_created"] //as Date
            financialReportView.invoiceNum = it["invoice_num"]
            financialReportView.patientId = it["patient_id"] as long
            financialReportView.patientName = it["Patient_name"]
            financialReportView.onAccount = it["on_account"]
            financialReportView.subtotal = it["subtotal"] as double
            financialReportView.discount = it["discount"] as double
            financialReportView.debit = it["debit"] as double
            financialReportView.credit = it["credit"] as double
            financialReportView.balance = it["balance"] as double
            dataList << financialReportView
            financialReportView = null
        }
        return dataList
    }

    Integer getClaimCount(GrailsParameterMap params) {
        def claimType = params["claimType"]
        def institutesId = params.long("institutesId")
        def branchId = params.long("branchId")
        def fromDate = DateUtils.parseDate(params["fromDate"] as String)
        def toDate = DateUtils.parseDate(params["toDate"] as String)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "SELECT count(a.id) as countNum FROM sample a, institutes b, invoice c,patient p ,financial_status fs  \n" +

                "  where a.institutes_id=b.id and a.id=c.sample_id\n" +
//                "  and a.payment_method='BY_CREDIT_Institutes'\n" +
                "  and p.id = a.patient_id\n" +
                "    and fs.transaction_status='PAID' \n" +
                " and c.id=fs.financial_id " +
                "  and a.medical_center_id=1 and a.sample_status<>'CREATED'\n" +
                "  and c.id=(select max(id) from invoice where serial_number=c.serial_number)\n" +
                "  and c.id not in (select financial_id FROM financial_status a where transaction_status='CANCEL' )"
        if (claimType == "includeInClaim")
            query += "  and include_in_clam_report=1"
        else if (claimType == "internalCash")
            query += "  and internal_cash_debit=1"
        if (institutesId) {
            query += "  and a.institutes_id=${institutesId}"
        }
        if (branchId) {
            query += "  and a.branch_id=${branchId}"
        }
        if (fromDate)
            query += "  and trunc(fs.date_created) >= '${fromDate.format("dd-MMM-yyyy")}'"
        if (toDate)
            query += "  and trunc(fs.date_created) <= '${toDate.format("dd-MMM-yyyy")}'"


        def data = sql.rows(query)
        return data["countNum"][0] as int
    }

    def searchClaimQuery(GrailsParameterMap params) {
        def claimType = params["claimType"]
        def institutesId = params.long("institutesId")
        def branchId = params.long("branchId")
        def fromDate = DateUtils.parseDate(params["fromDate"] as String)
        def toDate = DateUtils.parseDate(params["toDate"] as String)
        def max = params.parseMax()
        def offset = params.parseOffset()

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select * from (SELECT rownum as row_num, a.id, a.branch_id, b.ar_name as institutes_name, ben.AR_NAME as beneficiary_name,\n" +
                "trunc(fs.date_created) as sample_date,  c.serial_number as invoice_number,a.employee_reference,\n" +
                "a.form_number, a.institutes_id, a.medical_center_id, a.patient_id,p.ar_name as patient_name,\n" +
                "a.payment_method, a.sample_code, a.sample_status,\n" +
                "a.subtotal, a.discount, get_sample_total_paid(a.id) as receipts,\n" +
                "nvl(a.extra_discount_amount,0) as extra_discount,\n" +
                "a.total_price- get_sample_total_paid(a.id)- nvl(a.extra_discount_amount,0) as net\n" +
                "  FROM sample a, institutes b, invoice c,patient p , financial_status fs ,  BENEFICIARY ben \n" +
                "  where a.institutes_id=b.id and a.id=c.sample_id\n"
        if (institutesId) {
            query += "  and a.institutes_id=${institutesId}"
        }
        if (branchId) {
            query += "  and a.branch_id=${branchId}"
        }
        query += "  and a.payment_method='BY_CREDIT_Institutes'   and c.id=fs.financial_id \n" +
                "  and p.id = a.patient_id  and ben.id(+) =a.BENEFICIARY_ID \n" +
                "  and a.medical_center_id=1 and a.sample_status<>'CREATED'\n" +
                "    and fs.transaction_status='PAID' \n" +
                " and c.id not in (select financial_id FROM financial_status a where transaction_status='CANCEL' ) \n"
        if (claimType == "includeInClaim")
            query += "  and include_in_clam_report=1"
        else if (claimType == "internalCash")
            query += "  and internal_cash_debit=1"

        if (fromDate)
            query += "  and trunc(fs.date_created) >= '${fromDate.format("dd-MMM-yyyy")}'"
        if (toDate)
            query += "  and trunc(fs.date_created) <= '${toDate.format("dd-MMM-yyyy")}'"
        query += " order by institutes_name,sample_date "
        query += " ) vw1  "
        query += " where row_num> ${offset} and row_num <= ${max + offset} "

        def data = sql.rows(query)


        return data
    }

    PagedResultList searchClaim(GrailsParameterMap params) {
        def data = this.searchClaimQuery(params)
        List<ClaimView> dataList = []
        ClaimView claimView
        data.each {
            claimView = new ClaimView()
            claimView.id = it["id"] as long
            claimView.branchId = it["branch_id"] as long
            claimView.sampleDate = Date.parse("yyyy-MM-dd", it["sample_date"]?.toString())
            claimView.invoiceNumber = it["invoice_number"]
            claimView.employeeReference = it["employee_reference"]
            claimView.formNumber = it["form_number"]
            claimView.institutesId = it["institutes_id"] as long
            claimView.institutesName = it["institutes_name"]
            claimView.beneficiaryName = it["beneficiary_name"]
            claimView.medicalCenterId = it["medical_center_id"] as long
            claimView.patientId = it["patient_id"] as long
            claimView.patientName = it["patient_name"]
            claimView.paymentMethod = it["payment_method"]
            claimView.sampleCode = it["sample_code"]
            claimView.sampleStatus = it["sample_status"]
            claimView.subtotal = it["subtotal"] as double
            claimView.discount = it["discount"] as double
            claimView.receipts = it["receipts"] as double
            claimView.net = it["net"] as double
            claimView.extraDiscount = it["extra_discount"] as double

            dataList << claimView
            claimView = null
        }

        PagedResultList pagedResultList = new PagedResultList(dataList, getClaimCount(params) as int)
        return pagedResultList
    }

    def searchClaimExcel(GrailsParameterMap params) {
        def data = this.searchClaimQuery(params)
        List<ClaimView> dataList = []
        ClaimView claimView
        data.each {
            claimView = new ClaimView()
            claimView.id = it["id"] as long
            claimView.branchId = it["branch_id"] as long
            claimView.sampleDate = it["sample_date"]
            claimView.invoiceNumber = it["invoice_number"]
            claimView.employeeReference = it["employee_reference"]
            claimView.formNumber = it["form_number"]
            claimView.institutesId = it["institutes_id"] as long
            claimView.institutesName = it["institutes_name"]
            claimView.beneficiaryName = it["beneficiary_name"]
            claimView.medicalCenterId = it["medical_center_id"] as long
            claimView.patientId = it["patient_id"] as long
            claimView.patientName = it["patient_name"]
            claimView.paymentMethod = it["payment_method"]
            claimView.sampleCode = it["sample_code"]
            claimView.sampleStatus = it["sample_status"]
            claimView.subtotal = it["subtotal"] as double
            claimView.discount = it["discount"] as double
            claimView.receipts = it["receipts"] as double
            claimView.net = it["net"] as double
            claimView.extraDiscount = it["extra_discount"] as double


            dataList << claimView
            claimView = null
        }
        return dataList
    }

    def searchFinanceClaimExcel(GrailsParameterMap params) {
        def data = financeClaimsService.search(params)

        List<ClaimView> dataList = []
        ClaimView claimView
        data.list.each { FinanceClaims it ->

            claimView = new ClaimView()
            claimView.id = it.id
            claimView.branchId = it.batchId
            claimView.sampleDate = it.sampleDate
            claimView.invoiceNumber = it.invoiceNumber
            claimView.employeeReference = it.employeeReference
            claimView.formNumber = it.formNumber
            claimView.institutesId = it.institutesId
            claimView.institutesName = it.institutesName
            claimView.medicalCenterId = it.medicalCenterId
            claimView.patientId = it.patientId
            claimView.patientName = it.patientName
            claimView.paymentMethod = it.paymentMethod
            claimView.sampleCode = it.sampleCode
            claimView.sampleStatus = it.sampleStatus
            claimView.subtotal = it.subtotal
            claimView.discount = it.discount
            claimView.receipts = it.receipts
            claimView.net = it.net
            claimView.extraDiscount = it.extraDiscount
            claimView.beneficiaryName = it.beneficiaryName

            claimView.clinicalDiagnosis = it.clinicalDiagnosis
            claimView.sharePercentage = it.sharePercentage
            claimView.maximumCoverage = it.maximumCoverage

            dataList << claimView
            claimView = null
        }
        return dataList
    }

    def labSheetData(GrailsParameterMap params) {
        ArrayList list = new ArrayList()
        def sampleTestIds
        if (params["sampleTestIds"]) {
            sampleTestIds = params["sampleTestIds"].split("_")
            sampleTestIds.each { it ->
                if (it) {
                    def sampleTestId = Cryptography.decryptWithDate(SystemConstant.sampleTestKey, it)
                    list.add(sampleTestId)
                }
            }
        }


        String sampleId = Cryptography.decryptWithDate(SystemConstant.sampleKey, params.id)
        String testId = params.testId
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select rownum,a.sample_code, a.id as sample_id, a.branch_id, a.test_time, a.sample_date, \n" +
                "b.patient_code,  b.ar_name as patient_name,b.MOBILE as phone, b.gender,a.age as age, a.age_unit ,nvl(a.doctor_id,-1) as doctor_id, nvl(d.ar_name,'No Referring Doctor') as doctor_name,\n" +
                "s.test_type_id, t.name as test_name, t.lab_section_id, l.name as lab_section_name, t.sample_type_id, st.name as sample_type_name,\n" +
                "sc.id as category_id, sc.name as category_name, nvl(sc.parent_category_id,sc.id) as parent_cat_id,\n" +
                "ant.antibiotics_id, acs.description as antibiotics_name,\n" +
                "GET_SLICE_SI(  a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'LAB') as SI_UNIT,\n" +
                "GET_SLICE_CONV(  a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'LAB') as CONV_UNIT,\n" +
                "(select count(sss.test_type_id) from sample_test_type sss where sss.sample_test_type_id=a.id) as tests_count,\n" +
                "SAMPLE_TYPE_FN(a.id) as sample_type_all ,  sample_tests_fn(a.id) as sample_tests_all  , tc.NAME as TRANSPORT_CONDITION_NAME , sv.NAME as SAMPLE_VOLUME_NAME, \n" +
                " t.is_referral  ,b.whats_app_no as whats_app_no" +
                " from SAMPLE a , PATIENT b, DOCTOR d, sample_test_type s,\n" +
                " test_type t, lab_section l, sample_type st, sample_category sc,\n" +
                " sample_category_antibiotics ant, antibiotics acs , TRANSPORT_CONDITION tc , SAMPLE_VOLUME sv , sample_test g \n" +
                "where a.patient_id=b.id\n" +
                "and a.doctor_id=d.id(+)\n" +
                "and a.id=s.sample_test_type_id\n" +
                "and s.test_type_id=t.id\n" +
                "and sc.is_measurable = 1\n" +
                "and t.lab_section_id=l.id\n" +
                "and t.sample_type_id=st.id\n" +
                "and t.id=sc.test_type_id\n" +
                "and g.sample_id=a.id\n" +
                "and g.test_type_id=t.id\n" +
                "and sc.is_active=1\n" +
                "and t.TRANSPORT_CONDITION_ID = tc.ID(+)\n" +
                "and t.SAMPLE_VOLUME_ID = sv.ID(+) \n" +
                "and nvl(sc.parent_category_id,-1) not in (select id from sample_category where sample_category.is_active=0 ) " +
                "and sc.id=ant.sample_category_antibiotics_id(+)\n" +
                "and ant.antibiotics_id=acs.id(+)  \n"
        if (sampleId)
            query += "and a.id  = " + sampleId
        if (testId)
            query += " and s.test_type_id  = " + testId
        if (sampleTestIds)
            query += " and g.id  in ( " + list.toString().replace("[", "").replaceAll("]", "") + " ) "

        query += " order by a.id, l.id, s.test_type_id, sc.order_number ,ant.antibiotics_id \n"


        def data = sql.rows(query)
        def finalList = []
        SampleTestDetailsView sampleTestDetailsView
        data.each {
            sampleTestDetailsView = new SampleTestDetailsView()
            sampleTestDetailsView.sampleCode = it["SAMPLE_CODE"]
            sampleTestDetailsView.sampleId = it["SAMPLE_ID"] as long
            sampleTestDetailsView.branchId = it["BRANCH_ID"] as long
            sampleTestDetailsView.testTime = it["TEST_TIME"]
            sampleTestDetailsView.sampleDate = Date.parse("yyyy-MM-dd", it["SAMPLE_DATE"]?.toString())
            sampleTestDetailsView.patientCode = it["PATIENT_CODE"]
            sampleTestDetailsView.patientName = it["PATIENT_NAME"]
            sampleTestDetailsView.phone = it["PHONE"]
            sampleTestDetailsView.gender = it["GENDER"]
            sampleTestDetailsView.age = it["AGE"] as int
            sampleTestDetailsView.doctorId = it["DOCTOR_ID"] as long
            sampleTestDetailsView.doctorName = it["DOCTOR_NAME"]
            sampleTestDetailsView.testTypeId = it["TEST_TYPE_ID"] as long
            sampleTestDetailsView.testName = it["TEST_NAME"]
            sampleTestDetailsView.labSectionId = it["LAB_SECTION_ID"] as long
            sampleTestDetailsView.labSectionName = it["LAB_SECTION_NAME"]
            sampleTestDetailsView.sampleTypeId = it["SAMPLE_TYPE_ID"] as long
            sampleTestDetailsView.sampleTypeName = it["SAMPLE_TYPE_NAME"]
            sampleTestDetailsView.categoryId = it["CATEGORY_ID"] as long
            sampleTestDetailsView.categoryName = it["CATEGORY_NAME"]
            sampleTestDetailsView.parentCatId = it["PARENT_CAT_ID"]
            sampleTestDetailsView.antibioticsId = it["ANTIBIOTICS_ID"] as long
            sampleTestDetailsView.antibioticsName = it["ANTIBIOTICS_NAME"]
            sampleTestDetailsView.siUnit = it["SI_UNIT"]
            sampleTestDetailsView.convUnit = it["CONV_UNIT"]
            sampleTestDetailsView.testsCount = it["TESTS_COUNT"] as int
            sampleTestDetailsView.sampleTypeAll = it["SAMPLE_TYPE_ALL"]
            sampleTestDetailsView.ageUnit = it["AGE_UNIT"]
            sampleTestDetailsView.sampleTestsAll = it["SAMPLE_TESTS_ALL"]
            sampleTestDetailsView.transportConditionName = it["TRANSPORT_CONDITION_NAME"]
            sampleTestDetailsView.sampleVolumeName = it["SAMPLE_VOLUME_NAME"]
            sampleTestDetailsView.isReferral = it["is_referral"]
            sampleTestDetailsView.whatsAppNo = it["whats_app_no"]
            finalList << sampleTestDetailsView
            sampleTestDetailsView = null
        }
        return finalList
    }




    private String lobToString(def value) {
        if (value == null) {
            return null
        }

        try {
            Reader reader = null

            try {
                reader = value.getCharacterStream()
            } catch (ignored) {
                reader = null
            }

            if (reader != null) {
                StringWriter writer = new StringWriter()
                char[] buffer = new char[4096]
                int length

                try {
                    while ((length = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, length)
                    }
                } finally {
                    try {
                        reader.close()
                    } catch (ignored) {}
                }

                return writer.toString()
            }
        } catch (ignored) {}

        try {
            return value.stringValue()?.toString()
        } catch (ignored) {}

        return value.toString()
    }
    def resultSheetData(GrailsParameterMap params) {

        Long sampleId = Cryptography.decryptWithDate(SystemConstant.sampleKey, params.id)//params.long('id')
        Long medicalCenterId = params.long("medicalCenterId")
        def listOfTestIds = []
        def listResult = params.list('selector')
        listResult?.each {
            listOfTestIds.add(Cryptography.decryptWithDate(SystemConstant.sampleTestKey, it))
        }
        return resultSheetLoad(sampleId, medicalCenterId)
    }

    def resultSheetLoad(long sampleId, Long medicalCenterId) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select rownum,a.sample_code, null as QR_content, a.id as sample_id, a.branch_id, a.test_time,  to_char(a.date_created, 'yyyy-MM-dd hh24:mi')   as sample_date  , (select nvl(max(st2.ACCEPTED_DATE),sysdate ) from sample_test st2 where st2.sample_id=a.id) as ACCEPTED_DATE , \n" +
                "b.patient_code,  b.ar_name as patient_name,b.en_name as patient_latin_name,b.MOBILE as phone, b.gender,a.age as age, a.age_unit ,nvl(a.doctor_id,-1) as doctor_id, nvl(d.ar_name,'No Referring Doctor') as doctor_name,\n" +
                "s.test_type_id, t.name as test_name,t.is_referral,has_referral(a.id) as has_referral ,food_sample(a.id) as food_sample, t.lab_section_id, l.name as lab_section_name, t.sample_type_id, st.name as sample_type_name,\n" +
                "sc.id as category_id, sc.name as category_name, nvl(sc.parent_category_id,sc.id) as parent_cat_id,\n" +
                "ant.antibiotics_id, acs.description as antibiotics_name,t.is_require_doctor_approval as is_require_doctor_approval,\n" +
                "GET_SLICE_SI( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as SI_UNIT,\n" +
                "GET_SLICE_CONV( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as CONV_UNIT,\n" +
                "(select count(sss.test_type_id) from sample_test sss where sss.sample_id=a.id) as tests_count,\n" +
                "SAMPLE_TYPE_FN(a.id) as sample_type_all,\n" +
                "get_si_result(a.id,sc.id) as SI_RESULT,\n" +
                "get_conv_result(a.id,sc.id,ant.antibiotics_id) as CONV_RESULT,\n" +
                "get_anti_result(a.id,sc.id,ant.antibiotics_id) as ANTIBIOTIC_RESULT,\n" +
                "get_prev1 ( t.id, b.id,a.id) As prev_date_1,\n" +
                "get_prev2 ( t.id, b.id,a.id) As prev_date_2,\n" +
                "get_result_prev1 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_1,\n" +
                "get_result_prev2 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_2,\n" +
                "t.is_require_doctor_approval as IS_REQUIRE_DOCTOR_APPROVAL, \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 1),'') as SIGN_DESC1,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 1),'') as SIGN_PHOTO1 , \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 2),'') as SIGN_DESC2,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 2),'') as SIGN_PHOTO2 ,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 3),'') as SIGN_DESC3,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 3),'') as SIGN_PHOTO3,  \n" +
                " get_last_active_report_note() as REPORT_NOTE , b.id_number as Patient_Id , " +
                "   get_approved_by(a.id) as approved_by  " +
                " from SAMPLE a , PATIENT b, DOCTOR d, sample_test s,\n" +
                " test_type t, lab_section l, sample_type st, sample_category sc,\n" +
                " sample_category_antibiotics ant, antibiotics acs \n" +
                "where a.patient_id=b.id\n" +
                "and a.doctor_id=d.id(+)\n" +
                "and a.id=s.sample_id\n" +
                "and s.system_file_name is null\n" +
                "and s.test_type_id=t.id\n" +
                "and t.lab_section_id=l.id\n" +
                "and t.sample_type_id=st.id\n" +
                "and t.id=sc.test_type_id\n" +
                "and sc.is_measurable = 1\n" +
                "and sc.id=ant.sample_category_antibiotics_id(+)\n" +
                "and ant.antibiotics_id=acs.id(+)\n" +
                "and a.sample_status='COMPLETED'\n" +
                " and nvl(t.do_not_print_in_result_sheet,'0')='0' \n" +
                " and nvl(sc.do_not_print_in_result_sheet,'0')='0' \n" +
                "and (\n" +
                "    (\n" +
                "        get_si_result(a.id,sc.id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        get_conv_result(a.id,sc.id,ant.antibiotics_id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        (get_si_result(a.id,sc.id) is null OR DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) = 0)\n" +
                "        and\n" +
                "        (get_conv_result(a.id,sc.id,ant.antibiotics_id) is null OR DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) = 0)\n" +
                "    )\n" +
                ")"
        if (sampleId)
            query += "and a.id  = " + sampleId
        query += " order by a.id,\n" +
                "         l.id,\n" +
                "         t.print_priority,\n" +
                "         sc.order_number,\n" +
                "         case\n" +
                "             when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Sensitive')) = 0 then 2\n" +
                "             when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Resist')) = 0 then 1\n" +
                "             else 0\n" +
                "         end desc "


        def data = sql.rows(query)
        def finalList = []
        SampleTestCompletedView sampleTestCompletedView
        data.each {
            sampleTestCompletedView = new SampleTestCompletedView()
            sampleTestCompletedView.sampleCode = it["SAMPLE_CODE"]
//            sampleTestCompletedView.qrCode = it["QR_content"]
            sampleTestCompletedView.qrCode = generateQR(medicalCenterId, sampleId)
            sampleTestCompletedView.sampleId = it["SAMPLE_ID"] as long
            sampleTestCompletedView.branchId = it["BRANCH_ID"] as long
            sampleTestCompletedView.resultDate = Date.parse("yyyy-MM-dd HH:mm", it["ACCEPTED_DATE"]?.toString())
            sampleTestCompletedView.testTime = Date.parse("yyyy-MM-dd HH:mm", it["ACCEPTED_DATE"]?.toString()).format('HH:mm')
            sampleTestCompletedView.sampleDate = Date.parse("yyyy-MM-dd HH:mm", it["sample_date"]?.toString())
            sampleTestCompletedView.patientCode = it["PATIENT_CODE"]
            sampleTestCompletedView.patientName = it["PATIENT_NAME"]
            sampleTestCompletedView.patientLatinName = it["PATIENT_LATIN_NAME"]
            sampleTestCompletedView.phone = it["PHONE"]
            sampleTestCompletedView.gender = it["GENDER"]
            sampleTestCompletedView.age = it["AGE"] as int
            sampleTestCompletedView.doctorId = it["DOCTOR_ID"] as long
            sampleTestCompletedView.doctorName = it["DOCTOR_NAME"]
            sampleTestCompletedView.testTypeId = it["TEST_TYPE_ID"] as long
            sampleTestCompletedView.testName = it["TEST_NAME"]
            sampleTestCompletedView.isReferral = it["is_referral"]
            sampleTestCompletedView.hasReferral = it["has_referral"]
            sampleTestCompletedView.foodSample = it["food_sample"]
            sampleTestCompletedView.labSectionId = it["LAB_SECTION_ID"] as long
            sampleTestCompletedView.labSectionName = it["LAB_SECTION_NAME"]
            sampleTestCompletedView.sampleTypeId = it["SAMPLE_TYPE_ID"] as long
            sampleTestCompletedView.sampleTypeName = it["SAMPLE_TYPE_NAME"]
            sampleTestCompletedView.categoryId = it["CATEGORY_ID"] as long
            sampleTestCompletedView.categoryName = it["CATEGORY_NAME"]
            sampleTestCompletedView.parentCatId = it["PARENT_CAT_ID"]
            sampleTestCompletedView.antibioticsId = it["ANTIBIOTICS_ID"] as long
            sampleTestCompletedView.antibioticsName = it["ANTIBIOTICS_NAME"]
            sampleTestCompletedView.siUnit = it["SI_UNIT"]
            sampleTestCompletedView.convUnit = it["CONV_UNIT"]
            sampleTestCompletedView.testsCount = it["TESTS_COUNT"] as int
            sampleTestCompletedView.sampleTypeAll = it["SAMPLE_TYPE_ALL"]
            sampleTestCompletedView.ageUnit = it["AGE_UNIT"]
            sampleTestCompletedView.siResult = lobToString(it["SI_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.convResult = lobToString(it["CONV_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.antibioticResult = lobToString(it["ANTIBIOTIC_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult1 = lobToString(it["PREV_RESULT_1"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult2 = lobToString(it["PREV_RESULT_2"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevDate1 = it["PREV_DATE_1"]
            sampleTestCompletedView.prevDate2 = it["PREV_DATE_2"]
            sampleTestCompletedView.isRequireDoctorApproval = it["IS_REQUIRE_DOCTOR_APPROVAL"]
            sampleTestCompletedView.reportNote = it["REPORT_NOTE"]
            sampleTestCompletedView.patientId = it["Patient_Id"]
            sampleTestCompletedView.signDesc1 = it["SIGN_DESC1"]
            sampleTestCompletedView.approvedBy = it["approved_by"]
            Blob blob = it["SIGN_PHOTO1"]
            if (blob && blob?.length() > 0) {
                int blobLength = (int) blob.length();
                byte[] blobAsBytes = blob.getBytes(1, blobLength);
                sampleTestCompletedView.signPhoto1 = blobAsBytes
                blob.free();
            }
            sampleTestCompletedView.signDesc2 = it["SIGN_DESC2"]
            Blob blob2 = it["SIGN_PHOTO2"]
            if (blob2 && blob2?.length() > 0) {
                int blobLength2 = (int) blob2.length();
                byte[] blobAsBytes2 = blob2.getBytes(1, blobLength2);
                sampleTestCompletedView.signPhoto2 = blobAsBytes2
                blob2.free();
            }
            sampleTestCompletedView.signDesc3 = it["SIGN_DESC3"]
            Blob blob3 = it["SIGN_PHOTO3"]
            if (blob3 && blob3?.length() > 0) {
                int blobLength3 = (int) blob3.length();
                byte[] blobAsBytes3 = blob3.getBytes(1, blobLength3);
                sampleTestCompletedView.signPhoto3 = blobAsBytes3
                blob3.free();
            }
            finalList << sampleTestCompletedView
            sampleTestCompletedView = null
        }
        return finalList
    }

    def resultPartialData(GrailsParameterMap params) {
        def listOfTestIds = []
        def listResult = params.list('selector')
        listResult?.each {
            listOfTestIds.add(Cryptography.decryptWithDate(SystemConstant.sampleTestKey, it))
        }

        Long medicalCenterId = params.long("medicalCenterId")
        boolean isDraft = params.boolean("isDraft")
        Long sampleId = Cryptography.decryptWithDate(SystemConstant.sampleKey, params['id'])
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select rownum,a.sample_code,null as QR_content, a.id as sample_id, a.branch_id, a.test_time, to_char(a.sample_date, 'yyyy-MM-dd hh24:mi') as sample_date , (select nvl(max(st2.ACCEPTED_DATE),sysdate ) from sample_test st2 where st2.sample_id=a.id) as ACCEPTED_DATE , \n" +
                "b.patient_code,  b.ar_name as patient_name,b.MOBILE as phone, b.gender,a.age as age, a.age_unit ,nvl(a.doctor_id,-1) as doctor_id, nvl(d.ar_name,'No Referring Doctor') as doctor_name,\n" +
                "s.test_type_id, t.name as test_name,t.is_referral, has_referral(a.id) as has_referral ,food_sample(a.id) as food_sample,t.lab_section_id, l.name as lab_section_name, t.sample_type_id, st.name as sample_type_name,\n" +
                "sc.id as category_id, sc.name as category_name, nvl(sc.parent_category_id,sc.id) as parent_cat_id,\n" +
                "ant.antibiotics_id, acs.description as antibiotics_name,\n"
        if (isDraft)
            query += "GET_SLICE_SI( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'LAB') as SI_UNIT,\n" +
                    "GET_SLICE_CONV( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'LAB') as CONV_UNIT,\n"
        if (!isDraft)
            query += "GET_SLICE_SI( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as SI_UNIT,\n" +
                    "GET_SLICE_CONV( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as CONV_UNIT,\n"

        query += "(select count(sss.test_type_id) from sample_test sss where sss.sample_id=a.id) as tests_count,\n" +
                "SAMPLE_TYPE_FN(a.id) as sample_type_all,\n" +
                "get_si_result(a.id,sc.id) as SI_RESULT,\n" +
                "get_conv_result(a.id,sc.id,ant.antibiotics_id) as CONV_RESULT,\n" +
                "get_anti_result(a.id,sc.id,ant.antibiotics_id) as ANTIBIOTIC_RESULT,\n" +
                "get_prev1 ( t.id, b.id,a.id) As prev_date_1,\n" +
                "get_prev2 ( t.id, b.id,a.id) As prev_date_2,\n" +
                "get_result_prev1 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_1,\n" +
                "get_result_prev2 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_2,\n" +
                "t.is_require_doctor_approval as IS_REQUIRE_DOCTOR_APPROVAL, \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 1),'') as SIGN_DESC1,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 1),'') as SIGN_PHOTO1 , \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 2),'') as SIGN_DESC2,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 2),'') as SIGN_PHOTO2 ,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 3),'') as SIGN_DESC3,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 3),'') as SIGN_PHOTO3,  \n" +
                " get_last_active_report_note() as REPORT_NOTE , b.id_number as Patient_Id" +
                " from SAMPLE a , PATIENT b, DOCTOR d, sample_test s,\n" +
                " test_type t, lab_section l, sample_type st, sample_category sc,\n" +
                " sample_category_antibiotics ant, antibiotics acs\n" +
                "where a.patient_id=b.id\n" +
                "and a.doctor_id=d.id(+)\n" +
                "and a.id=s.sample_id\n" +
                "and s.system_file_name is null\n" +
                "and s.test_type_id=t.id\n" +
                "and sc.is_measurable = 1\n" +
                "and t.lab_section_id=l.id\n" +
                "and t.sample_type_id=st.id\n" +
                "and t.id=sc.test_type_id\n" +
                "and sc.id=ant.sample_category_antibiotics_id(+)\n" +
                "and ant.antibiotics_id=acs.id(+)\n"
        if (!isDraft)
            query += "and s.test_type_status='ACCEPTED'\n"
//        query += "and (get_si_result(a.id,sc.id) not in ('x','X') OR \n" +
//                "get_conv_result(a.id,sc.id,ant.antibiotics_id)  not in ('x','X') OR \n" +
//                "(get_si_result(a.id,sc.id) is null and get_conv_result(a.id,sc.id,ant.antibiotics_id) is null) ) \n"
        query += "and (\n" +
                "    (\n" +
                "        get_si_result(a.id,sc.id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        get_conv_result(a.id,sc.id,ant.antibiotics_id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        (get_si_result(a.id,sc.id) is null OR DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) = 0)\n" +
                "        and\n" +
                "        (get_conv_result(a.id,sc.id,ant.antibiotics_id) is null OR DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) = 0)\n" +
                "    )\n" +
                ") \n"
        if (sampleId)
            query += "and a.id  = " + sampleId
        if (listOfTestIds?.size() > 0) {
            query += " and s.ID IN(${listOfTestIds.join(",")})"
        }
//        query += "order by a.id, l.id, t.id,  sc.order_number  , get_anti_result(a.id,sc.id,ant.antibiotics_id) desc "
        query += "order by a.id,\n" +
                "         l.id,\n" +
                "         t.id,\n" +
                "         sc.order_number,\n" +
                "         case\n" +
                "             when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Sensitive')) = 0 then 2\n" +
                "             when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Resist')) = 0 then 1\n" +
                "             else 0\n" +
                "         end desc "

        def data = sql.rows(query)
        def finalList = []
        SampleTestCompletedView sampleTestCompletedView
        data.each {
            sampleTestCompletedView = new SampleTestCompletedView()
            sampleTestCompletedView.sampleCode = it["SAMPLE_CODE"]
//            sampleTestCompletedView.qrCode = it["QR_content"]
            sampleTestCompletedView.qrCode = generateQR(medicalCenterId, sampleId)
            sampleTestCompletedView.sampleId = it["SAMPLE_ID"] as long
            sampleTestCompletedView.branchId = it["BRANCH_ID"] as long
            sampleTestCompletedView.resultDate = Date.parse("yyyy-MM-dd HH:mm", it["ACCEPTED_DATE"]?.toString())
            sampleTestCompletedView.testTime = Date.parse("yyyy-MM-dd HH:mm", it["ACCEPTED_DATE"]?.toString()).format('HH:mm')
            sampleTestCompletedView.sampleDate = Date.parse("yyyy-MM-dd HH:mm", it["sample_date"]?.toString())
            sampleTestCompletedView.patientCode = it["PATIENT_CODE"]
            sampleTestCompletedView.patientName = it["PATIENT_NAME"]
            sampleTestCompletedView.phone = it["PHONE"]
            sampleTestCompletedView.gender = it["GENDER"]
            sampleTestCompletedView.age = it["AGE"] as int
            sampleTestCompletedView.doctorId = it["DOCTOR_ID"] as long
            sampleTestCompletedView.doctorName = it["DOCTOR_NAME"]
            sampleTestCompletedView.testTypeId = it["TEST_TYPE_ID"] as long
            sampleTestCompletedView.testName = it["TEST_NAME"]
            sampleTestCompletedView.isReferral = it["is_referral"]
            sampleTestCompletedView.hasReferral = it["has_referral"]
            sampleTestCompletedView.foodSample = it["food_sample"]
            sampleTestCompletedView.labSectionId = it["LAB_SECTION_ID"] as long
            sampleTestCompletedView.labSectionName = it["LAB_SECTION_NAME"]
            sampleTestCompletedView.sampleTypeId = it["SAMPLE_TYPE_ID"] as long
            sampleTestCompletedView.sampleTypeName = it["SAMPLE_TYPE_NAME"]
            sampleTestCompletedView.categoryId = it["CATEGORY_ID"] as long
            sampleTestCompletedView.categoryName = it["CATEGORY_NAME"]
            sampleTestCompletedView.parentCatId = it["PARENT_CAT_ID"]
            sampleTestCompletedView.antibioticsId = it["ANTIBIOTICS_ID"] as long
            sampleTestCompletedView.antibioticsName = it["ANTIBIOTICS_NAME"]
            sampleTestCompletedView.siUnit = it["SI_UNIT"]
            sampleTestCompletedView.convUnit = it["CONV_UNIT"]
            sampleTestCompletedView.testsCount = it["TESTS_COUNT"] as int
            sampleTestCompletedView.sampleTypeAll = it["SAMPLE_TYPE_ALL"]
            sampleTestCompletedView.ageUnit = it["AGE_UNIT"]
            sampleTestCompletedView.siResult = lobToString(it["SI_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.convResult = lobToString(it["CONV_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.antibioticResult = lobToString(it["ANTIBIOTIC_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult1 = lobToString(it["PREV_RESULT_1"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult2 = lobToString(it["PREV_RESULT_2"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult2 = lobToString(it["PREV_RESULT_2"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevDate1 = it["PREV_DATE_1"]
            sampleTestCompletedView.prevDate2 = it["PREV_DATE_2"]
            sampleTestCompletedView.isRequireDoctorApproval = it["IS_REQUIRE_DOCTOR_APPROVAL"]
            sampleTestCompletedView.reportNote = it["REPORT_NOTE"]
            sampleTestCompletedView.patientId = it["Patient_Id"]
            sampleTestCompletedView.signDesc1 = it["SIGN_DESC1"]
            Blob blob = it["SIGN_PHOTO1"]
            if (blob && blob?.length() > 0) {
                int blobLength = (int) blob.length();
                byte[] blobAsBytes = blob.getBytes(1, blobLength);
                sampleTestCompletedView.signPhoto1 = blobAsBytes
                blob.free();
            }
            sampleTestCompletedView.signDesc2 = it["SIGN_DESC2"]
            Blob blob2 = it["SIGN_PHOTO2"]
            if (blob2 && blob2?.length() > 0) {
                int blobLength2 = (int) blob2.length();
                byte[] blobAsBytes2 = blob2.getBytes(1, blobLength2);
                sampleTestCompletedView.signPhoto2 = blobAsBytes2
                blob2.free();
            }
            sampleTestCompletedView.signDesc3 = it["SIGN_DESC3"]
            Blob blob3 = it["SIGN_PHOTO3"]
            if (blob3 && blob3?.length() > 0) {
                int blobLength3 = (int) blob3.length();
                byte[] blobAsBytes3 = blob3.getBytes(1, blobLength3);
                sampleTestCompletedView.signPhoto3 = blobAsBytes3
                blob3.free();
            }
            finalList << sampleTestCompletedView
            sampleTestCompletedView = null
        }
        return finalList
    }
    def sampleTestService

    def barCodeData(GrailsParameterMap params) {
        Sample sampleInstance = sampleService.getInstance(params, true)
        SampleTest sampleTestInstance
        if (params.sampleTestId) {
            params.id = params.sampleTestId
            sampleTestInstance = sampleTestService.getInstance(params, true)
        }
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select rownum id, aa.\"SAMPLE_ID\",aa.\"SAMPLE_CODE\",aa.\"SAMPLE_DATE\",aa.\"MEDICAL_CENTER_ID\",aa.\"AR_NAME\",aa.\"BARCODE\"  , \n" +
                " aa.\"SAMPLE_TYPE_NAME\", aa.\"MACHINE_NAME\" from " +
                "(SELECT  distinct a.id as sample_id,to_char(a.sample_date,'dd/MM/yyyy') as SAMPLE_DATE, a.sample_code, a.medical_center_id, p.ar_name,        \n" +
                " lpad(get_machine_id ( a.branch_id, b.test_type_id),2,'0')||lpad(c.sample_type_id,2,'0') " +
                " ||lpad(a.id,7,'0') as BARCODE , \n " +
                " nvl(t.abbr,t.name) SAMPLE_TYPE_NAME, get_machine_name( get_machine_id( a.branch_id, b.test_type_id)) as MACHINE_NAME " +
                " FROM sample a, sample_test_type b  , test_type c, PATIENT p , sample_type t ,sample_test d \n" +
                " where a.id=b.sample_test_type_id\n" +
                " and b.test_type_id=c.id\n" +
                " and a.patient_id=p.id\n" +
                " and c.SAMPLE_TYPE_ID=t.id " +
                " and d.sample_id=a.id " +
                " and d.test_type_id=c.id "
        if (sampleInstance)
            query += " and a.id = " + sampleInstance.id.toString()
        else query += " and a.id  is null "


        if (sampleTestInstance)
            query += " and d.id = " + sampleTestInstance.id.toString()

        query += " order by 1) aa"


        def data = sql.rows(query)
        def finalList = []
        SampleBarcodeView sampleBarcodeView
        data.each {
            sampleBarcodeView = new SampleBarcodeView()
            sampleBarcodeView.sampleCode = it["SAMPLE_CODE"]
            sampleBarcodeView.sampleId = it["SAMPLE_ID"] as long
            sampleBarcodeView.medicalCenterId = it["MEDICAL_CENTER_ID"] as long
            sampleBarcodeView.arName = it["AR_NAME"]
            sampleBarcodeView.barcode = it["BARCODE"]
            sampleBarcodeView.sampleTypeName = it["SAMPLE_TYPE_NAME"]
            sampleBarcodeView.machineName = it["MACHINE_NAME"]
            sampleBarcodeView.sampleDate = DateUtils.parseDate(it["SAMPLE_DATE"])
            finalList << sampleBarcodeView
            sampleBarcodeView = null
        }
        return finalList
    }


    def instituteResultSheetData(GrailsParameterMap params) {

        return instituteResultSheetLoad(params)
    }

    def instituteResultSheetLoad(GrailsParameterMap params) {
        def fromDate = DateUtils.parseDate(params["fromDate"] as String)
        def toDate = DateUtils.parseDate(params["toDate"] as String)
        Long institutesId = params.long('institutesId')

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select rownum,a.sample_code, a.id as sample_id, a.branch_id, a.test_time,  to_char(a.sample_date, 'yyyy-MM-dd hh24:mi')   as sample_date ,  (select nvl(max(st2.ACCEPTED_DATE),sysdate ) from sample_test st2 where st2.sample_id=a.id) as ACCEPTED_DATE , \n" +
                "b.patient_code,  b.ar_name as patient_name,b.MOBILE as phone, b.gender,a.age as age, a.age_unit ,nvl(a.doctor_id,-1) as doctor_id, nvl(d.ar_name,'No Referring Doctor') as doctor_name,\n" +
                "s.test_type_id, t.name as test_name, t.lab_section_id, l.name as lab_section_name, t.sample_type_id, st.name as sample_type_name,\n" +
                "sc.id as category_id, sc.name as category_name, nvl(sc.parent_category_id,sc.id) as parent_cat_id,\n" +
                "ant.antibiotics_id, acs.description as antibiotics_name,t.is_require_doctor_approval as is_require_doctor_approval,\n" +
                "GET_SLICE_SI( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as SI_UNIT,\n" +
                "GET_SLICE_CONV( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as CONV_UNIT,\n" +
                "(select count(sss.test_type_id) from sample_test sss where sss.sample_id=a.id) as tests_count,\n" +
                "SAMPLE_TYPE_FN(a.id) as sample_type_all,\n" +
                "get_si_result(a.id,sc.id) as SI_RESULT,\n" +
                "get_conv_result(a.id,sc.id,ant.antibiotics_id) as CONV_RESULT,\n" +
                "get_anti_result(a.id,sc.id,ant.antibiotics_id) as ANTIBIOTIC_RESULT,\n" +
                "get_prev1 ( t.id, b.id,a.id) As prev_date_1,\n" +
                "get_prev2 ( t.id, b.id,a.id) As prev_date_2,\n" +
                "get_result_prev1 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_1,\n" +
                "get_result_prev2 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_2,\n" +
                "t.is_require_doctor_approval as IS_REQUIRE_DOCTOR_APPROVAL, \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 1),'') as SIGN_DESC1,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 1),'') as SIGN_PHOTO1 , \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 2),'') as SIGN_DESC2,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 2),'') as SIGN_PHOTO2 ,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_desc(a.id, s.id, 3),'') as SIGN_DESC3,  \n" +
                "decode(t.is_require_doctor_approval,1,sign_photo(a.id, s.id, 3),'') as SIGN_PHOTO3 , \n" +
                " get_last_active_report_note() as REPORT_NOTE " +
                " from SAMPLE a , PATIENT b, DOCTOR d, sample_test s,\n" +
                " test_type t, lab_section l, sample_type st, sample_category sc,\n" +
                " sample_category_antibiotics ant, antibiotics acs, institutes ins \n" +
                "where ins.id=a.institutes_id\n" +
                "and a.patient_id=b.id\n" +
                "and a.doctor_id=d.id(+)\n" +
                "and a.id=s.sample_id\n" +
                "and s.system_file_name is null\n" +
                "and s.test_type_id=t.id\n" +
                "and t.lab_section_id=l.id\n" +
                "and t.sample_type_id=st.id\n" +
                "and t.id=sc.test_type_id\n" +
                "and sc.is_measurable = 1\n" +
                "and sc.id=ant.sample_category_antibiotics_id(+)\n" +
                "and ant.antibiotics_id=acs.id(+)\n" +
                "and a.sample_status='COMPLETED'\n" +
                "and (\n" +
                "    (\n" +
                "        get_si_result(a.id,sc.id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_si_result(a.id,sc.id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        get_conv_result(a.id,sc.id,ant.antibiotics_id) is not null\n" +
                "        and DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) > 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('x')), 1) <> 0\n" +
                "        and NVL(DBMS_LOB.COMPARE(get_conv_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('X')), 1) <> 0\n" +
                "    )\n" +
                "    OR\n" +
                "    (\n" +
                "        (get_si_result(a.id,sc.id) is null OR DBMS_LOB.GETLENGTH(get_si_result(a.id,sc.id)) = 0)\n" +
                "        and\n" +
                "        (get_conv_result(a.id,sc.id,ant.antibiotics_id) is null OR DBMS_LOB.GETLENGTH(get_conv_result(a.id,sc.id,ant.antibiotics_id)) = 0)\n" +
                "    )\n" +
                ") \n"
        if (institutesId)
            query += " and a.institutes_id  = " + institutesId
        if (fromDate)
            query += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate)
            query += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        query += " order by a.id,\n" +
                "          l.id,\n" +
                "          t.id,\n" +
                "          sc.order_number,\n" +
                "          case\n" +
                "              when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Sensitive')) = 0 then 2\n" +
                "              when DBMS_LOB.COMPARE(get_anti_result(a.id,sc.id,ant.antibiotics_id), TO_NCLOB('Resist')) = 0 then 1\n" +
                "              else 0\n" +
                "          end desc "

        def data = sql.rows(query)
        def finalList = []
        SampleTestCompletedView sampleTestCompletedView

        data.each {
            sampleTestCompletedView = new SampleTestCompletedView()
            sampleTestCompletedView.sampleCode = it["SAMPLE_CODE"]
            sampleTestCompletedView.sampleId = it["SAMPLE_ID"] as long
            sampleTestCompletedView.branchId = it["BRANCH_ID"] as long
            sampleTestCompletedView.resultDate = Date.parse("yyyy-MM-dd", it["ACCEPTED_DATE"]?.toString())
            sampleTestCompletedView.testTime = Date.parse("yyyy-MM-dd HH:mm", it["ACCEPTED_DATE"]?.toString()).format('HH:mm')
            sampleTestCompletedView.sampleDate = Date.parse("yyyy-MM-dd HH:mm", it["sample_date"]?.toString())
            sampleTestCompletedView.patientCode = it["PATIENT_CODE"]
            sampleTestCompletedView.patientName = it["PATIENT_NAME"]
            sampleTestCompletedView.phone = it["PHONE"]
            sampleTestCompletedView.gender = it["GENDER"]
            sampleTestCompletedView.age = it["AGE"] as int
            sampleTestCompletedView.doctorId = it["DOCTOR_ID"] as long
            sampleTestCompletedView.doctorName = it["DOCTOR_NAME"]
            sampleTestCompletedView.testTypeId = it["TEST_TYPE_ID"] as long
            sampleTestCompletedView.testName = it["TEST_NAME"]
            sampleTestCompletedView.labSectionId = it["LAB_SECTION_ID"] as long
            sampleTestCompletedView.labSectionName = it["LAB_SECTION_NAME"]
            sampleTestCompletedView.sampleTypeId = it["SAMPLE_TYPE_ID"] as long
            sampleTestCompletedView.sampleTypeName = it["SAMPLE_TYPE_NAME"]
            sampleTestCompletedView.categoryId = it["CATEGORY_ID"] as long
            sampleTestCompletedView.categoryName = it["CATEGORY_NAME"]
            sampleTestCompletedView.parentCatId = it["PARENT_CAT_ID"]
            sampleTestCompletedView.antibioticsId = it["ANTIBIOTICS_ID"] as long
            sampleTestCompletedView.antibioticsName = it["ANTIBIOTICS_NAME"]
            sampleTestCompletedView.siUnit = lobToString(it["SI_UNIT"])
            sampleTestCompletedView.convUnit = lobToString(it["CONV_UNIT"])
            sampleTestCompletedView.testsCount = it["TESTS_COUNT"] as int
            sampleTestCompletedView.sampleTypeAll = lobToString(it["SAMPLE_TYPE_ALL"])
            sampleTestCompletedView.ageUnit = it["AGE_UNIT"]

            sampleTestCompletedView.siResult = lobToString(it["SI_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.convResult = lobToString(it["CONV_RESULT"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.antibioticResult = lobToString(it["ANTIBIOTIC_RESULT"])?.replaceAll("<nl>", "\n")

            sampleTestCompletedView.prevDate1 = lobToString(it["PREV_DATE_1"])
            sampleTestCompletedView.prevDate2 = lobToString(it["PREV_DATE_2"])
            sampleTestCompletedView.prevResult1 = lobToString(it["PREV_RESULT_1"])?.replaceAll("<nl>", "\n")
            sampleTestCompletedView.prevResult2 = lobToString(it["PREV_RESULT_2"])?.replaceAll("<nl>", "\n")

            sampleTestCompletedView.isRequireDoctorApproval = it["IS_REQUIRE_DOCTOR_APPROVAL"]
            sampleTestCompletedView.reportNote = lobToString(it["REPORT_NOTE"])
            sampleTestCompletedView.signDesc1 = lobToString(it["SIGN_DESC1"])
            Blob blob = it["SIGN_PHOTO1"]
            if (blob && blob?.length() > 0) {
                int blobLength = (int) blob.length();
                byte[] blobAsBytes = blob.getBytes(1, blobLength);
                sampleTestCompletedView.signPhoto1 = blobAsBytes
                blob.free();
            }
            sampleTestCompletedView.signDesc2 = lobToString(it["SIGN_DESC2"])
            Blob blob2 = it["SIGN_PHOTO2"]
            if (blob2 && blob2?.length() > 0) {
                int blobLength2 = (int) blob2.length();
                byte[] blobAsBytes2 = blob2.getBytes(1, blobLength2);
                sampleTestCompletedView.signPhoto2 = blobAsBytes2
                blob2.free();
            }
            sampleTestCompletedView.signDesc3 = lobToString(it["SIGN_DESC3"])
            Blob blob3 = it["SIGN_PHOTO3"]
            if (blob3 && blob3?.length() > 0) {
                int blobLength3 = (int) blob3.length();
                byte[] blobAsBytes3 = blob3.getBytes(1, blobLength3);
                sampleTestCompletedView.signPhoto3 = blobAsBytes3
                blob3.free();
            }
            finalList << sampleTestCompletedView
            sampleTestCompletedView = null
        }
        return finalList
    }


    def searchSamples(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"] ?: '-Q'
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long doctorId = params.long("doctor.id")
        def branchId = params.list("branchId")
        String medicalCenterId = params.medicalCenterId
        String subQuery = ''
        String doctorString = ''

        if (doctorId) doctorString = " and d.id = ${doctorId} "
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchId) subQuery += " and a.BRANCH_ID in (${branchId.toString().replace('[', '').replace(']', '')}) "

        String query = """
                       select b.MEDICAL_CENTER_CODE as branch,d.id,d.ar_name as doctorName,b.period,sample_count from doctor d,
                        (SELECT c.MEDICAL_CENTER_CODE, to_char(a.sample_date, 'YYYY${periodType}') as period,a.doctor_id,count(*) as sample_count
                        FROM sample a 
                         left outer join medical_center c on a.BRANCH_ID = c.ID
                        where 1=1
                         ${subQuery}

                        group by a.doctor_id,to_char(a.sample_date, 'YYYY${periodType}'),c.MEDICAL_CENTER_CODE
                        order by a.doctor_id
                        )b
                        where
                        d.id=b.doctor_id
                        ${doctorString}

                   """


        def data = sql.rows(query)
        def finalList = []
        SampleView sampleView
        data.each {
            sampleView = new SampleView()
            sampleView.period = it["period"]
            sampleView.doctorName = it["doctorName"]
            sampleView.branchName = it["branch"]
            sampleView.samplesCount = it["sample_count"] as Long
            finalList << sampleView
            sampleView = null
        }
        return finalList


    }

    def doctorSupervisorPercentageSearch(GrailsParameterMap params) {


        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long doctorId = params.long("doctor.id")
        String medicalCenterId = params.medicalCenterId


        String subQuery = ''
        if (fromDate && toDate) {
            subQuery = "  and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        }

        String query = """
                     select * from(SELECT rownum AS rnum, s.sample_code, p.ar_name, s.sample_date, t.name,
                        (a.price  - (a.price*round((nvl(s.discount,0)+ nvl(s.extra_discount_amount,0))/ decode(s.subtotal,0,1,s.subtotal),2))) as price ,
                        (SELECT  nvl(a.percentage,0) FROM doctor_supervisor a where id=${doctorId}) as dr_perct
                        FROM sample_test a, sample s , patient p, test_type t
                        where a.sample_id=s.id
                        and s.patient_id=p.id
                        and a.test_type_id=t.id
                        and s.medical_center_id =${medicalCenterId}
                       ${subQuery}
                        and s.sample_status <> 'CANCELED'
                        and s.sample_status <> 'CREATED'
                        and a.to_branch_id in (SELECT   a.branch_id
                        FROM join_doctor_branch a
                        where doctor_supervisor_id=${doctorId})
                        and t.lab_section_id in (SELECT   a.lab_section_id
                        FROM join_doctor_lab_section a
                        where doctor_supervisor_id=${doctorId})
                        and  rownum <= ${params.long('max') + params.long('offset')}
                        order by  s.sample_date
                       )
                       where rnum > ${params.long('offset')}
                   """
        def data = sql.rows(query)
        def dataList = []
        def resultMap = [:]
        Float totalPrice = 0.0
        Float afterVAT = 0.0
        DoctorSupervisorView doctorSupervisorView
        data.each {
            doctorSupervisorView = new DoctorSupervisorView()
            doctorSupervisorView.sampleCode = it["sample_code"]
            doctorSupervisorView.arabicName = it["ar_name"]
            doctorSupervisorView.sampleDate = DateUtils.parseDate(it["sample_date"] as String)
            doctorSupervisorView.name = it["name"]
            doctorSupervisorView.price = it["price"] ?: 0.0
            dataList << doctorSupervisorView
            doctorSupervisorView = null

        }
        PagedResultList pagedResultList = new PagedResultList(dataList, countDoctorSupervisor(params) as int)
        return pagedResultList


    }

    def countDoctorSupervisor(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long doctorId = params.long("doctor.id")
        String subQuery = ''
        if (fromDate && toDate) {
            subQuery = "  and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        }
        String query = """ select count(1) as countNum from (
                      SELECT  s.sample_code, p.ar_name, s.sample_date, t.name,
                        (a.price  - (a.price*round((nvl(s.discount,0)+ nvl(s.extra_discount_amount,0))/ decode(s.subtotal,0,1,s.subtotal),2))) as price ,
                        (SELECT  nvl(a.percentage,0) FROM doctor_supervisor a where id=${doctorId}) as dr_perct
                        FROM sample_test a, sample s , patient p, test_type t
                        where a.sample_id=s.id
                        and s.patient_id=p.id
                        and a.test_type_id=t.id
                       ${subQuery}
                        and s.sample_status <> 'CANCELED'
                        and s.sample_status <> 'CREATED'
                        and a.to_branch_id in (SELECT   a.branch_id
                        FROM join_doctor_branch a
                        where doctor_supervisor_id=${doctorId})
                        and t.lab_section_id in (SELECT   a.lab_section_id
                        FROM join_doctor_lab_section a
                        where doctor_supervisor_id=${doctorId})
                        )
                  """
        def data = sql.rows(query)
        return data["countNum"][0] as int
    }


    def doctorSupervisorPercentageReport(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long doctorId = params.long("doctor.id")

        String subQuery = ''
        if (fromDate && toDate) {
            subQuery = "  and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        }
        String query = """
                      SELECT  s.sample_code, p.ar_name, s.sample_date, t.name,
                        (a.price  - (a.price*round((nvl(s.discount,0)+ nvl(s.extra_discount_amount,0))/ decode(s.subtotal,0,1,s.subtotal),2))) as price ,
                        (SELECT  nvl(a.percentage,0) FROM doctor_supervisor a where id=${doctorId}) as dr_perct
                        FROM sample_test a, sample s , patient p, test_type t
                        where a.sample_id=s.id
                        and s.patient_id=p.id
                        and a.test_type_id=t.id
                        and s.medical_center_id =${params.medicalCenterId}

                       ${subQuery}
                        and s.sample_status <> 'CANCELED'
                        and s.sample_status <> 'CREATED'
                        and a.to_branch_id in (SELECT   a.branch_id
                        FROM join_doctor_branch a
                        where doctor_supervisor_id=${doctorId})
                        and t.lab_section_id in (SELECT   a.lab_section_id
                        FROM join_doctor_lab_section a
                        where doctor_supervisor_id=${doctorId})
                        order by  s.sample_date

                   """

        def data = sql.rows(query)
        def dataList = []
        def resultMap = [:]
        Float totalPrice = 0.0
        Float afterVAT = 0.0
        DoctorSupervisorView doctorSupervisorView
        data.each {
            doctorSupervisorView = new DoctorSupervisorView()
            doctorSupervisorView.sampleCode = it["sample_code"]
            doctorSupervisorView.arabicName = it["ar_name"]
            doctorSupervisorView.sampleDate = DateUtils.parseDate(it["sample_date"] as String)
            doctorSupervisorView.name = it["name"]
            doctorSupervisorView.price = it["price"] ?: 0.0
            dataList << doctorSupervisorView
            doctorSupervisorView = null

            if (it["price"]) {
                totalPrice += it["price"]
            }
        }
        DoctorSupervisor doctorSupervisor = DoctorSupervisor.get(doctorId)
        afterVAT = (totalPrice / 1.16)
        resultMap.testList = dataList
        resultMap.totalPrice = Math.round(totalPrice)
        resultMap.VAT = Math.round(totalPrice - (totalPrice / 1.16))
        resultMap.afterVAT = Math.round(afterVAT)
        resultMap.doctorPart = doctorSupervisor ? Math.round(((afterVAT) * ((doctorSupervisor?.percentage ?: 0) * (1 / 100)))) : 0.0
        resultMap.doctorName = doctorSupervisor ? doctorSupervisor?.arName : ''

        return resultMap
    }


    def innerTransferReport(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long branchId = params.long("branchId")
        String dateBetween = ''
        if (fromDate && toDate) dateBetween += " and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "

        String query = """
                  select Branch,m.medical_center_code, sum(tests) tests,sum(charge) charge,sum(loss) loss,sum(tests2) tests2,sum(charge2) charge2,sum(gain2) gain2
                    from (
                    select branch_id as Branch, count(1) as tests, round(sum(charge),2) as charge, round(sum(loss),2) as loss,0 as tests2,0 as charge2,0 as gain2
                    from (
                    SELECT  s.branch_id,   a.to_branch_id
                    , round((s.discount+ s.extra_discount_amount)/ s.subtotal,2) as discount_perct,
                      (a.price  - (a.price*round((s.discount+ s.extra_discount_amount)/ decode(s.subtotal,0,1,s.subtotal),2))) as charge,
                      (a.price  - (a.price*round((s.discount+ s.extra_discount_amount)/ decode(s.subtotal,0,1,s.subtotal),2)))*(select  nvl(transfer_percentage,0)/100
                      FROM medical_center b where b.id=s.branch_id) as loss
                     FROM sample_test a, sample s
                      where a.sample_id=s.id
                      and  a.to_branch_id<>s.branch_id
                        and  s.branch_id not in (select mc.id from medical_center mc where nvl(mc.IS_EXTERNAL_CENTER,0)='1')
                        and  a.to_branch_id not in (select mc.id from medical_center mc where nvl(mc.IS_EXTERNAL_CENTER,0)='1')
                    ${dateBetween}
                     and a.test_type_status <> 'REJECTED' ) from_branch
                     group by branch_id
                     union
                     select to_branch_id,0,0,0,   count(1) as tests, round(sum(charge),2) as charge2, round(sum(loss),2) as Gain
                    from (
                    SELECT  s.branch_id,   a.to_branch_id
                    , round((s.discount+ s.extra_discount_amount)/ s.subtotal,2) as discount_perct,
                      (a.price  - (a.price*round((s.discount+ s.extra_discount_amount)/ decode(s.subtotal,0,1,s.subtotal),2))) as charge,
                      (a.price  - (a.price*round((s.discount+ s.extra_discount_amount)/ decode(s.subtotal,0,1,s.subtotal),2)))*(select  nvl(transfer_percentage,0)/100
                      FROM medical_center b where b.id=a.to_branch_id) as loss
                     FROM sample_test a, sample s
                      where a.sample_id=s.id
                      and  a.to_branch_id<>s.branch_id
                        and  s.branch_id not in (select mc.id from medical_center mc where nvl(mc.IS_EXTERNAL_CENTER,0)='1')
                        and  a.to_branch_id not in (select mc.id from medical_center mc where nvl(mc.IS_EXTERNAL_CENTER,0)='1')
                    ${dateBetween}
                     and a.test_type_status <> 'REJECTED' ) from_branch
                     group by to_branch_id) all_rows , medical_center m
                     where all_rows.Branch=m.id and nvl(m.IS_EXTERNAL_CENTER,0)<>'1'
                     group by Branch,medical_center_code
                     order by 1


                   """


        def data = sql.rows(query)
        def finalList = []
        LinkedHashMap resultMap = [:]
        InnerTransferView innerTransferView = new InnerTransferView()
        data?.each {
            innerTransferView = new InnerTransferView()
            innerTransferView.branch = it["branch"]
            innerTransferView.medicalCenterCode = it["medical_center_code"]
            innerTransferView.tests = it["tests"] ?: 0
            innerTransferView.charge = it["charge"] ?: 0.0
            innerTransferView.loss = it["loss"] ?: 0.0
            innerTransferView.tests2 = it["tests2"] ?: 0
            innerTransferView.charge2 = it["charge2"] ?: 0.0
            innerTransferView.gain = it["gain2"] ?: 0.0
            innerTransferView.balance = (it["gain2"] ?: 0) - (it["loss"] ?: 0)
            finalList << innerTransferView
            innerTransferView = null
        }

        resultMap.transferList = finalList
        return resultMap

    }

    def howYouKnowAboutUsReport(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long medicalCenterId = params.long("medicalCenterId")
        String dateBetween = ''
        if (fromDate && toDate) dateBetween += " and h.DATE_CREATED between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "

        String query = """
                 
                   SELECT  count(id) as countRow , ANSWER from HOW_YOU_KNOW_ABOUTMC h WHERE h.MEDICAL_CENTER_ID = ${medicalCenterId} $dateBetween GROUP BY  ANSWER

                   """
        def data = sql.rows(query)

        def finalList = []
        LinkedHashMap resultMap = [:]

        data?.each {
            resultMap.count = it["countRow"]
            resultMap.answer = messageSource.getMessage("HowYouKnowMCAnswer." + it["ANSWER"], [] as Object[], " ", Locale.getDefault()) + " "

            finalList << resultMap
            resultMap = [:]
        }

        return finalList

    }


    def transferredTestsSearch(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long fromBranchId = params.long("fromBranchId")
        Long toBranchId = params.long("toBranchId")
        def testTypeIds = params.list("testTypeId")

        String subQuery = ''
        if (fromDate && toDate) subQuery += " and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        if (fromBranchId) subQuery += "  and a.from_medical_center_id=${fromBranchId}   "
        if (toBranchId) subQuery += "  and a.to_medical_center_id=${toBranchId} "
        if (testTypeIds) subQuery += " and y.id in ( ${testTypeIds.toString().replace('[', '').replace(']', '')} ) "

        String query = """

                       SELECT rownum as seq,s.sample_code, p.ar_name, s.age||' '|| s.age_unit  as patient_age,
                          p.gender, y.name
                           FROM transfer_package a , transfer_package_status b , transfer_package_sample_test c,
                           sample_test t , sample s , test_type y, patient p
                           where a.current_status_id=b.id
                           and a.id=c.trnsfr_pckg_smpl_tst_id
                           and c.sample_test_id=t.id
                           and t.sample_id=s.id
                           and t.test_type_id=y.id
                           and s.patient_id=p.id
                           ${subQuery}
                           and b.package_status<>'REJECTED'
                           and b.package_status<>'CREATED'
                           and s.sample_status<>'CANCELED'
                          order by seq,trunc(a.date_created), s.sample_code

                   """

        def data = sql.rows(query)
        def finalList = []
        LinkedHashMap resultMap = [:]
        TransferredTestsView transferredTestsView = new TransferredTestsView()
        data?.each {
            transferredTestsView = new TransferredTestsView()
            transferredTestsView.seq = it["seq"]
            transferredTestsView.arName = it["ar_name"]
            transferredTestsView.sampleCode = it["sample_code"]
            transferredTestsView.patientAge = it["patient_age"]
            transferredTestsView.gender = it["gender"]
            transferredTestsView.testTypeName = it["name"]
            finalList << transferredTestsView
            transferredTestsView = null
        }

        resultMap.transferTestsList = finalList
        return resultMap

    }

    def transferredTestsViewSearch(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long fromBranchId = params.long("fromBranchId")
        Long toBranchId = params.long("toBranchId")
        def testTypeIds = params.list("testTypeId")


        String subQuery = ''
        if (fromDate && toDate) subQuery += " and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        if (fromBranchId) subQuery += "  and a.from_medical_center_id=${fromBranchId}   "
        if (toBranchId) subQuery += "  and a.to_medical_center_id=${toBranchId}   "
        if (testTypeIds) subQuery += " and y.id in ( ${testTypeIds.toString().replace('[', '').replace(']', '')} ) "


        String query = """
                    select * from(
                       SELECT rownum as seq,s.sample_code, p.ar_name, s.age||' '|| s.age_unit  as patient_age,
                          p.gender, y.name
                           FROM transfer_package a , transfer_package_status b , transfer_package_sample_test c,
                           sample_test t , sample s , test_type y, patient p
                           where a.current_status_id=b.id
                           and a.id=c.trnsfr_pckg_smpl_tst_id
                           and c.sample_test_id=t.id
                           and t.sample_id=s.id
                           and t.test_type_id=y.id
                           and s.patient_id=p.id
                           ${subQuery}
                           and b.package_status<>'REJECTED'
                           and b.package_status<>'CREATED'
                           and s.sample_status<>'CANCELED'
                           and  rownum <= ${params.long('max') + params.long('offset')}
                           order by trunc(a.date_created), s.sample_code

                       )
                       where seq > ${params.long('offset')}

                   """


        def data = sql.rows(query)
        def finalList = []
        TransferredTestsView transferredTestsView = new TransferredTestsView()
        data?.each {
            transferredTestsView = new TransferredTestsView()
            transferredTestsView.seq = it["seq"]
            transferredTestsView.arName = it["ar_name"]
            transferredTestsView.sampleCode = it["sample_code"]
            transferredTestsView.patientAge = it["patient_age"]
            transferredTestsView.gender = it["gender"]
            transferredTestsView.testTypeName = it["name"]
            finalList << transferredTestsView
            transferredTestsView = null
        }

        PagedResultList pagedResultList = new PagedResultList(finalList, countTransferredTestsView(params) as int)
        return pagedResultList

    }

    def countTransferredTestsView(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long fromBranchId = params.long("fromBranchId")
        Long toBranchId = params.long("toBranchId")
        def testTypeIds = params.list("testTypeId")
        String subQuery = ''
        if (fromDate && toDate) subQuery += " and s.sample_date between '${fromDate.format("dd-MMM-yyyy")}' and '${toDate.format("dd-MMM-yyyy")}' "
        if (fromBranchId) subQuery += "  and a.from_medical_center_id=${fromBranchId}  "
        if (toBranchId) subQuery += "  and a.to_medical_center_id=${toBranchId}   "
        if (testTypeIds) subQuery += " and y.id in ( ${testTypeIds.toString().replace('[', '').replace(']', '')} ) "

        String query = """
                  select count(1) as countNum from (
                       SELECT rownum as seq,s.sample_code, p.ar_name, s.age||' '|| s.age_unit  as patient_age,
                          p.gender, y.name
                           FROM transfer_package a , transfer_package_status b , transfer_package_sample_test c,
                           sample_test t , sample s , test_type y, patient p
                           where a.current_status_id=b.id
                           and a.id=c.trnsfr_pckg_smpl_tst_id
                           and c.sample_test_id=t.id
                           and t.sample_id=s.id
                           and t.test_type_id=y.id
                           and s.patient_id=p.id
                           ${subQuery}
                           and b.package_status<>'REJECTED'
                           and b.package_status<>'CREATED'
                           and s.sample_status<>'CANCELED'

                       )


                   """
        def data = sql.rows(query)
        return data["countNum"][0] as int

    }

    def searchDoctorSamplesExcel(GrailsParameterMap params, ArrayList period) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"] ?: '-Q'
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long doctorId = params.long("doctor.id")
        def branchId = params.list("branchId")
        String medicalCenterId = params.medicalCenterId

        String subQuery = ''
        String doctorString = ''
        if (doctorId) doctorString = " and d.id = ${doctorId} "
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchId) subQuery += " and a.BRANCH_ID in ( ${branchId.toString().replace('[', '').replace(']', '')} ) "

        String selected = ''
        period.each {
            if (!it.equals('doctor_Name') && !it.equals('id')) {
                String key = "\"" + it + "\"";
                selected += " sum(decode(b.period,${it},b.sample_count,0)) as ${key} , "
            }

        }

        String query = """
                        SELECT b.medical_center_code as Branch ,case grouping(AR_NAME) when 1 then 'Total'
                       else AR_NAME end AS DOCTOR_NAME,
                          ${selected}
                          sum(sample_count) as "Total"
                         from doctor d,
                        (SELECT c.medical_center_code  , to_char(a.sample_date, 'YYYY${periodType}') as period,a.doctor_id,count(*) as sample_count
                        FROM sample a
                        left outer join medical_center c on c.id=a.branch_id
                         where 1=1
                         ${subQuery}

                        group by a.doctor_id,to_char(a.sample_date, 'YYYY${periodType}'),c.medical_center_code 
                        order by a.doctor_id
                        )b
                        where
                        d.id=b.doctor_id
                        ${doctorString}
                         group by rollup(AR_NAME), b.medical_center_code

                   """

//        print( query )
        def data = sql.rows(query)
        return data


    }


    def searchInstitutesSamples(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long instituteId = params.long("institutes.id")
        def branchId = params.list("branchId")
        String medicalCenterId = params.medicalCenterId
        String instituteWhere = ''

        String subQuery = " "
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (instituteId) instituteWhere = " and ins.id = ${instituteId} "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchId) subQuery += " and a.BRANCH_ID in (${branchId.toString().replace('[', '').replace(']', '')}) "

//        String query = """
//                       select ins.id,ins.ar_name as institutes_name ,b.period,sample_count from institutes ins,
//                      (SELECT to_char(a.sample_date, 'YYYY${periodType}') as period,a.institutes_id,count(*) as sample_count
//                            FROM sample a
//                       where a.institutes_id is not null
//                           ${subQuery}
//                       group by a.institutes_id,to_char(a.sample_date, 'YYYY${periodType}')
//                       order by a.institutes_id)b
//                      where
//                      ins.id= b.institutes_id
//                       ${instituteWhere}
//
//                   """


        String query = """
                       select b.branch_id,ins.id,ins.ar_name as institutes_name ,b.period,sample_count from institutes ins,
                      (SELECT a.branch_id,to_char(a.sample_date, 'YYYY${periodType}') as period,a.institutes_id,count(*) as sample_count
                            FROM sample a
                       where a.institutes_id is not null
                           ${subQuery}
                       group by a.institutes_id,a.branch_id,to_char(a.sample_date, 'YYYY${periodType}')
                       order by a.institutes_id)b
                      where
                      ins.id= b.institutes_id
                       ${instituteWhere}

                   """

        def data = sql.rows(query)
        def finalList = []
        SampleView sampleView
        data.each {
            sampleView = new SampleView()
            sampleView.id = it["id"]
            sampleView.period = it["period"]
            sampleView.instituteName = it["institutes_name"]
            sampleView.branchName = MedicalCenter.findById(it["branch_id"] as Long)?.arName
            sampleView.samplesCount = it["sample_count"] as Long
            finalList << sampleView
            sampleView = null
        }
        return finalList


    }


    def searchInstitutesSamplesExcel(GrailsParameterMap params, ArrayList period) {


        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long instituteId = params.long("institutes.id")
        def branchId = params.list("branchId")
        String medicalCenterId = params.medicalCenterId

        String subQuery = ''
        String instituteWhere = ''
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (instituteId) instituteWhere = " and id = ${instituteId} "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchId) subQuery += " and a.BRANCH_ID in (${branchId.toString().replace('[', '').replace(']', '')}) "


        String selected = ''
        period.each {
            if (!it.equals('institute_Name') && !it.equals('id')) {
                String key = "\"" + it + "\"";
                selected += " sum(decode(b.period,${it},b.sample_count,0)) as ${key} , "
            }

        }
        String query = """

                SELECT branch,case grouping(AR_NAME) when 1 then 'Total'
                       else AR_NAME end AS institute_Name,
                  ${selected}
                  sum(sample_count) as "Total"

                 from institutes ins,
                  (SELECT b.medical_center_code as branch, to_char(a.sample_date, 'YYYY${periodType}') as period,a.institutes_id,count(*) as sample_count
                   FROM sample a
                     join medical_center b on a.branch_id = b.id
                   where a.institutes_id is not null
                     ${subQuery}
                   group by a.institutes_id,to_char(a.sample_date, 'YYYY${periodType}'),b.medical_center_code
                   order by a.institutes_id)b
                where
                  ins.id= b.institutes_id
                   ${instituteWhere}
                 group by rollup(AR_NAME),branch

        """


        def data = sql.rows(query)
        return data


    }


    def searchTestsSamples(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        def testTypeIds = params.list("testTypeId")
        def labSectionIds = params.list("labSectionId")
        def branchIds = params.list("branch.id")
        def workingBranchId = params.long("workingBranchId")
        def doctorId = params.long("doctor.id")
        String medicalCenterId = params.medicalCenterId


        String subQuery = ''
        String testNameWhere = ''
        if (testTypeIds) testNameWhere = " and tt.id in ( ${testTypeIds.toString().replace('[', '').replace(']', '')} ) "
        if (medicalCenterId) {
            (subQuery += " a.MEDICAL_CENTER_ID = ${params.medicalCenterId} ")
        } else {
            subQuery += " a.MEDICAL_CENTER_ID is null } "
        }
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchIds) subQuery += " and a.BRANCH_ID in ( ${branchIds.toString().replace('[', '').replace(']', '')} )  "
        if (doctorId) subQuery += " and d.id = ${doctorId}"
        if (labSectionIds) testNameWhere += " and tt.lab_section_id in (${labSectionIds.join(',')})   "

        if (workingBranchId) {
            testNameWhere += " and sst.to_branch_id = ${workingBranchId}"
        }

        String query = """
                     select b.branch_id , st.id,tt.NAME as test_name ,b.period,sample_count from SAMPLE_TEST st,
                       (SELECT a.branch_id , to_char(a.sample_date, 'YYYY${periodType}') as period,a.Id,count(*) as sample_count
                       FROM sample a left outer join doctor d on a.doctor_id = d.id
                       left outer join medical_center c on a.branch_id = c.id
                       where 
                       ${subQuery}
                       group by a.id,to_char(a.sample_date, 'YYYY${periodType}'),a.branch_id
                       order by a.id) b
                      ,TEST_TYPE  tt ,
                       SAMPLE_TEST sst
                      ,lab_section ls
                    where
                      st.SAMPLE_ID= b.id
                    and
                      st.TEST_TYPE_ID = tt.ID
                      and tt.lab_section_id = ls.id
                      and sst.test_type_id =  tt.id 
                      and sst.SAMPLE_ID = b.id
                      ${testNameWhere}
                  """


        def data = sql.rows(query)
        def finalList = []
        SampleView sampleView
        data.each {
            sampleView = new SampleView()
            sampleView.period = it["period"]
            sampleView.testTypeName = it["test_name"]
            sampleView.branchName = MedicalCenter.findById(it["branch_id"] as Long)?.arName
            sampleView.samplesCount = it["sample_count"] as Long
            finalList << sampleView
            sampleView = null
        }
        return finalList


    }


    def searchTestsSamplesExcel(GrailsParameterMap params, ArrayList period) {


        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
//        Long testTypeId = params.long("testType.id")
//        Long branchId = params.long("branchId")
        def testTypeIds = params.list("testTypeId")
        def labSectionIds = params.list("labSectionId")
        def branchIds = params.list("branch.id")
        Long doctorId = params.long("doctor.id")
        String medicalCenterId = params.medicalCenterId
        def toBranchId = params.workingBranchId

        String subQuery = ''
        String testNameWhere = ''
        if (testTypeIds) testNameWhere = " and tt.id in ( ${testTypeIds.toString().replace('[', '').replace(']', '')} ) "
        if (medicalCenterId) {
            (subQuery += " a.MEDICAL_CENTER_ID = $medicalCenterId ")
        } else {
            subQuery += " a.MEDICAL_CENTER_ID is null } "
        }
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchIds) subQuery += " and a.BRANCH_ID in (${branchIds.toString().replace('[', '').replace(']', '')}) "
        if (doctorId) subQuery += " and d.id = ${doctorId} "
        if (toBranchId) testNameWhere += " and sst.TO_BRANCH_ID = ${toBranchId}  "
        if (labSectionIds) testNameWhere += " and tt.lab_section_id in (${labSectionIds.join(',')})   "

        String selected = ''
        period.each {
            if (!it.equals('Test_Name') && !it.equals('id') && !it.equals("'-'")) {
                String key = "\"" + it + "\"";
                selected += " sum(decode(b.period,${it},b.sample_count,0)) as ${key} , "

            }
        }
        String query = """
                    SELECT b.medical_center_code as Branch,case grouping(NAME) when 1 then 'Total'
                       else NAME end AS Test_Name,
                          ${selected}
                          sum(sample_count) as "Total"

                         from SAMPLE_TEST st,
                       (SELECT c.medical_center_code ,to_char(a.sample_date, 'YYYY${periodType}') as period,a.Id,count(*) as sample_count

                       FROM sample a left outer join doctor d on a.doctor_id = d.id
                        left outer join medical_center c on a.branch_id = c.id
                         where 
                       ${subQuery}
                       group by a.id,to_char(a.sample_date, 'YYYY${periodType}'),c.medical_center_code
                       order by a.id) b
                      ,TEST_TYPE  tt,
                       SAMPLE_TEST sst
                      
                    where
                      st.SAMPLE_ID= b.id
                    and
                      st.TEST_TYPE_ID = tt.ID
                       and sst.test_type_id =  tt.id 
                      and sst.SAMPLE_ID = b.id
                       ${testNameWhere}

                      group by rollup(NAME),b.medical_center_code


                   """


        return sql.rows(query)

    }

    def searchBranchTestsSamples(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
//        Long branchId = params.long("branchId")
        def branchIds = params.list("branch.id")
        String medicalCenterId = params.medicalCenterId

        String subQuery = ''
        String branchIdWhere = ''
        if (branchIds) branchIdWhere = " and mc.id in( ${branchIds.toString().replace('[', '').replace(']', '')} )"
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "

        String query = """
                      select mc.EN_NAME as branch_name ,nvl(b.period,'-') as period,nvl(sample_count,0) as sample_count  FROM MEDICAL_CENTER mc ,
                      (SELECT to_char(a.sample_date, 'YYYY${periodType}') as period,a.BRANCH_ID ,count(*) as sample_count
                       FROM sample a LEFT OUTER JOIN  SAMPLE_TEST st ON st.SAMPLE_ID =a.ID where 1=1
                       ${subQuery}
                       group by a.BRANCH_ID,to_char(a.sample_date, 'YYYY${periodType}')
                       ) b
                    where
                       mc.ID= b.BRANCH_ID(+)
                        and mc.parent_id ='$medicalCenterId' and is_active='1'
                        ${branchIdWhere}


                   """
        def data = sql.rows(query)
        def finalList = []
        SampleView sampleView
        data.each {
            sampleView = new SampleView()
            sampleView.period = it["period"]
            sampleView.branchName = it["branch_name"]
            sampleView.samplesCount = it["sample_count"] as Long
            finalList << sampleView
            sampleView = null
        }
        return finalList


    }

    def searchBranchTestsSamplesExcel(GrailsParameterMap params, ArrayList period) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        def branchIds = params.list("branch.id")
        String medicalCenterId = params.medicalCenterId

        String subQuery = ''
        String branchIdWhere = ''
        if (branchIds) branchIdWhere = " and mc.id in( ${branchIds.toString().replace('[', '').replace(']', '')} )"
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "

        String selected = ''
        period.each {
            if (!it.equals('branch_Name') && !it.equals('id') && !it.equals("'-'")) {
                String key = "\"" + it + "\"";
                selected += " sum(decode(b.period,${it},b.sample_count,0)) as ${key} , "
            }
        }

        String query = """
                      SELECT case grouping(AR_NAME) when 1 then 'Total'
                       else AR_NAME end AS BRANCH_NAME,
                          ${selected}
                          sum(sample_count) as "Total"

                      FROM MEDICAL_CENTER mc ,
                      (SELECT to_char(a.sample_date, 'YYYY${periodType}') as period,a.BRANCH_ID ,count(*) as sample_count
                       FROM sample a LEFT OUTER JOIN  SAMPLE_TEST st ON st.SAMPLE_ID =a.ID where 1=1
                       ${subQuery}
                       group by a.BRANCH_ID,to_char(a.sample_date, 'YYYY${periodType}')
                       ) b
                    where
                       mc.ID= b.BRANCH_ID(+)
                        and mc.parent_id='$medicalCenterId' and is_active='1'
                        ${branchIdWhere}

                       group by rollup(AR_NAME)

                   """

        return sql.rows(query)


    }

    def searchBranchSamples(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long organizationId = params.long("institutes.id")
//        Long branchId = params.long("branchId")
        def branchIds = params.list("branchId")
        String medicalCenterId = params.medicalCenterId
        String subQuery = ''
        String branchIdWhere = ''
        if (branchIds) branchIdWhere = " and mc.id in ( ${branchIds.toString().replace('[', '').replace(']', '')} ) "
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (organizationId) subQuery = " and a.institutes_id = ${organizationId} "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "

        String query = """
                      select mc.EN_NAME as branch_name ,nvl(b.period,'*') as period,
                    nvl(sample_count,0) as sample_count  FROM MEDICAL_CENTER mc ,
                      (SELECT to_char(a.sample_date, 'YYYY${periodType}') as period,a.BRANCH_ID ,count(*) as sample_count
                       FROM sample a  where 1=1
                       ${subQuery}
                       group by a.BRANCH_ID,to_char(a.sample_date, 'YYYY${periodType}')
                       ) b
                    where
                       mc.ID= b.BRANCH_ID(+)  and
                        mc.PARENT_ID = '$medicalCenterId' and is_active='1'
                       ${branchIdWhere}
                   """

        def data = sql.rows(query)
        def finalList = []
        SampleView sampleView
        data.each {
            sampleView = new SampleView()
            sampleView.period = it["period"]
            sampleView.branchName = it["branch_name"]
            sampleView.samplesCount = it["sample_count"] as Long
            finalList << sampleView
            sampleView = null
        }
        return finalList

    }

    def searchBranchSamplesExcel(GrailsParameterMap params, ArrayList period) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String periodType = params["periodType"]
        if (periodType.equals('-YYYY')) periodType = ''

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long organizationId = params.long("institutes.id")
        def branchIds = params.list("branchId")
        String medicalCenterId = params.medicalCenterId

        String subQuery = ''
        String branchIdWhere = ''
        if (branchIds) branchIdWhere = " and mc.id in ( ${branchIds.toString().replace('[', '').replace(']', '')} ) "
        if (medicalCenterId) subQuery += " and a.medical_center_id  = $medicalCenterId "
        else subQuery += " and a.medical_center_id  is null "
        if (organizationId) subQuery = " and a.institutes_id = ${organizationId} "
        if (fromDate) subQuery += "  and a.sample_date >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and a.sample_date <= '${toDate.format("dd-MMM-yyyy")}' "

        String selected = ''
        period.each {
            if (!it.equals('branch_Name') && !it.equals('id') && !it.equals("'-'")) {
                String key = "\"" + it + "\"";
                selected += " sum(decode(b.period,${it},b.sample_count,0)) as ${key} , "

            }

        }
        String query = """
                    SELECT case grouping(AR_NAME) when 1 then 'Total'
                       else AR_NAME end AS BRANCH_NAME,
                          ${selected}
                          sum(sample_count) as "Total"
                     FROM MEDICAL_CENTER mc ,
                      (SELECT to_char(a.sample_date, 'YYYY${periodType}') as period,a.BRANCH_ID ,count(*) as sample_count
                       FROM sample a  where 1=1
                       ${subQuery}
                       group by a.BRANCH_ID,to_char(a.sample_date, 'YYYY${periodType}')
                       ) b
                    where
                        mc.ID= b.BRANCH_ID(+)  and
                         mc.PARENT_ID = '$medicalCenterId' and is_active='1'
                        ${branchIdWhere}
                       group by rollup(AR_NAME)
                   """

        return sql.rows(query)

    }


    def currentDate(Boolean format = false) {
        def date = new java.util.Date();
        if (!format) return Calendar.getInstance().getTime()
        else return new SimpleDateFormat("dd/MM/yyyy").format(Calendar.getInstance().getTime())
    }


    def getFirstDateOfMonth(Boolean format = false) {
        def date = new java.util.Date();
        if (!format) return new java.util.Date(date.getYear(), date.getMonth(), 1)
        else return new SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date(date.getYear(), date.getMonth(), 1));
    }

    def getLastDateOfMonth(Boolean format = false) {

        def date = new java.util.Date();
        if (!format) return new java.util.Date(date.getYear(), date.getMonth() + 1, 0)
        else return new SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date(date.getYear(), date.getMonth() + 1, 0));

    }

    def searchUserLogFile(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        Long medicalCenterId = params.long("medicalCenterId")

        String subQuery = ''

        if (fromDate) subQuery += "  and DATE_CREATED >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and DATE_CREATED <= '${toDate.format("dd-MMM-yyyy")}' "
        if (medicalCenterId) subQuery += " and Medical_Center_Id = ${medicalCenterId} "

        String query = """
                     select * from (SELECT rownum as serial  , user_name userName ,  to_char(date_created, 'dd/mm/yyyy hh24:mi:ss')  dateCreated, action_type actionType, ip_address ipAddress,host_name hostName

                     FROM USER_LOG_FILE

                    where 1=1

                       ${subQuery}
                    )

                   """


        return sql.rows(query)


    }


    def getCampaignList(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

//        Long campaignId = params.long("campaign.id")
        def campaignIds = params.list("campaign.id")
        Long medicalCenterId = params.long("medicalCenterId")
        def branchIds = params.list("branchId")
        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)


        // Convert campaign IDs to a string for the SQL IN clause
        String campaignIdsString = campaignIds.join(',')
        String subQuery = ''


        if (branchIds) {
            subQuery += " and a.branch_id in (${branchIds.join(',')}) "
        }

//        String query = """
//                    SELECT  a.branch_id  as  branchId, b.medical_center_code as medicalCenterCode,
//  sum(check_campaigns(a.id,SYS.ODCINumberList(${campaignIdsString}))) as Total
//                      FROM sample a   , medical_center b
//                      where a.branch_id=b.id
//                      and sample_date >=
//                      (SELECT   p.from_date
//                      FROM  campaign p
//                      where   ID in (${campaignIdsString}))
//                      and  sample_date <=
//                      (SELECT   p.to_date
//                      FROM   campaign p
//                      where   id in (${campaignIdsString}))
//                       and a.branch_id in
//                      (SELECT   p.medical_center_id
//                      FROM  campaign_medical_center p
//                      where p.campaign_medical_center_id  in (${campaignIdsString})  $subQuery )
//                      and sample_date between '${fromDate.format("dd-MMM-yy")}' and  '${toDate.format("dd-MMM-yy")}'
//
//                       and a.medical_center_id='${medicalCenterId}'   group by a.branch_id,b.medical_center_code     order by 1
//
//                      """


        String query = """ 
select branchId, medicalCenterCode, sum(chk) as Total
from (
SELECT  a.branch_id  as  branchId,
        b.medical_center_code as medicalCenterCode,
        a.sample_code, a.sample_date,
         check_multi_campaigns(a.id,SYS.ODCINumberList(${campaignIdsString}),a.sample_date, a.branch_id) chk
FROM sample a , medical_center b
where a.branch_id=b.id 
      ${subQuery}
      and sample_date between '${fromDate.format("dd-MMM-yy")}' and  '${toDate.format("dd-MMM-yy")}'
      and a.medical_center_id='1'
) vw
group by branchId, medicalCenterCode
"""


        return sql.rows(query)

    }


    def getCampaignList1(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

//        Long campaignId = params.long("campaign.id")
        def campaignIds = params.list("campaign.id")
        def branchIds = params.list("branchId")
        def fromDate = DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String)
        def toDate = DateUtils.parseDate(params["toDate"] + " 23:59:59" as String)
        String subQuery = ''

        if (campaignIds) {
            subQuery += " and c.id in (${campaignIds.join(',')})"
        }

        if (branchIds) {
            subQuery += " and a.branch_id in (${branchIds.join(',')}) "
        }


        String query = """

SELECT
  vw.branch_id AS branchId,
  vw.medical_center_code AS medicalCenterCode,
  vw.code,
  SUM(chk) AS Total
FROM (
       SELECT
         a.branch_id,
         b.medical_center_code,
         a.sample_code,
         a.sample_date,
         c.code,
         CHECK_multi_campaigns(a.id,SYS.ODCINumberList(c.id),a.sample_date, a.branch_id) AS chk
       FROM
         sample a
         left outer JOIN
         medical_center b ON a.branch_id = b.id
           left outer JOIN
           campaign_medical_center cmc ON a.branch_id = cmc.medical_center_id
           left outer JOIN
           campaign c ON cmc.campaign_medical_center_id = c.id
       WHERE
         1=1
         ${subQuery}
         AND a.sample_date BETWEEN '${fromDate.format("dd-MMM-yyyy")}'  AND '${toDate.format("dd-MMM-yyyy")}'
         AND a.medical_center_id = '1'
     ) vw
GROUP BY
  vw.branch_id,
  vw.code,
  vw.medical_center_code
  order by 
  vw.branch_id,
  vw.code,
  vw.medical_center_code
"""


        return sql.rows(query)

    }


    PagedResultList searchPriceList(GrailsParameterMap params) {
        params.put("isCount", false)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchPriceListQuery(params))
        List<PriceListView> dataList = []
        PriceListView priceListView
        data.each {
            priceListView = new PriceListView()
            priceListView.serial = it["serial"] as Long
            priceListView.testName = it["test_name"]
            priceListView.chargePrice = it["charge_price"] as Float
            priceListView.turnAroundTime = it["turn_around_time"] as Long
            priceListView.unit = it["unit"]
            priceListView.labSection = it["lab_section"]
            priceListView.sampleType = it["sample_type"]
            priceListView.sampleVolume = it["sample_volume"]
            priceListView.transportCondition = it["transport_condition"]

            dataList << priceListView
            priceListView = null
        }

        PagedResultList pagedResultList = new PagedResultList(dataList, searchPriceListCount(params))
        return pagedResultList

    }

    Integer searchPriceListCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchPriceListQuery(params))
        return data["countNum"][0] as int

    }

    def searchPriceListQuery(GrailsParameterMap params) {
        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")
        Long labSectionId = params.long("labSectionId")
        Long medicalCenterId = params.long("medicalCenterId")

        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """
                        select * from(
                           SELECT rownum as serial , a.name as test_name, a.charge_price,
                          a.turn_around_time, a.unit, b.name as lab_section,
                          c.name as sample_type, m.name as sample_volume,
                          t.name as transport_condition
                       """
        }

        query += """      FROM test_type a left outer join sample_volume m
                            on a.sample_volume_id=m.id
                          left outer join transport_condition t
                            on a.transport_condition_id=t.id ,
                          lab_section b, sample_type c
                        where a.lab_section_id=b.id
                              and a.sample_type_id=c.id
                              and a.is_active=1

                       """
        if (labSectionId) query += " and  b.id = ${labSectionId}"
        if (medicalCenterId) query += " and  a.medical_center_id = ${medicalCenterId}"
        if (!isCount) {
            if (isReport) query += " )vm "
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} "
        }

        return query
    }

    PagedResultList searchPatientInvoiceList(GrailsParameterMap params) {
        params.put("isCount", false)
        ValidationTagLib validationTagLib = new ValidationTagLib()
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchPatientInvoiceListQuery(params))
        List<PatientInvoiceView> dataList = []
        PatientInvoiceView patientInvoiceView
        data.each {
            patientInvoiceView = new PatientInvoiceView()
            patientInvoiceView.id = it["id"] as Long
            patientInvoiceView.serial = it["serial"] as Long
            patientInvoiceView.sampleCode = it["sampleCode"]
            patientInvoiceView.transactionAmount = it["transactionAmount"] as Float
            patientInvoiceView.transactionDate = it["transactionDate"]
            if (it["transactionType"].equals("RECEIPT") || it["transactionType"].equals("ExtraDiscount")) {
                patientInvoiceView.amount = -it["transactionAmount"] as Float
            } else {
                patientInvoiceView.amount = it["transactionAmount"] as Float
            }
            if (it["transactionType"]) {

                patientInvoiceView.transactionType = messageSource.getMessage("TransactionType." + it["transactionType"], [] as Object[], " ", Locale.getDefault()) + " "

            } else patientInvoiceView.transactionType = it["transactionType"]

            dataList << patientInvoiceView
            patientInvoiceView = null
        }

        PagedResultList pagedResultList = new PagedResultList(dataList, searchPatientInvoiceListCount(params))
        return pagedResultList

    }

    Integer searchPatientInvoiceListCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchPatientInvoiceListQuery(params))
        return data["countNum"][0] as int

    }

    def searchPatientInvoiceListQuery(GrailsParameterMap params) {
        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")
        Long patientId = params.long("patient.id")
        String sampleCode = params.sampleCode
        Long medicalCenterId = params.long("medicalCenterId")

        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """select * from(
                                      SELECT rownum as serial ,f.id , fs.TRANSACTION_STATUS transactionStatus, f.DATE_CREATED dateCreated ,
                                       f.TRANSACTION_AMOUNT transactionAmount , f.TRANSACTION_TYPE transactionType ,
                                       to_char(f.TRANSACTION_DATE, 'dd/mm/yyyy hh24:mi:ss') as transactionDate , s.SAMPLE_CODE sampleCode , p.AR_NAME arName

                       """
        }

        query += """        FROM FINANCIAL f INNER JOIN FINANCIAL_STATUS fs on ( fs.id = f.CURRENT_STATUS_ID and fs.TRANSACTION_STATUS NOT IN ('REJECTED','CANCEL') )
                          left OUTER JOIN SAMPLE s on s.id = f.SAMPLE_ID2
                          LEFT OUTER JOIN PATIENT p on p.id = s.PATIENT_ID
                         where
                                 1=1
                         """
        if (patientId) {
            query += " and  p.id = ${patientId}"
        } else {
            query += " and  p.id = -1"
        }
        if (sampleCode) {
            query += " and  s.SAMPLE_CODE = '${sampleCode}' "
        }
        if (medicalCenterId) {
            query += " and  f.medical_center_id = ${medicalCenterId}"
        }
        if (!isCount) {
            if (isReport) query += " )vm "
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} "
        }
        if (!isCount && params.boolean("isReport")) {
            query += " where  vm.serial <= 1000 "
            query += " order by vm.dateCreated "
        }


        return query
    }

    Integer searchInstituteTestCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchInsNotAllowedTestQuery(params))
        return data["countNum"][0] as int

    }

    def searchInsNotAllowedTestQuery(GrailsParameterMap params) {


        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")

        Long institutesId = params.long("institutes.id")
        String subQuery = ''
        if (institutesId) subQuery += " and I.id = ${institutesId} "
        subQuery += " and I.medical_center_id = ${params.medicalCenterId} "


        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """select * from(  SELECT rownum as serial ,
                      TT.ID AS id ,
                          case
                  when '${params.lang}' = 'ar'
                    then I.AR_NAME
                  when (I.EN_NAME is null )
                    then I.AR_NAME
                  else I.EN_NAME
                  end as institute_name

                        , TT.NAME as test_name
                     """
        }
        query += """ from INSTITUTES I RIGHT  OUTER JOIN INS_NOT_ALLOWED_TEST INAT on I.ID = INAT.INSTITUTES_ID
                          INNER JOIN  INS_NOT_ALLOWED_TEST_LIST INATL  ON INAT.ID = INATL.INS_NOT_ALLOWED_TEST_ID
                          INNER JOIN  TEST_TYPE TT ON  INATL.TEST_TYPE_ID = TT.ID
                          where 1=1
                          $subQuery
                      """
        if (!isCount) {
            if (isReport) query += " )vm ORDER BY vm.institute_name"
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} ORDER BY vm.institute_name "
        }

        return query


    }

    def searchInsNotAllowedTest(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())


        def data = sql.rows(searchInsNotAllowedTestQuery(params))
        List<InstituteTestView> finalList = []
        InstituteTestView instituteTestView
        data?.each {
            instituteTestView = new InstituteTestView()
            instituteTestView.id = it["serial"] as Long
            instituteTestView.instituteName = it["institute_name"]
            instituteTestView.testName = it["test_name"]
            finalList << instituteTestView
            instituteTestView = null
        }

        PagedResultList pagedResultList = new PagedResultList(finalList, searchInstituteTestCount(params))
        return pagedResultList

    }

    def searchProceedNotAllowedTests(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())


        def data = sql.rows(searchProceedNotAllowedTestsQuery(params))
        List<ProceedNotAllowedTestView> finalList = []
        ProceedNotAllowedTestView proceedNotAllowedTestView
        data?.each {
            proceedNotAllowedTestView = new ProceedNotAllowedTestView()
            proceedNotAllowedTestView.id = it["serial"] as Long
            proceedNotAllowedTestView.instituteName = it["institute_name"]
            proceedNotAllowedTestView.testName = it["test_name"]
            proceedNotAllowedTestView.sampleCode = it["SAMPLE_CODE"]
            proceedNotAllowedTestView.reasonInsertionDate = DateUtils.parseDate(it["REASON_INSERTION_DATE"] as String)
            proceedNotAllowedTestView.reason = it["REASON"]

            finalList << proceedNotAllowedTestView
            proceedNotAllowedTestView = null
        }
        PagedResultList pagedResultList = new PagedResultList(finalList, searchPrecededNotAllowedTestsCount(params))
        return pagedResultList
    }

    def searchProceedNotAllowedTestsQuery(GrailsParameterMap params) {

        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")

        def fromDate = DateUtils.parseDate(params["fromDate"])
        def toDate = DateUtils.parseDate(params["toDate"])

        Long institutesId = params.long("institutes.id")
        String subQuery = ''
        if (institutesId) subQuery += " and I.id = ${institutesId} "
        subQuery += " and s.medical_center_id = ${params.medicalCenterId} "
        if (fromDate) subQuery += "  and TRUNC(NATR.REASON_INSERTION_DATE) >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and TRUNC(NATR.REASON_INSERTION_DATE) <= '${toDate.format("dd-MMM-yyyy")}' "


        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """
                   select * from(  SELECT rownum as serial ,
                       TT.ID AS id ,
                       case
                       when '${params.lang}' = 'ar'
                         then I.AR_NAME
                       when (I.EN_NAME is null )
                         then I.AR_NAME
                       else I.EN_NAME
                       end as institute_name,
                       TT.NAME as test_name ,
                       S.SAMPLE_CODE ,
                       NATR.REASON_INSERTION_DATE as REASON_INSERTION_DATE,
                       NATR.REASON as REASON

                     """
        }
        query += """  from NOT_ALLOWED_TEST_RECORD NATR RIGHT  OUTER JOIN TEST_RECORD_TEST_TYPE TRTT ON NATR.ID = TRTT.NOT_ALLOWED_TEST_RECORD_ID
                       INNER JOIN  SAMPLE S  ON NATR.SAMPLE_ID = s.ID
                       INNER JOIN  INSTITUTES I ON NATR.INSTITUTES_ID = I.ID
                       INNER JOIN  TEST_TYPE TT ON  TRTT.TEST_TYPE_ID = TT.ID
                       where 1=1
                          $subQuery
                      """
        if (!isCount) {
            if (isReport) query += " )vm ORDER BY  vm.institute_name , vm.REASON "
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} ORDER BY  vm.institute_name , vm.REASON "
        }
        return query


    }

    Integer searchPrecededNotAllowedTestsCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchProceedNotAllowedTestsQuery(params))
        return data["countNum"][0] as int

    }


    def searchAuditTestType(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def defaultLocale
        if (params.lang) {
            defaultLocale = new java.util.Locale(params.lang);
        } else {
            defaultLocale = Locale.getDefault()
        }


        def data = sql.rows(searchAuditTestTypeQuery(params))
        List<AuditTestTypeView> finalList = []
        AuditTestTypeView auditTestTypeView
        String propertyName
        data?.each {
            auditTestTypeView = new AuditTestTypeView()
            propertyName = it["class_name"]?.split(/\./)?.last()?.toString()?.substring(0, 1).toLowerCase() + it["class_name"].split(/\./)?.last()?.toString()?.substring(1)
            auditTestTypeView.testName = it["test_name"]
            auditTestTypeView.className = it["class_name"] ? messageSource.getMessage(it["class_name"], [] as Object[], " ", defaultLocale) : ""
            auditTestTypeView.propertyName = propertyName ? messageSource.getMessage(propertyName + '.' + it["PROPERTY_NAME"] + '.label', [] as Object[], " ", defaultLocale) : it["PROPERTY_NAME"]
            auditTestTypeView.oldValue = it["old_value"]
            auditTestTypeView.newValue = it["new_value"]
            auditTestTypeView.dateCreated = DateUtils.parseDate(it["DATE_CREATED"] as String)
            auditTestTypeView.createdBy = it["actor"]

            finalList << auditTestTypeView
            auditTestTypeView = null
            propertyName = null
        }
        PagedResultList pagedResultList = new PagedResultList(finalList, searchAuditTestTypeCount(params))
        return pagedResultList
    }

    def searchAuditTestTypeQuery(GrailsParameterMap params) {

        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")

        def testTypeId = params.long("testType.id")

        def fromDate = DateUtils.parseDate(params["fromDate"])
        def toDate = DateUtils.parseDate(params["toDate"])

        String subQuery = ''

        if (fromDate) subQuery += "  and TRUNC(al.date_created) >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and TRUNC(al.date_created) <= '${toDate.format("dd-MMM-yyyy")}' "

        if (testTypeId) subQuery += "  and  nvl(nvl(tt.id  , tt2.id ), tt3.id) = ${testTypeId} "

        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """
                   select * from(  SELECT rownum as serial ,
                      nvl(nvl(tt.NAME  , tt2.name ), tt3.name) as test_name  , al.CLASS_NAME , al.OLD_VALUE , al.NEW_VALUE , al.PROPERTY_NAME , al.DATE_CREATED , al.actor 

                     """
        }
        query += """  from AUDIT_LOG al 
                       left OUTER JOIN    TEST_TYPE tt on (tt.ID = al.PERSISTED_OBJECT_ID  and al.CLASS_NAME ='com.ubs.mlms.test.TestType'  )
                       left outer join    SAMPLE_CATEGORY sc on (sc.ID = al.PERSISTED_OBJECT_ID    and al.CLASS_NAME ='com.ubs.mlms.test.SampleCategory'  )
                       left outer join    TEST_TYPE tt2 on (sc.TEST_TYPE_ID=tt2.id)
                       left outer join    CATEGORY_SLICED cs on (cs.ID = al.PERSISTED_OBJECT_ID   and al.CLASS_NAME ='com.ubs.mlms.test.CategorySliced'  )
                       left outer join    SAMPLE_CATEGORY sc3 on (cs.SAMPLE_CATEGORY_ID=sc3.id)
                       left outer join    TEST_TYPE tt3 on (sc3.TEST_TYPE_ID=tt3.id)
                       
                       where CLASS_NAME IN ('com.ubs.mlms.test.CategorySliced','com.ubs.mlms.test.SampleCategory','com.ubs.mlms.test.TestType')
                     
                        $subQuery
                       and tt.medical_Center_Id = ${params.medicalCenterId}
                       and al.EVENT_NAME='UPDATE'
                       and al.OLD_VALUE<>al.NEW_VALUE

                      """
        if (!isCount) {
            if (isReport) query += " )vm ORDER BY DATE_CREATED desc "
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} ORDER BY  DATE_CREATED desc "
        }
        return query


    }

    Integer searchAuditTestTypeCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchAuditTestTypeQuery(params))
        return data["countNum"][0] as int

    }

    def searchInvoiceCancelRequest(GrailsParameterMap params) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def defaultLocale
        if (params.lang) {
            defaultLocale = new java.util.Locale(params.lang);
        } else {
            defaultLocale = Locale.getDefault()
        }


        def data = sql.rows(searchInvoiceCancelRequestQuery(params))
        List<AuditTestTypeView> finalList = []
        InvoiceCancelRequestView invoiceCancelRequestView

        data?.each {
            invoiceCancelRequestView = new InvoiceCancelRequestView()

            invoiceCancelRequestView.sampleCode = it["sample_code"]
            invoiceCancelRequestView.serialNumber = it["serial_number"]
            invoiceCancelRequestView.status = messageSource.getMessage("ValueStatus." + it["status"], [] as Object[], " ", defaultLocale)
            invoiceCancelRequestView.reason = it["reason"]
            invoiceCancelRequestView.fullName = it["full_name"]
            invoiceCancelRequestView.requestBy = it["created_By"]
            invoiceCancelRequestView.requestDate = DateUtils.parseDate(it["date_Created"] as String)

            finalList << invoiceCancelRequestView
            invoiceCancelRequestView = null
        }
        PagedResultList pagedResultList = new PagedResultList(finalList, searchInvoiceCancelRequestCount(params))
        return pagedResultList
    }

    def searchInvoiceCancelRequestQuery(GrailsParameterMap params) {

        def max = params.parseMax()
        def offset = params.parseOffset()
        def isCount = params.boolean("isCount")
        def isReport = params.boolean("isReport")

        def status = params.status
        def serialNumber = params.long("serialNumber")
        def reasonId = params.long("reason.id")
        def sampleCode = params.sampleCode


        def fromDate = DateUtils.parseDate(params["fromDate"])
        def toDate = DateUtils.parseDate(params["toDate"])

        String subQuery = ''

        if (fromDate) subQuery += "  and TRUNC(icr.date_created) >= '${fromDate.format("dd-MMM-yyyy")}' "
        if (toDate) subQuery += "  and TRUNC(icr.date_created) <= '${toDate.format("dd-MMM-yyyy")}' "

        if (status) subQuery += "  and icr.status  = '${status}' "
        if (serialNumber) subQuery += "  and i.serial_number  = ${serialNumber} "
        if (reasonId) subQuery += "  and icr.reason_id  = ${reasonId} "
        if (sampleCode) subQuery += "  and s.sample_code  = '${sampleCode}' "

        String query
        if (isCount) {
            query = " select count(1) as countNum "
        } else {
            query = """
                   select * from(  SELECT rownum as serial , s.SAMPLE_CODE ,icr.STATUS , i.SERIAL_NUMBER , (u.FIRST_NAME||' '||u.LAST_NAME) as full_name , icr.DATE_CREATED  ,  icrn.NAME as reason , icr.created_By
                           
                     """
        }
        query += """  from INVOICE_CANCEL_REQUEST  icr
                      
                        INNER JOIN  INVOICE i on i.ID = icr.INVOICE_ID
                        INNER JOIN  SAMPLE  s on s.ID = i.SAMPLE_ID
                        INNER JOIN  INVOICE_CANCEL_REASON icrn on icrn.ID = icr.REASON_ID
                        INNER JOIN  USERS u on u.USERNAME = icr.CREATED_BY
  
                        where icr.medical_center_id = ${params.medicalCenterId}
                        $subQuery

                      """
        if (!isCount) {
            if (isReport) query += " )vm ORDER BY DATE_CREATED desc "
            else query += " )vm  where vm.serial> ${offset} and vm.serial <= ${max + offset} ORDER BY  DATE_CREATED desc "
        }
        return query


    }

    Integer searchInvoiceCancelRequestCount(GrailsParameterMap params) {
        params.put("isCount", true)
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        def data = sql.rows(searchInvoiceCancelRequestQuery(params))
        return data["countNum"][0] as int

    }

    static void mergePdfFiles(List<InputStream> inputPdfList, OutputStream outputStream, File file = null) throws Exception {
        //Create document and pdfReader objects.
        Document document = new Document();
        List<PdfReader> readers =
                new ArrayList<PdfReader>();
        int totalPages = 0;

        //Create pdf Iterator object using inputPdfList.
        Iterator<InputStream> pdfIterator =
                inputPdfList.iterator();

        // Create reader list for the input pdf files.
        while (pdfIterator.hasNext()) {
            InputStream pdf = pdfIterator.next();
            PdfReader pdfReader = new PdfReader(pdf);
            readers.add(pdfReader);
            totalPages = totalPages + pdfReader.getNumberOfPages();
        }

        // Create writer for the outputStream
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);

        //Open document.
        document.open();

        //Contain the pdf data.
        PdfContentByte pageContentByte = writer.getDirectContent();

        PdfImportedPage pdfImportedPage;
        int currentPdfReaderPage = 1;
        Iterator<PdfReader> iteratorPDFReader = readers.iterator();

        // Iterate and process the reader list.
        while (iteratorPDFReader.hasNext()) {
            PdfReader pdfReader = iteratorPDFReader.next();
            //Create page and add content.
            while (currentPdfReaderPage <= pdfReader.getNumberOfPages()) {
                document.newPage();
                pdfImportedPage = writer.getImportedPage(
                        pdfReader, currentPdfReaderPage);
                pageContentByte.addTemplate(pdfImportedPage, 0, 0);
                currentPdfReaderPage++;
            }
            currentPdfReaderPage = 1;
        }
        //Close document and outputStream.
        outputStream.flush();
        document.close();
        outputStream.close();

    }

    def pointTransactionReportData(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def fromDate = params["fromDate"] ? DateUtils.parseDate(params["fromDate"] + " 00:00:00" as String) : null
        def toDate = params["toDate"] ? DateUtils.parseDate(params["toDate"] + " 23:59:59" as String) : null
        Long medicalCenterId = params.long("medicalCenterId")
        Long patientId = params.long("patient.id") ?: -1
        String subQuery = ''
        if (fromDate) subQuery += " and pt.DATE_CREATED >= '${fromDate.format("dd-MMM-yyyy")}'  "
        if (toDate) subQuery += " and pt.DATE_CREATED  <= '${toDate.format("dd-MMM-yyyy")}' "

        if (patientId) {
            subQuery += " and  pt.patient_id  = ${patientId} "
        }
        String query = """
                 
                        SELECT pt.id ,pt.POINTS as point_value , pt.POINT_TRANSACTION_STATUS  status ,s.SAMPLE_CODE as sample_code  , i.SERIAL_NUMBER
                          as serial_number , pt.date_created , f.TRANSACTION_AMOUNT invoice_amount from POINT_TRANSACTION pt LEFT OUTER JOIN  SAMPLE s on pt.SAMPLE_ID = s.ID
                         LEFT OUTER JOIN  INVOICE i on pt.INVOICE_ID = i.ID
                         LEFT OUTER JOIN FINANCIAL f on ( i.ID = f.ID and  TRANSACTION_TYPE = 'INVOICE')
                         where 1=1 
                          $subQuery
                        
                        ORDER BY  pt.ID DESC

                   """

        def data = sql.rows(query)

        def finalList = []
        LinkedHashMap resultMap = [:]

        Double totalPoints = 0.0
        data?.each {

            resultMap.pointValue = it["point_value"]
            resultMap.sampleCode = it["sample_code"]
            resultMap.invoiceNumber = it["serial_number"]
            resultMap.transactionDate = DateUtils.parseDate(it["DATE_CREATED"] as String)
            resultMap.invoiceAmount = it["invoice_amount"]
            resultMap.transactionType = messageSource.getMessage("PointTransactionStatus." + it["status"], [] as Object[], " ", Locale.getDefault()) + " "

            if (it["status"] == PointTransactionStatus.ADD.toString()) {
                totalPoints += (it["point_value"] as Double)
            } else if (it["status"] == PointTransactionStatus.REMOVE.toString()) {
                totalPoints -= (it["point_value"] as Double)
            }

            finalList << resultMap
            resultMap = [:]
        }

        return [finalList: finalList, totalPoints: totalPoints]

    }

    String generateQR(Long medicalCenterId, Long sampleId) {

        String resultURL = ""
        if (medicalCenterId) {
            MedicalCenter medicalCenter = MedicalCenter.get(medicalCenterId)
            resultURL = medicalCenter?.smsUrlResult
            String hashId = Cryptography.encrypt(SystemConstant.generalKey, sampleId?.toString())
            resultURL += hashId
        }
        return resultURL
    }

    def searchJournalVoucher(GrailsParameterMap params) {

        def list = []
        Map map = [:]
        def journalVoucherData = null

        List<JournalVoucherView> journalVoucherViewList = []
        JournalVoucherReportView journalVoucherReportView
        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        if (params.boolean("isExcel") != null && params.boolean("isExcel") == Boolean.FALSE) {

            journalVoucherData = sql.rows(this.generateJournalVoucherQuery(params))
            sql.close()
            journalVoucherData.each {
                journalVoucherReportView = new JournalVoucherReportView()
                journalVoucherReportView.selectedBranch = MedicalCenter.get(params.branchId ? params.branchId : params.currentBranchId)?.arName
                journalVoucherReportView.account = it["account"]
                journalVoucherReportView.refAccount = it["ref_account"]
                journalVoucherReportView.reference = it["reference"]
                journalVoucherReportView.currencyType = it["currency_type"]
                journalVoucherReportView.currencyCode = it["currency_code"]
                journalVoucherReportView.foreign = it["foreign"] as double
                journalVoucherReportView.cAmount = it["c_amount"] as double
                journalVoucherReportView.dAmount = it["d_amount"] as double
                journalVoucherReportView.branchAccountNumber = it["branch_account_number"]
                journalVoucherReportView.branchName = it["branch_name"]
                journalVoucherReportView.description = it["description"]
                journalVoucherReportView.providerTaxId = it["provider_tax_id"]
                journalVoucherReportView.providerName = it["provider_name"]
                journalVoucherReportView.invoiceNumber = it["invoice_number"]
                journalVoucherReportView.invoiceDate = DateUtils.parseDate(it["invoice_date"] as String)
                journalVoucherViewList << journalVoucherReportView
                journalVoucherReportView = null
            }
        }

        return journalVoucherViewList
    }

    String generateJournalVoucherQuery(GrailsParameterMap params) {
        def branchId = params.long("branchId")
        if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
            branchId = params.long("branchId") ? params.long("branchId") : params.long("currentBranchId")
        }

        //if the user not admin just allow to search for specific period
        if (params["fromDate"]) {

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {

                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)
                def today = new java.util.Date()?.plus(-financeReportDays)
                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
                if (fromSelectedDate < allowingDate) {
                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
                }
            }
        }

        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"


        String query = """
                select account,ref_account ,decode(account,'1300','0000220','') as reference,description,
               branch_account_number,branch_name,currency_type,currency_code,
               foreign,
                case when c_amount=0 and d_amount<0 then 
               -1*d_amount 
               when c_amount<0 and d_amount=0 then 
               0
               else 
                c_amount
               end c_amount  , 
                
               case when d_amount=0 and c_amount<0 then 
               -1*c_amount 
               when d_amount<0 and c_amount=0 then 
               0
               else 
                d_amount
               end d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date 
               
               from (
               select
                 account,ref_account,description,
                 branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code, 0 as foreign,
                 sum(credit) as  c_amount , 0 as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Cash In' as record_type
               from
                 (
                   select m2.cash_account_ils as account,'' as ref_account,'Cash In' as description,
                          m.branch_account_no as branch_account_number ,m.EN_NAME as branch_name,
                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal)  as credit

                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and (
                          fs.payment_method = 'CASH'
                          or (
                          fs.transaction_status = 'CANCEL'
                          and exists (
                          select 1
                          from financial_status fs_paid
                          where fs_paid.financial_id = f.id
                          and fs_paid.transaction_status = 'PAID'
                          and fs_paid.payment_method = 'CASH'
                        )
                       )
                       )
                         and f.current_branch_id=m.id
                         and m2.id=1
                         and c.id=f.id
                         and a.id=f.sample_id2
                         and f.id=fs.financial_id
                         and a.medical_center_id=1
             
      """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   select m2.cash_account_ils as account,'' as ref_account,'Cash In' as description, m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name ,
                          0 as credit
                   FROM sample a , patient b , invoice c, financial f, medical_center m,  medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         and m2.id=1
                         and fs.institutes_id=S.id and a.medical_center_id=1
                         and c.id=f.id and f.id=fs.financial_id """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   select  m2.cash_account_ils as account,'' as ref_account,'Cash In' as description, m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name  ,
                           nvl(decode(fs.transaction_status,'PAID',f.transaction_amount,-1*f.transaction_amount),0) as credit
                   FROM sample a , patient b , invoice c, receipt r, financial f, medical_center m, medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and nvl(r.payment_method,'CASH')<>'BY_VISA_OR_E_WALLET'
                         and f.current_branch_id=m.id
                         and m2.id=1
                         AND fs.institutes_id=S.id
                         and c.id=r.invoice_id
                         and r.id=f.id
                         and f.id=fs.financial_id
                         and a.medical_center_id=1
                         and f.transaction_amount<>0  """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   select  m2.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name ,
                           0 as credit
                   from sample a , patient b ,   financial f, medical_center m,medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         and m2.id=1
                         AND fs.institutes_id=S.id
                         and f.transaction_type='ExtraDiscount'
                         and f.sample_id2=a.id and f.id=fs.financial_id
                         and fs.transaction_status<>'PENDING'  and
                         fs.transaction_status<>'REJECTED'
                         and a.medical_center_id=1
                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """

        if (fromDate && toDate) {
            query += " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')" +
                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
        }

        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """  union all
                   SELECT  m4.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name,
                           round(nvl(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),0) ,2)  as credit
                   FROM expenses a , medical_center m, medical_center m2,medical_center m3,medical_center m4,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and ( (a.id =emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
                          and m4.id=1
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id=1  """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ union all
                   SELECT   m3.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name ,
                            nvl(decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) ,0)  as credit
                   FROM expenses a , medical_center m, medical_center m2,   medical_center m3,financial f , financial_status fs
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and m3.id=1
                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id=1  """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ )
               view_fin
               group by branch_account_number,account,ref_account,description,branch_name """


        query += """  union all
               select
                 account , ref_account,description,
                 branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,
                                                   round(sum(balance),2) as c_amount , 0 as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Receivable' as record_type
               from
                 (
                   select m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description, m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name  ,
                          decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount),-1*(fs.subtotal-fs.discount))  as balance
                   FROM sample a , patient b , invoice c, financial f, medical_center m,  medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         and m2.id=1
                         and fs.institutes_id=S.id
                         and a.medical_center_id=1
                         and c.id=f.id and f.id=fs.financial_id  """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ union all
                   select  m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description,  m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name   ,
                           decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount)  as balance
                   FROM sample a , patient b , invoice c, receipt r, financial f, medical_center m, medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes' 
                         and f.current_branch_id=m.id
                         and m2.id=1
                         AND fs.institutes_id=S.id
                         and c.id=r.invoice_id
                         and r.id=f.id
                         and f.id=fs.financial_id
                         and a.medical_center_id=1
                         and f.transaction_amount<>0 """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ union all
                   select  m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description, m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name ,
                           decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount) as balance
                   from sample a , patient b ,   financial f, medical_center m,medical_center m2, institutes S, financial_status fs
                   where a.patient_id=b.id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         and m2.id=1
                         and fs.institutes_id=S.id
                         and f.transaction_type='ExtraDiscount'
                         and f.sample_id2=a.id and f.id=fs.financial_id
                         and fs.transaction_status<>'PENDING'  and
                         fs.transaction_status<>'REJECTED'
                         and a.medical_center_id=1
                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """

        if (fromDate && toDate) {
            query += " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')" +
                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
        }

        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ union all
                   select
                          m2.receivable_account as account,
                          m2.receivable_ref_account as ref_account,
                          'Receivable' as description,
                          m.BRANCH_ACCOUNT_NO as branch_account_number,
                          m.EN_NAME as branch_name,
                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount) as balance
                   FROM sample a,
                        patient b,
                        invoice c,
                        receipt r,
                        financial f,
                        medical_center m,
                        medical_center m2,
                        financial_status fs
                   where a.patient_id = b.id
                         and a.id = c.sample_id
                         and c.id = r.invoice_id
                         and r.id = f.id
                         and f.id = fs.financial_id
                         and f.current_branch_id = m.id
                         and m2.id = 1
                         and a.medical_center_id = 1
                         and f.transaction_type = 'RECEIPT'
                         and f.transaction_amount <> 0
                         and fs.payment_method = 'BY_VISA_OR_E_WALLET'
                         and r.payment_method = 'BY_VISA_OR_E_WALLET'
                         and exists (
                             select 1
                             from financial_status fs_inv
                             where fs_inv.financial_id = c.id
                               and fs_inv.payment_method = 'BY_CREDIT_Institutes'
                               and fs_inv.transaction_status = 'PAID'
                         ) """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }
        query += """ )
                 view_fin
               group by branch_account_number,account,ref_account,description,branch_name """


        query += """  union all
               select
                 account,ref_account,description,branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,0 as c_amount,round(SUM(debit)/1.16,2) as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Revenue' as record_type
               from
                 (
                   SELECT m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,
                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit
                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
                   where a.patient_id=b.id
                         and m2.id =1
                         and a.id=c.sample_id
                         and fs.payment_method='CASH'
                         and f.current_branch_id=m.id
                         and c.id=f.id
                         and a.id=f.sample_id2
                         and a.medical_center_id=1
                         and f.id=fs.financial_id  """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   SELECT  m2.sales_account as account,'00002' as ref_account,'Revenue' as description,
                           m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit
                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_VISA_OR_E_WALLET'
                         and f.current_branch_id=m.id
                         and c.id=f.id
                         and m2.id=1
                         and a.id=f.sample_id2
                         and a.medical_center_id=1
                         and f.id=fs.financial_id """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ union all
                   SELECT  m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  , decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount ),-1*(fs.subtotal-fs.discount)) as debit
                   FROM sample a , patient b , invoice c, financial f, medical_center m, institutes S, financial_status fs, medical_center m2
                   where a.patient_id=b.id
                         and m2.id=1
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         and fs.institutes_id=S.id
                         and a.medical_center_id=1
                         and c.id=f.id and f.id=fs.financial_id   """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   SELECT m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) as debit
                   FROM sample a , patient b ,   financial f, medical_center m, institutes S, financial_status fs, medical_center m2
                   where a.patient_id=b.id
                         and m2.id=1
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         AND fs.institutes_id=S.id
                         and f.transaction_type='ExtraDiscount'
                         and f.sample_id2=a.id and f.id=fs.financial_id
                         and fs.transaction_status<>'PENDING'  and
                         fs.transaction_status<>'REJECTED'
                         and a.medical_center_id=1
                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """

        if (fromDate && toDate) {
            query += " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')" +
                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
        }

        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ )
                 view_fin
               group by branch_account_number,account,ref_account,description,branch_name """

        query += """ union all
               select
                 account,ref_account,description,branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,0 as c_amount,round(SUM(tax) /1.16*0.16,2) as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Tax' as record_type
               from
                 (
                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as tax
                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
                   where a.patient_id=b.id
                         and m2.id =1
                         and a.id=c.sample_id
                         and fs.payment_method='CASH'
                         and f.current_branch_id=m.id
                         and c.id=f.id
                         and a.id=f.sample_id2
                         and a.medical_center_id=1
                         and f.id=fs.financial_id  """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ union all
                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as tax
                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
                   where a.patient_id=b.id
                         and a.id=c.sample_id
                         and fs.payment_method='BY_VISA_OR_E_WALLET'
                         and f.current_branch_id=m.id
                         and c.id=f.id
                         and m2.id=1
                         and a.id=f.sample_id2
                         and a.medical_center_id=1
                         and f.id=fs.financial_id """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   SELECT  '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name, decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount ),-1*(fs.subtotal-fs.discount)) as tax
                   FROM sample a , patient b , invoice c, financial f, medical_center m, institutes S, financial_status fs, medical_center m2
                   where a.patient_id=b.id
                         and m2.id=1
                         and a.id=c.sample_id
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         AND fs.institutes_id=S.id
                         and a.medical_center_id=1
                         and c.id=f.id and f.id=fs.financial_id  """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }


        query += """ union all
                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) as tax
                   FROM sample a , patient b ,   financial f, medical_center m, institutes S, financial_status fs, medical_center m2
                   where a.patient_id=b.id
                         and m2.id=1
                         and fs.payment_method='BY_CREDIT_Institutes'
                         and f.current_branch_id=m.id
                         AND fs.institutes_id=S.id
                         and f.transaction_type='ExtraDiscount'
                         and f.sample_id2=a.id and f.id=fs.financial_id
                         and fs.transaction_status<>'PENDING'  and
                         fs.transaction_status<>'REJECTED'
                         and a.medical_center_id=1
                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """


        if (fromDate && toDate) {
            query += " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')" +
                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
        }

        if (branchId) {
            query += " and f.current_branch_id=${branchId} "
        }

        query += """ ) group by account,ref_account,branch_account_number,description,branch_name """

        query += """ union all
               select
                 account,ref_account,
                 expense_category ,
                 branch_account_number,
                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign,
                            abs(round(sum(credit),2)) as c_amount , 0 as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses - no invoice' as record_type
               from
                 (
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no, 6, '0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
                          m3.medical_center_code   as on_account,
                          round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)) ,2)  as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
                          and a.id=f.id
                          and b.id = a.expense_category_id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_NO_INVOICE' """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ union all
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no, 6, '0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
                          m2.medical_center_code  as on_account,
                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
                   where  a.from_medical_center_id=m.id
                          and b.id = a.expense_category_id
                          and (a.to_medical_center_id=m2.id)
                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_NO_INVOICE' """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ )
                 view_fin
               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """


        query += """ union all
               select
                 account,ref_account,
                 expense_category ,
                 branch_account_number,
                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign, 0 as c_amount,
                            abs(round(sum(debit),2)) as d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses - no invoice' as record_type
               from
                 (
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no, 6, '0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
                          m3.medical_center_code   as on_account,
                          round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),2)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
                          and a.id=f.id
                          and b.id = a.expense_category_id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1  and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_NO_INVOICE'  """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ union all
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no, 6, '0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
                          m2.medical_center_code  as on_account,
                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
                   where  a.from_medical_center_id=m.id
                          and b.id = a.expense_category_id
                          and (a.to_medical_center_id=m2.id)
                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_NO_INVOICE'  """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ )
                 view_fin
               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """


        query += """ union all
               select
                 account,ref_account,
                 expense_category ,
                 branch_account_number,
                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign, 0 as c_amount,
                            abs(round(sum(debit)/1.16,2)) as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses' as record_type
               from
                 (
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no ,6,'0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
                          m3.medical_center_code   as on_account,
                          round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),2)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
                          and a.id=f.id
                          and b.id = a.expense_category_id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ union all
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no ,6,'0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
                          m2.medical_center_code  as on_account,
                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
                   where  a.from_medical_center_id=m.id
                          and b.id = a.expense_category_id
                          and (a.to_medical_center_id=m2.id)
                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ )
                 view_fin
               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """


        query += """ union all 
    select
    account,ref_account,
    expense_category ,
    branch_account_number,
    on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign
     ,0 as c_amount,abs(round(sum(debit)/1.16*0.16,2)) as d_amount  ,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses' as record_type
     from
    (
      select '5500' as account,''  as ref_account, ('Tax - ' || b.name) as expense_category,m3.branch_account_no as branch_account_number ,
             m3.medical_center_code   as on_account,
             round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),2)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
      from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
      where  a.from_medical_center_id=m.id
             and (a.to_medical_center_id=m2.id)
             and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
             and a.id=f.id
             and b.id = a.expense_category_id
             and fs.financial_id=f.id
             and f.medical_center_id = 1 and f.transaction_type='EXPENSES'
             AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' 
          """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ union all
      select '5500' as account,'' as ref_account,('Tax - ' || b.name) as expense_category,  m2.branch_account_no as branch_account_number ,
             m2.medical_center_code  as on_account,
             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
      from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
      where  a.from_medical_center_id=m.id
             and b.id = a.expense_category_id
             and (a.to_medical_center_id=m2.id)
             and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
             and a.id=f.id
             and fs.financial_id=f.id
             and f.medical_center_id = 1
             and f.transaction_type='EXPENSES'
             AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ )
         view_fin
         group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date
         """

        query += """ union all
               select
                 account,ref_account,
                 expense_category ,
                 branch_account_number,
                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign,
                            abs(round(sum(credit)/1.16,2)) as c_amount, 0 as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses' as record_type
               from
                 (
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no ,6,'0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
                          m3.medical_center_code   as on_account,
                          round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)) ,2)  as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
                   where  a.from_medical_center_id=m.id
                          and (a.to_medical_center_id=m2.id)
                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
                          and a.id=f.id
                          and b.id = a.expense_category_id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ union all
                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no ,6,'0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
                          m2.medical_center_code  as on_account,
                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
                   where  a.from_medical_center_id=m.id
                          and b.id = a.expense_category_id
                          and (a.to_medical_center_id=m2.id)
                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
                          and a.id=f.id
                          and fs.financial_id=f.id
                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """
        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }

        query += """ )
                 view_fin
               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """


        query += """ union all 
    select
    account,ref_account,
    expense_category ,
    branch_account_number,
    on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign
     ,abs(round(sum(credit)/1.16*0.16,2)) as c_amount ,0 as d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date,'expenses' as record_type
     from
    (
      select '5500' as account,''  as ref_account, ('Tax - ' || b.name) as expense_category,m3.branch_account_no as branch_account_number ,
             m3.medical_center_code   as on_account,
             round(decode(fs.transaction_status,'PAID',-1*nvl(a.transaction_amount_to_transfer,f.transaction_amount),nvl(a.transaction_amount_to_transfer,f.transaction_amount))/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),2)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
      from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
      where  a.from_medical_center_id=m.id
             and (a.to_medical_center_id=m2.id)
             and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
             and a.id=f.id
             and b.id = a.expense_category_id
             and fs.financial_id=f.id
             and f.medical_center_id = 1 and f.transaction_type='EXPENSES'
             AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' 
          """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ union all
      select '5500' as account,'' as ref_account,('Tax - ' || b.name) as expense_category,  m2.branch_account_no as branch_account_number ,
             m2.medical_center_code  as on_account,
             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
      from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
      where  a.from_medical_center_id=m.id
             and b.id = a.expense_category_id
             and (a.to_medical_center_id=m2.id)
             and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
             and a.id=f.id
             and fs.financial_id=f.id
             and f.medical_center_id = 1
             and f.transaction_type='EXPENSES'
             and fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """

        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and a.from_medical_center_id=${branchId} "
        }


        query += """ )
         view_fin
         group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date
         """


        query += """ union all
               select bank_account,ref_account,company,branch_account_number,branch_name,currency_type,currency_code,foreign,c_amount,0 as d_amount,'' as invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Banks' as record_type
               from (
                select  '1110' as bank_account ,
       case(a.currency_type) when 'JOD' then b.account_number_jod else b.account_number_usd end as ref_account,b.name as company ,m.branch_account_no as branch_account_number,
           m.en_name as branch_name,
      a.currency_type,case(a.currency_type) when 'JOD' then '03' else '02' end as currency_code,
      case a.currency_type
      when  'ILS' then 0
      else round(sum(a.paid_amount),2)
      end as foreign ,round(sum(a.amount_in_ils),2) as c_amount
    from amount_paid_foreign_currency a , visa_wallet_company b , medical_center m
    where a.is_active = 1 and a.payment_method = 'BY_VISA_OR_E_WALLET'
          and m.id= a.current_branch_id
          and a.visa_wallet_id = b.id """

        if (fromDate) {
            query += "  and  a.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  a.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and  a.current_branch_id=${branchId} "
        }

        query += """ group by a.currency_type,b.account_number_jod,b.account_number_usd,b.name,m.branch_account_no,m.en_name """

        query += """ union all
                 select '1110' as bank_account ,b.account_number_ils as ref_account, b.name as company ,m.branch_account_no as branch_account_number, m.EN_NAME as branch_name, 'ILS' as currency_type,'01' as currency_code,
                        0 as foreign, round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2) as c_amount
                 from sample s , visa_wallet_company b ,receipt r, financial d, financial_status fs, medical_center m
                 where  s.id = d.sample_id2
                        and  m.id= d.current_branch_id
                        and  d.id = fs.financial_id
                        and fs.financial_id = r.id
                        and  r.visa_wallet_id = b.id
                        and  d.transaction_type = 'RECEIPT'
                        and  fs.payment_method = 'BY_VISA_OR_E_WALLET'
                        and  s.id not in
                             (select sample_id from amount_paid_foreign_currency where is_active=1) """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += "  and  d.current_branch_id=${branchId} "
        }

        query += " group by b.account_number_ils , b.name ,m.branch_account_no , m.en_name,r.card_number,r.visa_wallet_id "
        query += " having round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2) <> 0"

        query += """ union all
                 select  '1110' as bank_account ,b.account_number_ils as ref_account, b.name as company ,m.branch_account_no as branch_account_number,
                  m.EN_NAME as branch_name, 'ILS' as currency_type,'01' as currency_code,
                        0 as foreign,round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2)  as c_amount
                 from sample s , financial d, financial_status fs, visa_wallet_company b, medical_center m,
                 receipt r
                 where  s.id = d.sample_id2
                        and m.id= d.current_branch_id
                        and  d.id = fs.financial_id                    
                        and r.id=d.id
                        and r.visa_wallet_id = b.id
                         and  d.transaction_type = 'RECEIPT'
                         and r.payment_method='BY_VISA_OR_E_WALLET'
                         and  fs.payment_method = 'BY_CREDIT_Institutes'  
                        and  s.id not in
                             (select sample_id from amount_paid_foreign_currency where is_active=1
                              and trunc(d.transaction_amount)= trunc(amount_in_ils)
 							  and payment_method='BY_VISA_OR_E_WALLET')
                               """


        if (fromDate) {
            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (branchId) {
            query += " and  d.current_branch_id=${branchId} "
        }

        query += " group by  b.account_number_ils,b.name,m.branch_account_no,m.en_name ,r.card_number ,r.visa_wallet_id "
        query += " having round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2) <> 0"


        query += " ) "

        query += """ union all
  select account,ref_account,description,
  branch_account_number,branch_name,currency_type,currency_code,
  foreign,c_amount,d_amount,'' as invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date,'Banks - Cash In' as record_type from (
  select
    '1110' as account,
    case a.currency_type
    when 'ILS'
      then b.ils_account
    when 'JOD'
      then b.jod_account
    else   b.usd_account
  end                          as ref_account,
    'Banks'              as description,
    m.branch_account_no         as branch_account_number,
    (c.en_name ||' - '|| a.currency_type)     as branch_name,
    a.currency_type             as currency_type
    ,case a.currency_type
      when 'ILS'
      then '01'
      when 'JOD'
      then '03'
      else   '02'
      end as currency_code,
    case a.currency_type
    when 'ILS'
      then 0
    else   a.deposit_amount
  end             as foreign,
    round(a.amount_ils, 2) as c_amount,
    0                           as d_amount
  from bank_deposit a, bank_branch b, medical_center m , bank c, bank_medical_center d
  where a.bank_branch_id = b.id and c.id = b.bank_id
        and m.id = a.current_branch_id and d.bank_branch_id = b.id 
        and a.current_branch_id = d.medical_center_id """

        if (fromDate) {
            query += "  and ( a.from_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  OR  a.to_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS'))"
        }
        if (branchId) {
            query += " and  a.current_branch_id=${branchId} "
        }

//        query+= " group by  a.currency_type, b.ils_account, b.jod_account, " +
//                "    b.usd_account, m.branch_account_no, m.medical_center_code, a.deposit_amount,c.en_name "

        query += "  union all " +
                "    select " +
                "    m2.cash_account_ils         as account, " +
                "    ''                          as ref_account, " +
                "    'Cash In'                   as description, " +
                "    m.branch_account_no         as branch_account_number, " +
                "    m.medical_center_code       as branch_name, " +
                "    'ILS'                       as currency_type, " +
                "    '01'                        as currency_code, " +
                "    0                           as foreign, " +
                "    0                           as c_amount, " +
                "    round(sum(a.amount_ils), 2) as d_amount " +
                "  from bank_deposit a, bank_branch b, medical_center m, " +
                "    medical_center m2 , bank_medical_center d " +
                "  where a.bank_branch_id = b.id and m.id = a.current_branch_id " +
                "  and m2.id = 1 and d.bank_branch_id = b.id and a.current_branch_id = d.medical_center_id "


        if (fromDate) {
            query += "  and ( a.from_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
        }
        if (toDate) {
            query += "  OR  a.to_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS'))"
        }
        if (branchId) {
            query += " and  a.current_branch_id=${branchId} "
        }

        query += " group by  m2.cash_account_ils, " +
                " m.branch_account_no, m.medical_center_code )"

        query += " )   where c_amount <>0 or d_amount<>0  ORDER BY " +
                " " +
                "CASE " +
                "        WHEN record_type = 'Cash In'  THEN 0 " +
                "        WHEN record_type = 'Receivable'  THEN 1 " +
                "        WHEN record_type = 'Revenue'  THEN 2 " +
                "        WHEN record_type = 'Tax'  THEN 3 " +
                "        WHEN record_type = 'expenses - no invoice'  THEN 4 " +
                "        WHEN record_type = 'expenses'  THEN 5 " +
                "        WHEN record_type = 'Banks'  THEN 6 " +
                "        WHEN record_type = 'Banks - Cash In'  THEN 7 " +
                "        ELSE 8 " +
                "        END, " +
                "        CASE " +
                "        WHEN record_type='Banks - Cash In' THEN account " +
                "        END desc ," +
                "        invoice_number, " +
                "       CASE " +
                "        WHEN record_type='expenses' THEN '0' else '1' " +
                "        END "



        return query
    }


//    String generateJournalVoucherQuery(GrailsParameterMap params) {
//        def branchId = params.long("branchId")
//        if(!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")  || params.boolean("fromBankDeposit") ){
//            branchId = params.long("branchId")?params.long("branchId"):params.long("currentBranchId")
//        }
//
//        //if the user not admin just allow to search for specific period
//        if (params["fromDate"]) {
//
//            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN")) {
//
//                int financeReportDays = (MedicalCenter.get(params.currentBranchId)?.financialReportDays) ?: 5
//                def fromSelectedDate = DateUtils.parseDate(params["fromDate"] as String)
//                def today = new java.util.Date()?.plus(-financeReportDays)
//                def ymdFmt = new SimpleDateFormat("yyyy-MM-dd")
//                def allowingDate = DateUtils.parseDate(ymdFmt.format(today))
//                if (fromSelectedDate < allowingDate) {
//                    params["fromDate"] = allowingDate?.format("dd/MM/YYYY")
//                }
//            }
//        }
//
//        def fromDate = params["fromDate"] ? (params["fromDate"] + " 00:00:00") : currentDate(true) + " 00:00:00"
//        def toDate = params["toDate"] ? (params["toDate"] + " 23:59:59") : currentDate(true) + " 23:59:59"
//
//
//        String query = """
//                select account,ref_account ,decode(account,'1300','0000220','') as reference,description,
//               branch_account_number,branch_name,currency_type,currency_code,
//               foreign,
//                case when c_amount=0 and d_amount<0 then
//               -1*d_amount
//               when c_amount<0 and d_amount=0 then
//               0
//               else
//                c_amount
//               end c_amount  ,
//
//               case when d_amount=0 and c_amount<0 then
//               -1*c_amount
//               when d_amount<0 and c_amount=0 then
//               0
//               else
//                d_amount
//               end d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date
//
//               from (
//               select
//                 account,ref_account,description,
//                 branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code, 0 as foreign,
//                 round(sum(credit),2) as  c_amount , 0 as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date
//               from
//                 (
//                   select m2.cash_account_ils as account,'' as ref_account,'Cash In' as description,
//                          m.branch_account_no as branch_account_number ,m.EN_NAME as branch_name,
//                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal)  as credit
//
//                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='CASH'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         and c.id=f.id
//                         and a.id=f.sample_id2
//                         and f.id=fs.financial_id
//                         and a.medical_center_id=1
//
//      """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+= """ union all
//                   select m2.cash_account_ils as account,'' as ref_account,'Cash In' as description, m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name ,
//                          0 as credit
//                   FROM sample a , patient b , invoice c, financial f, medical_center m,  medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         and fs.institutes_id=S.id and a.medical_center_id=1
//                         and c.id=f.id and f.id=fs.financial_id """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" union all
//                   select  m2.cash_account_ils as account,'' as ref_account,'Cash In' as description, m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name  ,
//                           nvl(decode(fs.transaction_status,'PAID',f.transaction_amount,-1*f.transaction_amount),0) as credit
//                   FROM sample a , patient b , invoice c, receipt r, financial f, medical_center m, medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and nvl(r.payment_method,'CASH')<>'BY_VISA_OR_E_WALLET'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         AND fs.institutes_id=S.id
//                         and c.id=r.invoice_id
//                         and r.id=f.id
//                         and f.id=fs.financial_id
//                         and a.medical_center_id=1
//                         and f.transaction_amount<>0  """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" union all
//                   select  m2.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name ,
//                           0 as credit
//                   from sample a , patient b ,   financial f, medical_center m,medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         AND fs.institutes_id=S.id
//                         and f.transaction_type='ExtraDiscount'
//                         and f.sample_id2=a.id and f.id=fs.financial_id
//                         and fs.transaction_status<>'PENDING'  and
//                         fs.transaction_status<>'REJECTED'
//                         and a.medical_center_id=1
//                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """
//
//        if (fromDate && toDate) {
//            query +=   " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')"+
//                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
//        }
//
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+="""  union all
//                   SELECT  m4.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name,
//                           nvl(decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID)),0)   as credit
//                   FROM expenses a , medical_center m, medical_center m2,medical_center m3,medical_center m4,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and ( (a.id =emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//                          and m4.id=1
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id=1  """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//        query+=""" union all
//                   SELECT   m3.cash_account_ils as account,'' as ref_account,'Cash In' as description,m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name ,
//                            nvl(decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) ,0)  as credit
//                   FROM expenses a , medical_center m, medical_center m2,   medical_center m3,financial f , financial_status fs
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and m3.id=1
//                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id=1  """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" )
//               view_fin
//               group by branch_account_number,account,ref_account,description,branch_name """
//
//
//
//        query+="""  union all
//               select
//                 account , ref_account,description,
//                 branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,
//                                                   round(sum(balance),2) as c_amount , 0 as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date
//               from
//                 (
//                   select m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description, m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name  ,
//                          decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount),-1*(fs.subtotal-fs.discount))  as balance
//                   FROM sample a , patient b , invoice c, financial f, medical_center m,  medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         and fs.institutes_id=S.id
//                         and a.medical_center_id=1
//                         and c.id=f.id and f.id=fs.financial_id  """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//        query+=""" union all
//                   select  m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description,  m.BRANCH_ACCOUNT_NO as branch_account_number,m.EN_NAME as branch_name   ,
//                           decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount)  as balance
//                   FROM sample a , patient b , invoice c, receipt r, financial f, medical_center m, medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         AND fs.institutes_id=S.id
//                         and c.id=r.invoice_id
//                         and r.id=f.id
//                         and f.id=fs.financial_id
//                         and a.medical_center_id=1
//                         and f.transaction_amount<>0 """
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//        query+=""" union all
//                   select  m2.receivable_account as account,m2.receivable_ref_account as ref_account,'Receivable' as description, m.BRANCH_ACCOUNT_NO as branch_account_number ,m.EN_NAME as branch_name ,
//                           decode(fs.transaction_status,'PAID',-1*f.transaction_amount, f.transaction_amount) as balance
//                   from sample a , patient b ,   financial f, medical_center m,medical_center m2, institutes S, financial_status fs
//                   where a.patient_id=b.id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and m2.id=1
//                         and fs.institutes_id=S.id
//                         and f.transaction_type='ExtraDiscount'
//                         and f.sample_id2=a.id and f.id=fs.financial_id
//                         and fs.transaction_status<>'PENDING'  and
//                         fs.transaction_status<>'REJECTED'
//                         and a.medical_center_id=1
//                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """
//
//        if (fromDate && toDate) {
//            query +=   " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')"+
//                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
//        }
//
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" )
//                 view_fin
//               group by branch_account_number,account,ref_account,description,branch_name """
//
//
//        query+="""  union all
//               select
//                 account,ref_account,description,branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,0 as c_amount,round(SUM(debit)/1.16,2) as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date
//               from
//                 (
//                   SELECT m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,
//                          decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit
//                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id =1
//                         and a.id=c.sample_id
//                         and fs.payment_method='CASH'
//                         and f.current_branch_id=m.id
//                         and c.id=f.id
//                         and a.id=f.sample_id2
//                         and a.medical_center_id=1
//                         and f.id=fs.financial_id  """
//
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" union all
//                   SELECT  m2.sales_account as account,'00002' as ref_account,'Revenue' as description,
//                           m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as debit
//                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_VISA_OR_E_WALLET'
//                         and f.current_branch_id=m.id
//                         and c.id=f.id
//                         and m2.id=1
//                         and a.id=f.sample_id2
//                         and a.medical_center_id=1
//                         and f.id=fs.financial_id """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//        query+=""" union all
//                   SELECT  m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  , decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount ),-1*(fs.subtotal-fs.discount)) as debit
//                   FROM sample a , patient b , invoice c, financial f, medical_center m, institutes S, financial_status fs, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id=1
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         and fs.institutes_id=S.id
//                         and a.medical_center_id=1
//                         and c.id=f.id and f.id=fs.financial_id   """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" union all
//                   SELECT m2.sales_account as account,'00002' as ref_account,'Revenue' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name  ,decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) as debit
//                   FROM sample a , patient b ,   financial f, medical_center m, institutes S, financial_status fs, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id=1
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         AND fs.institutes_id=S.id
//                         and f.transaction_type='ExtraDiscount'
//                         and f.sample_id2=a.id and f.id=fs.financial_id
//                         and fs.transaction_status<>'PENDING'  and
//                         fs.transaction_status<>'REJECTED'
//                         and a.medical_center_id=1
//                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """
//
//        if (fromDate && toDate) {
//            query +=   " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')"+
//                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
//        }
//
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//        query+=""" )
//                 view_fin
//               group by branch_account_number,account,ref_account,description,branch_name """
//
//        query+=""" union all
//               select
//                 account,ref_account,description,branch_account_number,branch_name,'ILS' as currency_type,'01' as currency_code,0 as foreign,0 as c_amount,round(SUM(tax) /1.16*0.16,2) as d_amount,'' as  invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date
//               from
//                 (
//                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as tax
//                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id =1
//                         and a.id=c.sample_id
//                         and fs.payment_method='CASH'
//                         and f.current_branch_id=m.id
//                         and c.id=f.id
//                         and a.id=f.sample_id2
//                         and a.medical_center_id=1
//                         and f.id=fs.financial_id  """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//        query+=""" union all
//                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',fs.subtotal-fs.discount,fs.discount-fs.subtotal) as tax
//                   FROM sample a , patient b , invoice c, financial f, financial_status fs, medical_center m, medical_center m2
//                   where a.patient_id=b.id
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_VISA_OR_E_WALLET'
//                         and f.current_branch_id=m.id
//                         and c.id=f.id
//                         and m2.id=1
//                         and a.id=f.sample_id2
//                         and a.medical_center_id=1
//                         and f.id=fs.financial_id """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//
//        query+=""" union all
//                   SELECT  '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name, decode(fs.transaction_status,'PAID',(fs.subtotal-fs.discount ),-1*(fs.subtotal-fs.discount)) as tax
//                   FROM sample a , patient b , invoice c, financial f, medical_center m, institutes S, financial_status fs, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id=1
//                         and a.id=c.sample_id
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         AND fs.institutes_id=S.id
//                         and a.medical_center_id=1
//                         and c.id=f.id and f.id=fs.financial_id  """
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//
//
//        query+=""" union all
//                   SELECT '5000' as account,'00011' as ref_account,'Tax' as description,m.branch_account_no as branch_account_number,m.EN_NAME as branch_name,decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount) as tax
//                   FROM sample a , patient b ,   financial f, medical_center m, institutes S, financial_status fs, medical_center m2
//                   where a.patient_id=b.id
//                         and m2.id=1
//                         and fs.payment_method='BY_CREDIT_Institutes'
//                         and f.current_branch_id=m.id
//                         AND fs.institutes_id=S.id
//                         and f.transaction_type='ExtraDiscount'
//                         and f.sample_id2=a.id and f.id=fs.financial_id
//                         and fs.transaction_status<>'PENDING'  and
//                         fs.transaction_status<>'REJECTED'
//                         and a.medical_center_id=1
//                         and f.id   in (select fs2.financial_id  from financial_status fs2 where fs2.transaction_status='PAID') """
//
//
//        if (fromDate && toDate) {
//            query +=   " and ( (f.last_updated >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  f.last_updated <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='CANCEL')"+
//                    " OR  (fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS') and  fs.transaction_status='PAID')) "
//        }
//
//        if (branchId) {
//            query += " and f.current_branch_id=${branchId} "
//        }
//
//        query += """ ) group by account,ref_account,branch_account_number,description,branch_name """
//
//        query+=""" union all
//               select
//                 account,ref_account,
//                 expense_category ,
//                 branch_account_number,
//                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign,
//                            abs(round(sum(credit),2)) as c_amount , 0 as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date
//               from
//                 (
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no, 6, '0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
//                          m3.medical_center_code   as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//                          and a.id=f.id
//                          and b.id = a.expense_category_id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_NO_INVOICE' """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" union all
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no, 6, '0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
//                          m2.medical_center_code  as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//                   where  a.from_medical_center_id=m.id
//                          and b.id = a.expense_category_id
//                          and (a.to_medical_center_id=m2.id)
//                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_NO_INVOICE' """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" )
//                 view_fin
//               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """
//
//
//
//
//        query+=""" union all
//               select
//                 account,ref_account,
//                 expense_category ,
//                 branch_account_number,
//                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign, 0 as c_amount,
//                            abs(round(sum(debit),2)) as d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date
//               from
//                 (
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no, 6, '0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
//                          m3.medical_center_code   as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//                          and a.id=f.id
//                          and b.id = a.expense_category_id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1  and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_NO_INVOICE'  """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" union all
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no, 6, '0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
//                          m2.medical_center_code  as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//                   where  a.from_medical_center_id=m.id
//                          and b.id = a.expense_category_id
//                          and (a.to_medical_center_id=m2.id)
//                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_NO_INVOICE'  """
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" )
//                 view_fin
//               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """
//
//
//
//        query+=""" union all
//               select
//                 account,ref_account,
//                 expense_category ,
//                 branch_account_number,
//                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign, 0 as c_amount,
//                            abs(round(sum(debit)/1.16,2)) as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date
//               from
//                 (
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no ,6,'0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
//                          m3.medical_center_code   as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//                          and a.id=f.id
//                          and b.id = a.expense_category_id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//
//        query+=""" union all
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no ,6,'0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
//                          m2.medical_center_code  as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//                   where  a.from_medical_center_id=m.id
//                          and b.id = a.expense_category_id
//                          and (a.to_medical_center_id=m2.id)
//                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" )
//                 view_fin
//               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """
//
//
//
//        query+=""" union all
//    select
//    account,ref_account,
//    expense_category ,
//    branch_account_number,
//    on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign
//     ,0 as c_amount,abs(round(sum(debit)/1.16*0.16,2)) as d_amount  ,invoice_number,provider_tax_id,provider_name,invoice_date
//     from
//    (
//      select '5500' as account,''  as ref_account, ('Tax - ' || b.name) as expense_category,m3.branch_account_no as branch_account_number ,
//             m3.medical_center_code   as on_account,
//             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//      from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//      where  a.from_medical_center_id=m.id
//             and (a.to_medical_center_id=m2.id)
//             and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//             and a.id=f.id
//             and b.id = a.expense_category_id
//             and fs.financial_id=f.id
//             and f.medical_center_id = 1 and f.transaction_type='EXPENSES'
//             AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE'
//          """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//        query+=""" union all
//      select '5500' as account,'' as ref_account,('Tax - ' || b.name) as expense_category,  m2.branch_account_no as branch_account_number ,
//             m2.medical_center_code  as on_account,
//             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as debit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//      from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//      where  a.from_medical_center_id=m.id
//             and b.id = a.expense_category_id
//             and (a.to_medical_center_id=m2.id)
//             and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//             and a.id=f.id
//             and fs.financial_id=f.id
//             and f.medical_center_id = 1
//             and f.transaction_type='EXPENSES'
//             AND fs.transaction_status='CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//        query+= """ )
//         view_fin
//         group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date
//         """
//
//        query+=""" union all
//               select
//                 account,ref_account,
//                 expense_category ,
//                 branch_account_number,
//                 on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign,
//                            abs(round(sum(credit)/1.16,2)) as c_amount, 0 as d_amount,invoice_number,provider_tax_id,provider_name,invoice_date
//               from
//                 (
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m3.branch_account_no ,6,'0') as ref_account, b.name as expense_category,m3.branch_account_no as branch_account_number ,
//                          m3.medical_center_code   as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//                   where  a.from_medical_center_id=m.id
//                          and (a.to_medical_center_id=m2.id)
//                          and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//                          and a.id=f.id
//                          and b.id = a.expense_category_id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//
//        query+=""" union all
//                   select b.EXPENSE_ACCOUNT_NUM as account,lpad(m2.branch_account_no ,6,'0') as ref_account,b.name as expense_category,  m2.branch_account_no as branch_account_number ,
//                          m2.medical_center_code  as on_account,
//                          decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//                   from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//                   where  a.from_medical_center_id=m.id
//                          and b.id = a.expense_category_id
//                          and (a.to_medical_center_id=m2.id)
//                          and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//                          and a.id=f.id
//                          and fs.financial_id=f.id
//                          and f.medical_center_id = 1 and f.transaction_type='EXPENSES' AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//        query+=""" )
//                 view_fin
//               group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date """
//
//
//
//        query+=""" union all
//    select
//    account,ref_account,
//    expense_category ,
//    branch_account_number,
//    on_account,'ILS' as currency_type,'01' as currency_code,0 as foreign
//     ,abs(round(sum(credit)/1.16*0.16,2)) as c_amount ,0 as d_amount ,invoice_number,provider_tax_id,provider_name,invoice_date
//     from
//    (
//      select '5500' as account,''  as ref_account, ('Tax - ' || b.name) as expense_category,m3.branch_account_no as branch_account_number ,
//             m3.medical_center_code   as on_account,
//             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)/(count(DISTINCT emc.MEDICAL_CENTER_ID) over ( partition by emc.EXPENSE_ID))   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//      from expenses a ,expense_category b, medical_center m, medical_center m2,medical_center m3,  financial f , financial_status fs , EXPENSE_MEDICAL_CENTER emc
//      where  a.from_medical_center_id=m.id
//             and (a.to_medical_center_id=m2.id)
//             and ((a.id = emc.EXPENSE_ID and emc.EXPENSE_ID is NOT null and m3.ID = emc.MEDICAL_CENTER_ID))
//             and a.id=f.id
//             and b.id = a.expense_category_id
//             and fs.financial_id=f.id
//             and f.medical_center_id = 1 and f.transaction_type='EXPENSES'
//             AND fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE'
//          """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//        query+=""" union all
//      select '5500' as account,'' as ref_account,('Tax - ' || b.name) as expense_category,  m2.branch_account_no as branch_account_number ,
//             m2.medical_center_code  as on_account,
//             decode(fs.transaction_status,'PAID',-1*f.transaction_amount,f.transaction_amount)   as credit,   a.invoice_number,a.provider_tax_id,a.provider_name,a.invoice_date
//      from expenses a ,expense_category b, medical_center m, medical_center m2,  financial f , financial_status fs
//      where  a.from_medical_center_id=m.id
//             and b.id = a.expense_category_id
//             and (a.to_medical_center_id=m2.id)
//             and a.id not in (select c.EXPENSE_ID from EXPENSE_MEDICAL_CENTER c)
//             and a.id=f.id
//             and fs.financial_id=f.id
//             and f.medical_center_id = 1
//             and f.transaction_type='EXPENSES'
//             and fs.transaction_status<>'CANCEL' and a.expense_classification = 'HAS_INVOICE' """
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and a.from_medical_center_id=${branchId} "
//        }
//
//
//        query+= """ )
//         view_fin
//         group by account,ref_account,branch_account_number,on_account,expense_category,invoice_number,provider_tax_id,provider_name,invoice_date
//         """
//
//
//
//
//        query+=""" union all
//               select bank_account,ref_account,company,branch_account_number,branch_name,currency_type,currency_code,foreign,c_amount,0 as d_amount,'' as invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date
//               from (
//                select  '1110' as bank_account ,
//       case(a.currency_type) when 'JOD' then b.account_number_jod else b.account_number_usd end as ref_account,b.name as company ,m.branch_account_no as branch_account_number,
//           m.en_name as branch_name,
//      a.currency_type,case(a.currency_type) when 'JOD' then '03' else '02' end as currency_code,
//      case a.currency_type
//      when  'ILS' then 0
//      else round(sum(a.paid_amount),2)
//      end as foreign ,round(sum(a.amount_in_ils),2) as c_amount
//    from amount_paid_foreign_currency a , visa_wallet_company b , medical_center m
//    where a.is_active = 1 and a.payment_method = 'BY_VISA_OR_E_WALLET'
//          and m.id= a.current_branch_id
//          and a.visa_wallet_id = b.id """
//
//        if (fromDate) {
//            query += "  and  a.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  a.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and  a.current_branch_id=${branchId} "
//        }
//
//        query+=""" group by a.currency_type,b.account_number_jod,b.account_number_usd,b.name,m.branch_account_no,m.en_name """
//
//        query += """ union all
//                 select '1110' as bank_account ,b.account_number_ils as ref_account, b.name as company ,m.branch_account_no as branch_account_number, m.EN_NAME as branch_name, 'ILS' as currency_type,'01' as currency_code,
//                        0 as foreign, round(sum(decode(fs.transaction_status,'PAID',s.total_price,-1*s.total_price)),2) as c_amount
//                 from sample s , visa_wallet_company b ,receipt r, financial d, financial_status fs, medical_center m
//                 where  s.id = d.sample_id2
//                        and  m.id= d.current_branch_id
//                        and  d.id = fs.financial_id
//                        and fs.financial_id = r.id
//                        and  r.visa_wallet_id = b.id
//                        and  d.transaction_type = 'RECEIPT'
//                        and  fs.payment_method = 'BY_VISA_OR_E_WALLET'
//                        and  s.id not in
//                             (select sample_id from amount_paid_foreign_currency where is_active=1) """
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += "  and  d.current_branch_id=${branchId} "
//        }
//
//        query+=" group by b.account_number_ils , b.name ,m.branch_account_no , m.en_name,s.card_number,s.visa_wallet_id "
//        query+=" having round(sum(decode(fs.transaction_status,'PAID',s.total_price,-1*s.total_price)),2) <> 0"
//
//        query += """ union all
//                 select  '1110' as bank_account ,b.account_number_ils as ref_account, b.name as company ,m.branch_account_no as branch_account_number,
//                  m.EN_NAME as branch_name, 'ILS' as currency_type,'01' as currency_code,
//                        0 as foreign,round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2)  as c_amount
//                 from sample s , financial d, financial_status fs, visa_wallet_company b, medical_center m,
//                 receipt r
//                 where  s.id = d.sample_id2
//                        and m.id= d.current_branch_id
//                        and  d.id = fs.financial_id
//                        and r.id=d.id
//                        and r.visa_wallet_id = b.id
//                         and  d.transaction_type = 'RECEIPT'
//                         and r.payment_method='BY_VISA_OR_E_WALLET'
//                         and  s.payment_method = 'BY_CREDIT_Institutes'
//                        and  s.id not in
//                             (select sample_id from amount_paid_foreign_currency where is_active=1) """
//
//
//        if (fromDate) {
//            query += "  and  fs.date_created >= to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  and  fs.date_created <= to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (branchId) {
//            query += " and  d.current_branch_id=${branchId} "
//        }
//
//        query+=" group by  b.account_number_ils,b.name,m.branch_account_no,m.en_name ,r.card_number ,r.visa_wallet_id "
//        query+=" having round(sum(decode(fs.transaction_status,'PAID',d.transaction_amount,-1*d.transaction_amount)),2) <> 0"
//
//
//        query+=" ) "
//
//        query+=""" union all
//  select account,ref_account,description,
//  branch_account_number,branch_name,currency_type,currency_code,
//  foreign,c_amount,d_amount,'' as invoice_number,'' as provider_tax_id,'' as provider_name,null as invoice_date from (
//  select
//    '1110' as account,
//    case a.currency_type
//    when 'ILS'
//      then b.ils_account
//    when 'JOD'
//      then b.jod_account
//    else   b.usd_account
//  end                          as ref_account,
//    'Banks'              as description,
//    m.branch_account_no         as branch_account_number,
//    (c.en_name ||' - '|| a.currency_type)     as branch_name,
//    a.currency_type             as currency_type
//    ,case a.currency_type
//      when 'ILS'
//      then '01'
//      when 'JOD'
//      then '03'
//      else   '02'
//      end as currency_code,
//    case a.currency_type
//    when 'ILS'
//      then 0
//    else   a.deposit_amount
//  end             as foreign,
//    round(sum(a.amount_ils), 2) as c_amount,
//    0                           as d_amount
//  from bank_deposit a, bank_branch b, medical_center m , bank c, bank_medical_center d
//  where a.bank_branch_id = b.id and c.id = b.bank_id
//        and m.id = a.current_branch_id and d.bank_branch_id = b.id and a.current_branch_id = d.medical_center_id """
//
//        if (fromDate) {
//            query += "  and ( a.from_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  OR  a.to_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS'))"
//        }
//        if (branchId) {
//            query += " and  a.current_branch_id=${branchId} "
//        }
//
//        query+= " group by  a.currency_type, b.ils_account, b.jod_account, " +
//                "    b.usd_account, m.branch_account_no, m.medical_center_code, a.deposit_amount,c.en_name "
//
//        query += "  union all " +
//                "    select " +
//                "    m2.cash_account_ils         as account, " +
//                "    ''                          as ref_account, " +
//                "    'Cash In'                   as description, " +
//                "    m.branch_account_no         as branch_account_number, " +
//                "    m.medical_center_code       as branch_name, " +
//                "    'ILS'                       as currency_type, " +
//                "    '01'                        as currency_code, " +
//                "    0                           as foreign, " +
//                "    0                           as c_amount, " +
//                "    round(sum(a.amount_ils), 2) as d_amount " +
//                "  from bank_deposit a, bank_branch b, medical_center m, " +
//                "    medical_center m2 , bank_medical_center d " +
//                "  where a.bank_branch_id = b.id and m.id = a.current_branch_id " +
//                "  and m2.id = 1 and d.bank_branch_id = b.id and a.current_branch_id = d.medical_center_id "
//
//
//
//
//        if (fromDate) {
//            query += "  and ( a.from_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS')"
//        }
//        if (toDate) {
//            query += "  OR  a.to_date between to_date('${fromDate}','dd/mm/yyyy HH24:MI:SS') and   to_date('${toDate}','dd/mm/yyyy HH24:MI:SS'))"
//        }
//        if (branchId) {
//            query += " and  a.current_branch_id=${branchId} "
//        }
//
//        query += " group by  m2.cash_account_ils, " +
//                " m.branch_account_no, m.medical_center_code )"
//
//        query+="  where c_amount <>0 or d_amount<>0 ) "
//
////        println "query ::> "+query
//
//        return query
//    }


    def postJV(GrailsParameterMap params, Map map) {
        CloseableHttpClient httpClient = null

        try {
            httpClient = HttpClientBuilder.create().build()

            List<JournalVoucherView> journalVoucherViewList = searchJournalVoucher(params)

            List<Map<String, Object>> journalVoucherDetailsList = getJournalVoucherDetail(journalVoucherViewList)



            def branchId = params.long("branchId")

            if (!userService.hasRole("ROLE_SUPER_ADMIN,ROLE_ADMIN") || params.boolean("fromBankDeposit")) {
                branchId = params.long("currentBranchId")
            }

            MedicalCenter medicalCenter = MedicalCenter.findById(branchId)
            String branchCode = medicalCenter?.branchAccountNo
            Long nextTransactionId = map.transactionId as Long

            Properties properties = getBisanProperties()

            String bisanAddJournalVoucherUrl = properties.bisanAddJournalVoucherUrl

            def date = new java.util.Date()
            String today = new SimpleDateFormat("dd/MM/yyyy").format(date)

            String comment = params.fromDate + "-" + params.toDate + "(" + medicalCenter?.arName + ")"

            Map requestBody = [
                    transactionId        : nextTransactionId?.toString(),
                    branchCode           : branchCode,
                    today                : today,
                    comment              : comment ?: "",
                    journalVoucherDetails: JsonOutput.toJson(journalVoucherDetailsList)
            ]

            String jsonBody = JsonOutput.toJson(requestBody)

            String apiUrl = "http://${bisanAddJournalVoucherUrl?.toString()?.trim()}"


            println "========== SALEEM JSON SENT TO BISAN API WAR =========="
            println jsonBody
            println "======================================================="
            println "----------------------------------------------------------"
            println("Send Journal Voucher to Bisan API WAR, Start Date: " + new java.util.Date())

            HttpPost httpPost = new HttpPost(apiUrl)
            httpPost.setHeader("Content-Type", "application/json; charset=utf-8")
            httpPost.setHeader("Accept", "application/json")
            httpPost.setEntity(new StringEntity(jsonBody, "UTF-8"))

            def response = httpClient.execute(httpPost)
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8")

            println(" responseBody :: "+responseBody)
            def jsonResult = new JsonSlurper().parseText(responseBody)

            Map<String, Object> reply = jsonResult?.reply as Map<String, Object>

            if (reply == null) {
                reply = [
                        status : response.statusLine.statusCode,
                        message: "Empty reply from Bisan API WAR",
                        rawBody: responseBody
                ]
            }

            reply.put("transactionId", nextTransactionId)
            reply.put("comment", comment)

            return reply

        } catch (Exception e) {
            e.printStackTrace()

            return [
                    status : 500,
                    message: e.message
            ]

        } finally {
            if (httpClient != null) {
                httpClient.close()
            }
        }
    }


    protected Properties getBisanProperties() {

        Properties properties = new Properties()
        getClass().getResource('/appConfig.properties').withInputStream {
            properties.load(it)
        }

        return properties
    }

    Map readRawData(Map params, List<JournalVoucherView> journalVoucherViewList) {
        JournalVoucherData dataRecord
        Map result = [success: true]
        try {
            journalVoucherViewList.each { it ->
                if (it) {
                    dataRecord = fillRecord(it, params)
                    saveInstance(dataRecord)
                } else {
                    result.success = false
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace()
            result.success = false
            return result
        }
        return result
    }

    JournalVoucherData fillRecord(def detail, Map params) {

        JournalVoucherData dataRecord = new JournalVoucherData()
        dataRecord.journalVoucher = params.journalVoucher
        dataRecord.account = detail.account
        dataRecord.refAccount = detail.refAccount
        dataRecord.branchAccountNumber = detail.branchAccountNumber
        dataRecord.currencyCode = detail.currencyCode
        dataRecord.cAmount = detail.dAmount
        dataRecord.dAmount = detail.cAmount
        dataRecord.dValue = detail.cAmount > 0 ? detail.foreign : 0.0
        dataRecord.cValue = detail.dAmount > 0 ? detail.foreign : 0.0
        dataRecord.invoiceDate = detail.invoiceDate
        dataRecord.providerTaxId = detail.providerTaxId
        dataRecord.providerTaxName = detail.providerName
        dataRecord.invoiceNo = detail.invoiceNumber

        return dataRecord
    }

    JournalVoucherData saveInstance(JournalVoucherData instance) {

        if (!instance.validate()) {
//            return  null
            throw new ValidationException("Instance not valid!", instance.errors)
        }
        instance.save(failOnError: true)

        return instance
    }


    boolean findDuplicateJV(GrailsParameterMap params) {

        boolean foundDuplicateRecord = false

        ArrayList<com.ubs.mlms.api.JournalVoucher> journalVoucher = com.ubs.mlms.api.JournalVoucher.createCriteria().list {
            or {
                eq("fromDate", DateUtils.parseDate(params.fromDate))
                eq("toDate", DateUtils.parseDate(params.toDate))
                between("fromDate", DateUtils.parseDate(params.fromDate), DateUtils.parseDate(params.toDate))
                between("toDate", DateUtils.parseDate(params.fromDate), DateUtils.parseDate(params.toDate))
            }
            eq("currentBranch.id", params.long("branchId") ? params.long("branchId") : params.long("currentBranchId"))
            eq("journalVoucherStatus", JournalVoucherStatus.SAVED)
            maxResults(1)
        }

        if (journalVoucher) {
            foundDuplicateRecord = true
        }

        return foundDuplicateRecord
    }




    List<Map<String, Object>> getJournalVoucherDetail(List<JournalVoucherView> journalVoucherViewList) {
        SimpleDateFormat invoiceDateSdf = new SimpleDateFormat("dd/MM/yyyy")

        List<Map<String, Object>> arrayList = []

        String invoiceDate = ""

        journalVoucherViewList.each { it ->

            if (it?.invoiceDate) {
                invoiceDate = invoiceDateSdf.format(it?.invoiceDate)
            } else {
                invoiceDate = ""
            }

            Map<String, Object> journalVoucherDetail = [
                    account             : it?.account ?: "",
                    currency            : it?.currencyCode ? it?.currencyCode : "",
                    subAct              : it?.refAccount ? it?.refAccount?.toString()?.trim() : "",
                    branch              : it?.branchAccountNumber ?: "",
                    reference           : it?.reference ?: "",

                    dbValue             : (it?.cAmount > 0) ? it?.foreign?.toString() : "",
                    dbAmount            : (it?.cAmount > 0) ? it?.cAmount?.toString() : "",

                    crValue             : (it?.dAmount > 0) ? it?.foreign?.toString() : "",
                    crAmount            : (it?.dAmount > 0) ? it?.dAmount?.toString() : "",

                    vatCode             : it?.invoiceNumber ?: "",
                    invoiceDate         : invoiceDate,

                    authorizedDealer    : it?.providerTaxId ?: "",
                    authorizedDealerName: it?.providerName ?: ""
            ]

            arrayList.add(journalVoucherDetail)
        }

        return arrayList
    }

    def updateJournalVoucherStatusToSaved(Map map,GrailsParameterMap params){
//        Map json = [:]
        Sql sql = new Sql(dataSource)
        String updateQuery = " update journal_voucher set journal_voucher_status  = 'SAVED' , code = '${params.code}',note = '${params.comment}'  where id = ${map.journalVoucherID} "
        sql.executeUpdate(updateQuery)
        sql.close()
//        if(updatedRecord < 1 ){
//            json.success = false
//        }else{
//            json.success = true
//        }
//
//        return json
    }

    def updateJournalVoucherStatusToFailed(Map map) {
//        Map json = [:]
        Sql sql = new Sql(dataSource)
        String updateQuery = "update journal_voucher set journal_voucher_status = 'FAILED',is_active = 0 where id = ${map.journalVoucherID} "
        sql.executeUpdate(updateQuery)
        sql.close()
//        if(updatedRecord < 1 ){
//            json.success = false
//        }else{
//            json.success = true
//        }
//
//        return json
    }

    def getDiscountPercentage(GrailsParameterMap params) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        def branchId = params.long('branchId')
        def fromDate = DateUtils.parseDate(params["fromDate"])
        def toDate = DateUtils.parseDate(params["toDate"])


        String subQuery = ''


        if (fromDate) {
            subQuery += "  and a.date_created >= '${fromDate.format("dd-MMM-yyyy")}' "
        }
        if (toDate) subQuery += "  and  a.date_created <= '${toDate.format("dd-MMM-yyyy")}' "
        if (branchId) {
            subQuery += " and branch_id = ${branchId}  "
        }


        String query = """

SELECT
    branch_id,
    subtotal,
    discount,
    CASE
        WHEN subtotal = 0 THEN 0
        ELSE ROUND((discount / subtotal) * 100, 2)
        END AS percentage,
    payment_method
FROM (
         SELECT
             a.branch_id,
             SUM(a.subtotal) AS subtotal,
             SUM(a.discount + NVL(a.extra_discount_amount, 0)) AS discount,
             CASE
                 WHEN a.payment_method = 'BY_VISA_OR_E_WALLET' THEN 'VISA'
                 WHEN i.notes NOT IN ('INSURANCE', 'MOH') THEN 'CASH'
                 ELSE 'CREDIT'
                 END AS payment_method
         FROM
             sample a
                 LEFT JOIN
             institutes i ON a.institutes_id = i.id
         WHERE
             a.sample_status IN ('INVOICED', 'COMPLETED')
             ${subQuery}
         GROUP BY
             a.branch_id,
             CASE
                 WHEN a.payment_method = 'BY_VISA_OR_E_WALLET' THEN 'VISA'
                 WHEN i.notes NOT IN ('INSURANCE', 'MOH') THEN 'CASH'
                 ELSE 'CREDIT'
                 END
     )

"""


        def data = sql.rows(query)


        def finalList = []
        LinkedHashMap resultMap = [:]

        data?.each {

            resultMap.payment_method = it["payment_method"]
            resultMap.branch = MedicalCenter.findById(it["branch_id"] as Long).arName
            resultMap.subtotal = it["subtotal"]
            resultMap.discount = it["discount"]
            resultMap.percentage = it["percentage"]

            finalList << resultMap
            resultMap = [:]
        }


        return finalList

    }

}
