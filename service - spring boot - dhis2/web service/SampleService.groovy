package com.ubs.portal.test

import com.ubs.portal.enums.FileDirectory
import com.ubs.portal.enums.IntegrationServicesResponse
import com.ubs.portal.enums.SampleStatus
import com.ubs.portal.lookup.CategoryValueFromMachine
import com.ubs.portal.relatedParty.Doctor
import com.ubs.portal.webServices.entities.OrderInfo
import com.ubs.portal.webServices.entities.SampleDataInfoEntity
import com.ubs.portal.webServices.entities.SampleRecordResultEntity
import com.ubs.portal.webServices.entities.ServiceResponseInfo
import com.ubs.portal.webServices.entities.TestEntity
import com.ubs.portal.webServices.entities.TestInfo
import grails.converters.JSON
import grails.orm.PagedResultList
import groovy.sql.Sql
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.codehaus.groovy.grails.web.util.WebUtils
import org.hibernate.SessionFactory

import java.nio.file.Files


class SampleService {

    static transactional = true;
    def commonService;
    def springSecurityService;
    def messageSource

    //--------------------columns that will be display on the data table and upon this we will render the json data -------------------//
    public static final List<String> DOMAIN_COLUMNS = [

            [sort: true, search: true, hidden: true, name: "id", type: "long", source: 'domain'],
//        [sort: true, search: true, hidden: true,name: "hasExtraDiscount",type:"boolean",source:'domain'],
[sort: true, search: true, hidden: false, name: "sampleCode", type: "string", source: 'domain'],
[sort: true, search: true, hidden: false, name: "sampleStatus", type: "enum", source: 'domain', messagePrefix: 'SampleStatus'],
[sort: true, search: true, hidden: false, name: "sampleDate", type: "date", source: 'domain'],
//        [sort: true, search: true, hidden: true,name: "employeeReference",type:"string",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "formNumber",type:"string",source:'domain'],
[sort: true, search: true, hidden: false, name: "doctor", type: "doctor", source: 'domain'],
//        [sort: true, search: true, hidden: true,name: "extraDiscountAmount",type:"float",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "ageUnit",type:"enum",source:'domain', messagePrefix: 'AgeUnits'],
//        [sort: true, search: true, hidden: true,name: "weekOfGestation",type:"integer",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "age",type:"integer",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "createdBy",type:"string",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "lastUpdatedBy",type:"string",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "note",type:"string",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "discount",type:"float",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "branch",type:"medicalcenter",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "hasGestation",type:"boolean",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "isSent",type:"boolean",source:'domain'],
//        [sort: true, search: true, hidden: false,name: "patient",type:"patient",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "paymentMethod",type:"paymentmethod",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "subtotal",type:"float",source:'domain'],
//        [sort: true, search: true, hidden: true,name: "testTime",type:"string",source:'domain'],
[sort: true, search: true, hidden: false, name: "institutes", type: "institutes", source: 'domain'],
[sort: true, search: true, hidden: true, name: "totalPrice", type: "float", source: 'domain']
    ]

    public static final List<String> DOMAIN_INSTITUTE_COLUMNS = [

            [sort: true, search: true, hidden: true, name: "id", type: "long", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "sampleCode", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "sampleStatus", type: "enum", source: 'domain', messagePrefix: 'SampleStatus'],
            [sort: true, search: true, hidden: false, name: "sampleDate", type: "date", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "doctor", type: "doctor", source: 'domain'],
            [sort: true, search: true, hidden: true, name: "institutes", type: "institutes", source: 'domain'],
            [sort: true, search: true, hidden: true, name: "totalPrice", type: "float", source: 'domain']
    ]

    public static final List<String> RESULT_SHEET_DOMAIN_COLUMNS = [

            [sort: true, search: true, hidden: true, name: "sampleCode", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: true, name: "testTime", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "patientName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "labSectionName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "sampleTypeName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "categoryName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "antibioticsName", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "siUnit", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "convUnit", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "testsCount", type: "int", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "siResult", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "convResult", type: "string", source: 'domain'],
            [sort: true, search: true, hidden: false, name: "antibioticResult", type: "string", source: 'domain']
//            [sort: true, search: true, hidden: false,name: "doctorName",type:"string",source:'domain']
    ]
//--------------------End columns that will be display on the data table and upon this we will render the json data -------------------//

/** @Author: UBS TEAM Thu Mar 10 17:56:31 IST 2016
 * @desc Generic search for Sample.
 * @param params 'Sample properties'.
 * @return a paged result List of instance Sample which have the  "totalCount" for the paged result ,
 *          sorted by lastUpdated desc by default  else if they provide a sort by and sort direction
 */
    PagedResultList search(GrailsParameterMap params) {

        // Pagination , sorting.
        def max = params.parseMax()
        def offset = params.parseOffset()
        def ids = params.listLong('ids[]')
        def dTplSortAttribute = params.parseSortAttribute()
        def dTplSortDirection = params.parseSortDirection()

        def session = WebUtils.retrieveGrailsWebRequest().getSession()
        // Variables
        def sortDirection = params.sortDirection ?: 'desc'
        def sortBy = params.sortBy ?: 'sampleDate'
        def sSearch = commonService.replaceSpecialChar(params.sSearch)
        if (sSearch)
            sSearch = "%${sSearch}%"

        def id = params.long("id")
        def sampleCode = params["sampleCode"]
        def institutesId = params.long("institutes.id")
//        def employeeReference = params["employeeReference"]
//        def formNumber = params["formNumber"]
        def doctorId = params['doctor'] ? params.long("doctor") : session.doctor
        def medicalCenterId = params.long("medicalCenterId")
        def note = params["note"]
        def branchId = params.long("branch.id")
        def patientId = params['patient.id'] ? params.long("patient.id") : session.patient
        def fromDate = params.date("fromDate", ['dd/MM/yyyy', 'dd/MM/yyyy HH:mm'])
        def toDate = params.date("toDate", ['dd/MM/yyyy', 'dd/MM/yyyy HH:mm'])
        def testTime = params["testTime"]
        def testTypeId = params.long("testType")
//        println (testTypeId)
        // Criteria
        return Sample.createCriteria().list(max: max, offset: offset) {

            and {
                if (sSearch) {
                    or {
                        ilike("sampleCode", sSearch)
                        ilike("employeeReference", sSearch)
                        ilike("formNumber", sSearch)
                        ilike("createdBy", sSearch)
                        ilike("lastUpdatedBy", sSearch)
                        ilike("note", sSearch)
                        ilike("testTime", sSearch)
                    }
                }

                if (ids)
                    inList("id", ids) // filter by ids.
                if (id)
                    eq("id", id)
                if (sampleCode)
                    ilike("sampleCode", "%${sampleCode}%")
                if (institutesId)
                    eq("institutes.id", institutesId)

//                        if(employeeReference)
//                            ilike("employeeReference", "%${employeeReference}%")
//                        if(formNumber)
//                            ilike("formNumber", "%${formNumber}%")
                if (doctorId)
                    eq("doctor.id", doctorId)

                if (medicalCenterId)
                    eq("medicalCenterId", medicalCenterId)
//                        if(sampleStatus)
                eq("sampleStatus", SampleStatus.COMPLETED)
                if (note)
                    ilike("note", "%${note}%")

                if (branchId)
                    eq("branch.id", branchId)

                if (patientId)
                    eq("patient.id", patientId)

                if (fromDate)
                    ge("sampleDate", fromDate)
                if (toDate)
                    le("sampleDate", toDate)

                if (testTypeId) {
                    testType {
                        eq('id', testTypeId)
                    }
                }
                if (testTime)
                    ilike("testTime", "%${testTime}%")

            }
            if (!sortBy && dTplSortAttribute && dTplSortDirection)
                order(DOMAIN_COLUMNS[dTplSortAttribute].name, dTplSortDirection)
            else if (sortBy && sortDirection)
                order(sortBy, sortDirection)
        }
    }

    PagedResultList search(HashMap params) {
        // Pagination , sorting.
        def max = params['max']
        def offset = params['offset']
        // Variables
        def sortDirection = params.sortDirection ?: 'desc'
        def sortBy = params.sortBy ?: 'sampleDate'
        def sSearch = commonService.replaceSpecialChar(params.sSearch)

        def id = params.id as Long
        def sampleCode = params["sampleCode"]
        def institutesId = params['institutes.id'] as Long
//        def employeeReference = params["employeeReference"]
//        def formNumber = params["formNumber"]
        def doctorId = params['doctor.id'] as Long
        def medicalCenterId = params["medicalCenterId"] as Long
        def note = params["note"]
        def branchId = params["branch.id"] as Long
        def patientId = params['patient.id'] as Long
        def sampleDate = null//params.date("sampleDate",['dd/MM/yyyy','dd/MM/yyyy HH:mm'])
        def testTime = params["testTime"]
        String mobileNumber = params.mobileNumber

        return Sample.createCriteria().list(max: max, offset: offset) {

            and {
                if (sSearch) {
                    or {
                        ilike("sampleCode", sSearch)
                        ilike("employeeReference", sSearch)
                        ilike("formNumber", sSearch)
                        ilike("createdBy", sSearch)
                        ilike("lastUpdatedBy", sSearch)
                        ilike("note", sSearch)
                        ilike("testTime", sSearch)
                    }
                }
                if (id)
                    eq("id", id)
                if (sampleCode)
                    ilike("sampleCode", "%${sampleCode}%")
                if (institutesId)
                    eq("institutes.id", institutesId)
                if (doctorId)
                    eq("doctor.id", doctorId)
                if (medicalCenterId)
                    eq("medicalCenterId", medicalCenterId)
                eq("sampleStatus", SampleStatus.COMPLETED)
                if (note)
                    ilike("note", "%${note}%")
                if (branchId)
                    eq("branch.id", branchId)
                if (patientId)
                    eq("patient.id", patientId)
                if (sampleDate)
                    eq("sampleDate", sampleDate)
                if (testTime)
                    ilike("testTime", "%${testTime}%")

                if(mobileNumber){
                    sqlRestriction("exists (select 1 from patient p where p.id=this_.patient_id and  mobile = '${mobileNumber}' ) ")

                }
            }
            if (sortBy && sortDirection)
                order(sortBy, sortDirection)
        }
    }

    /** @Author: UBS TEAM Thu Mar 10 17:56:31 IST 2016
     * @desc Generic search for dataTables.
     * @param params.
     * @return a JSON result List
     */
    JSON searchAsJson(List dtpColumns, GrailsParameterMap params) {
        PagedResultList<Sample> results = this.search(params)
        def dataToRender = commonService.generateDataToRender(dtpColumns, results, params)
        dataToRender.aaData.each {
            if (checkNotPaid(it.id as Long)) {
                it.checkNotPaid = true
            } else {
                it.acheckNotPaid = false
            }
        }
        commonService.cleanUpGorm();
        // to reset a value on the aaData  for example to get the value form other systems (remoting)
        return dataToRender as JSON
    }

    /** @Author: UBS TEAM Wed Mar 09 20:56:08 IST 2016
     * @desc Generic search for dataTables.
     * @param params.
     * @return a JSON result List
     */
    def shortList(GrailsParameterMap params) {

        def max = params.int('max', 10)
        def offset = params.int('offset', 0)
        def sortDirection = params.sortDirection
        def sortBy = params.sortBy
        def patientId = params.long("patient.id")

        // Criteria
        return Sample.createCriteria().list(max: max, offset: offset) {
            eq('patient.id', patientId)
            eq("sampleStatus", SampleStatus.COMPLETED)
            if (sortBy && sortDirection)
                order(sortBy, sortDirection)
            else
                order('sampleDate', 'desc')
//            order("nvl('lastUpdated', sysdate)", "desc")

//            SELECT a.id, a.version, a.age, a.branch_id, a.created_by,
//            a.date_created, a.discount, a.doctor_id, a.employee_reference,
//            a.form_number, a.has_gestation, a.institutes_id, a.last_updated,
//            a.last_updated_by, a.medical_center_id, a.note, a.patient_id,
//            a.payment_method, a.sample_code, a.sample_date, a.sample_status,
//            a.subtotal, a.total_price, a.week_of_gestation, a.test_time,
//            a.age_unit, a.has_extra_discount, a.extra_discount_amount,
//            a.is_sent
//            FROM sample a where a.patient_id=user_patient_code;

        }
    }

    List<Map> getAttachedResult(Long sampleId) {
        List<Map> dataList = []
        Map attachedResult = [:]
        List<SampleTest> sampleTestList = SampleTest.createCriteria().list {
            and {
                isNotNull('systemFileName')
                eq('sample.id', sampleId)
            }
        }
        sampleTestList.each { SampleTest sampleTest ->
            if (!dataList["systemTitle"]?.contains(sampleTest?.systemFileName)) {
                attachedResult = [:]
                File file = commonService.getFile(FileDirectory.SAMPLE, sampleTest?.systemFileName)
                attachedResult.put("fileContentType", Files?.probeContentType(file?.toPath()))
                attachedResult.put("bytes", file?.getBytes())
                attachedResult.put("title", sampleTest?.userFileName)
                attachedResult.put("systemTitle", sampleTest?.systemFileName)
                dataList << attachedResult
                attachedResult = null
            }
        }
        return dataList
    }

    List<Doctor> getDoctorListForPatient() {
        def session = WebUtils.retrieveGrailsWebRequest().getSession()

        def doctorIds = Sample.createCriteria().list() {
            eq('patient.id', session.patient)
            projections {
                doctor {
                    distinct('id')
                }
            }
        }
        if (!doctorIds)
            doctorIds = [-1L]

        List<Doctor> docs = Doctor.createCriteria().list() {
            inList('id', doctorIds)
        }
        docs
    }

    List<TestType> getTestTypesForPatient() {
        def session = WebUtils.retrieveGrailsWebRequest().getSession()

        def testIds = Sample.createCriteria().list() {
            eq('patient.id', session.patient)
            projections {
                testType {
                    property('id')
                }
            }
        }
        if (!testIds || testIds.isEmpty())
            testIds = [-1L]
        else
            testIds = testIds.unique()

        List<TestType> tests = TestType.createCriteria().list() {
            inList('id', testIds)
        }
        tests
    }

    List<TestEntity> getSampleTests(long sampleId, long patientId, Long doctorId = null) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "SELECT a.sample_code SAMPLE_CODE, p.ar_name as patient_name, a.sample_date as sample_date, " +
                " c.name as test_name, b.id as sample_test_id FROM sample a, sample_test b, test_type c, patient p " +
                "  where a.id=b.sample_id and a.patient_id=p.id and b.test_type_id=c.id and " +
                "  a.id= " + sampleId + " and p.id = " + patientId
        if (doctorId)
            query += " and a.doctor_id  = " + doctorId

        def data = sql.rows(query)
        def finalList = []
        TestEntity test
        data.each {
            test = new TestEntity()
            test.sampleCode = it["SAMPLE_CODE"]
            test.patientName = it["patient_name"]
            test.sampleDate = Date.parse("yyyy-MM-dd", it["SAMPLE_DATE"]?.toString())
            test.id = it["sample_test_id"] as Long
            test.sampleId = sampleId
            test.name = it["TEST_NAME"]
            finalList << test
            test = null
        }
        return finalList
    }


    def getTestDetails(long sampleId, long sampleTestId, long patientId, Long doctorId = null) {
        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = "select a.sample_code as sample_code, a.sample_date as sample_date, b.ar_name as patient_name, " +
                " nvl(d.ar_name,'No Referring Doctor') as doctor_name,  t.name as test_name,  sc.name as category_name, " +
                " acs.description as antibiotics_name, get_si_result(a.id,sc.id) as SI_RESULT, get_conv_result(a.id,sc.id,ant.antibiotics_id) as CONV_RESULT, " +
                " get_anti_result(a.id,sc.id,ant.antibiotics_id) as ANTIBIOTIC_RESULT, get_prev1 ( t.id, b.id,a.id) As prev_date_1, " +
                " get_prev2 ( t.id, b.id,a.id) As prev_date_2, get_result_prev1 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_1," +
                " get_result_prev2 ( t.id, b.id, a.id, sc.id , ant.antibiotics_id) as prev_result_2, " +
                " GET_SLICE_SI( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as SI_UNIT," +
                " GET_SLICE_CONV( a.id,sc.id, b.gender, a.age, a.has_gestation, a.test_time, a.age_unit,'RESULT') as CONV_UNIT " +
                " from SAMPLE a , PATIENT b, DOCTOR d, sample_test s, test_type t, lab_section l, sample_type st, sample_category sc," +
                " sample_category_antibiotics ant, antibiotics acs  " +
                " where a.patient_id=b.id and a.doctor_id=d.id(+) and a.id=s.sample_id and s.system_file_name is null and s.test_type_id=t.id" +
                " and t.lab_section_id=l.id and t.sample_type_id=st.id and t.id=sc.test_type_id and sc.is_measurable = 1 and sc.id=ant.sample_category_antibiotics_id(+)  " +
                " and ant.antibiotics_id=acs.id(+) and a.sample_status='COMPLETED' and (get_si_result(a.id,sc.id) not in ('x','X') OR " +
                " get_conv_result(a.id,sc.id,ant.antibiotics_id)  not in ('x','X') OR " +
                " (get_si_result(a.id,sc.id) is null and get_conv_result(a.id,sc.id,ant.antibiotics_id) is null) ) " +
                " and a.id = " + sampleId + " and s.id = " + sampleTestId + " and b.id = " + patientId
        if (doctorId)
            query += " and d.id  = " + doctorId

        query += " order by a.id, l.id, t.id, sc.order_number, get_anti_result(a.id,sc.id,ant.antibiotics_id) desc "

        def data = sql.rows(query)
        def finalList = []
        TestEntity test
        data.each {
            test = new TestEntity()
            test.id = sampleTestId
            test.name = it["TEST_NAME"]
            test.sampleId = sampleId
            test.sampleCode = it["SAMPLE_CODE"]
            test.patientName = it["patient_name"]
            test.doctorName = it["doctor_name"]
            test.sampleCategoryName = it["category_name"]
            test.antibioticsName = it["antibiotics_name"]
            test.sampleDate = Date.parse("yyyy-MM-dd", it["SAMPLE_DATE"]?.toString())
            test.siResult = it["SI_RESULT"]
            test.siUnit = it["SI_UNIT"]
            test.siUnit = (test.siUnit && test.siUnit.replaceAll("\\s", '').length() > 0) ? test.siUnit : null
            test.convUnit = it["CONV_UNIT"]
            test.convUnit = (test.convUnit && test.convUnit.replaceAll("\\s", '').length() > 0) ? test.convUnit : null
            test.convResult = it["CONV_RESULT"]
            test.antiBioticResult = it["ANTIBIOTIC_RESULT"]
            test.prevDate1 = it["prev_date_1"] ? Date.parse("yyyy-MM-dd", it["prev_date_1"]?.toString()) : null
            test.prevDate2 = it["prev_date_2"] ? Date.parse("yyyy-MM-dd", it["prev_date_2"]?.toString()) : null
            test.prevResult1 = it["prev_result_1"]
            test.prevResult2 = it["prev_result_2"]

            // handle SI and CONV
            test.convUnit = (test.convUnit && test.siUnit) ? ("Conv: " + test.convUnit + " / SI: " + test.siUnit) : (test.convUnit ? test.convUnit : test.siUnit)
            test.siUnit = null

            finalList << test
            test = null
        }
//        println ("finalList----------> " + finalList)
        return finalList
    }

    def checkNotPaid(Long sampleId) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())
        String query = """ select  check_sample_credit_icash(${sampleId}) as isPaid from sample where id=${sampleId}
                        """
        def result = sql.rows(query)
        if (result && result.size() > 0) {
            return result[0].isPaid
        } else {
            return false
        }


    }


    List<SampleDataInfoEntity> getSampleData(Long machineId, Long sampleId) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        String query = "select test_type_long_id, sample_category_long_id , start_position, category_length, factor from machine_configuration where machine_brand_long_id=(select m.machine_brand_id from machine m where m.id=" + machineId + ") and test_type_long_id in ( SELECT   a.test_type_id FROM sample_test a where a.sample_id= " + sampleId + ")"

        def data = sql.rows(query)
        def finalList = []
        SampleDataInfoEntity sampleDataInfoEntity


        data?.each {
            sampleDataInfoEntity = new SampleDataInfoEntity()

            sampleDataInfoEntity.testTypeId = it["test_type_long_id"] as Long
            sampleDataInfoEntity.categoryId = it["sample_category_long_id"] as Long
            sampleDataInfoEntity.startPosition = it["start_position"] as Long
            sampleDataInfoEntity.categoryLength = it["category_length"] as Long
            sampleDataInfoEntity.factor = it["factor"] as Double
            finalList << sampleDataInfoEntity

            sampleDataInfoEntity = null
        }
        return finalList
    }


    Boolean recordSerialData(String machineId, Long barcodeLocation, String serialData) {

        /**
         * Read the barcode from the serial information
         * */

        String barcode = serialData.substring(barcodeLocation, 11);
        println("Bar Code read: " + barcode);
        Integer locMachineId = new Integer(barcode.substring(0, 2));
        println("locMachineId read: " + locMachineId);
        Integer sampleTypeId = new Integer(barcode.substring(2, 2));
        println("sampleTypeId read: " + sampleTypeId);
        Long sampleId = new Long(barcode.substring(4, 7));
        println("sampleId read: " + sampleId);


        if(locMachineId!=machineId){
            println 'The machine id not identical to the specified machine id'
            return false;
        }


        /**
         * get the sample info
         * */

        
        List<SampleDataInfoEntity> sampleData=getSampleData(machineId,sampleId)

        if(sampleData){

            def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
            SessionFactory sessionFactory = ctx.sessionFactory
            def sql = new Sql(sessionFactory.getCurrentSession().connection())


            String query = "Insert into category_value_from_machine " +
                    " (category_value, original_value, date_created, read_number, sample_id, test_type_id, sample_category_id, sample_barcode, Machine_id) " +
                    " Values " +
                    " (?,?, sysdate ,?,?,?,?,?,?)"

            sampleData.each {SampleDataInfoEntity sampleDataInfoEntity->

                Double originalVal = Double.parseDouble(serialData.substring(sampleDataInfoEntity.startPosition, sampleDataInfoEntity.categoryLength));
                Double newVal = originalVal * sampleDataInfoEntity.factor;

                int insertedData = sql.executeUpdate(query,[newVal,originalVal,sampleId,sampleDataInfoEntity.testTypeId,sampleDataInfoEntity.categoryId,barcode])

                if(insertedData<1){
                    throw new Exception("Error in save data ...")
                }
            }
            return true
        }else{
            return false
        }
    }

    Boolean recordSerialData(Long machineId,Long sampleId,String barcode,SampleRecordResultEntity[] sampleRecordResultEntities) {
        try{
            if(machineId && sampleId && sampleRecordResultEntities?.length>0){

                def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
                SessionFactory sessionFactory = ctx.sessionFactory
                def sql = new Sql(sessionFactory.getCurrentSession().connection())


                String query = "Insert into category_value_from_machine " +
                        " (category_value, original_value, date_created, read_number, sample_id, test_type_id, sample_category_id, sample_barcode, Machine_id) " +
                        " Values " +
                        " (?,?, sysdate ,0,?,?,?,?,?)"

                sampleRecordResultEntities?.each {
                    int insertedData = sql.executeUpdate(query,[new Double(it.newVal),new Double(it.originalVal),sampleId,new Long(it.testTypeId),new Long(it.categoryId),barcode,machineId])
                    if(insertedData<1){
                        throw new Exception("Error in save data ...")
                    }
                }
                return true
            }else{
                return false
            }
        }catch(Exception e){
            e.printStackTrace()
            return false
        }
    }





//    List<OrderInfo> transferArchitectSampleTests(String machineBrand , String sampleId) {
//
//        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
//        SessionFactory sessionFactory = ctx.sessionFactory
//        def sql = new Sql(sessionFactory.getCurrentSession().connection())
//
//        String query ="SELECT" +
//                "  assay_no," +
//                "   result1," +
//                "   result2," +
//                "    result3," +
//                "   rule1," +
//                "    rule2," +
//                "    rule3," +
//                "    sample_category_id," +
//                "    test_type_id," +
//                "    (select sample_code from sample where id='${new Long(sampleId) % 10000000}') as sample_code ," +
//                "   (select substr(gender,1,1) from patient  where id=(select patient_id from sample where id='${new Long(sampleId) % 10000000}')) as patient_gender" +
//                "    FROM" +
//                "   astm_configuration" +
//                "    where machine_brand_id=(select id from machine_brand where name='${machineBrand}')" +
//                "    and test_type_id in" +
//                "    ( SELECT  test_type_id FROM" +
//                "    sample_test where sample_id='${new Long(sampleId) % 10000000}' and test_type_status in ('NORMAL','TRANSFERRED'))"
//
//
//        def data = sql.rows(query)
//        def finalList = []
//        OrderInfo orderInfo
//
//
//        data?.each{it ->
//
//
//            orderInfo = new OrderInfo()
//
//            orderInfo.patientName = it["sample_code"] as String
//            orderInfo.patientGender = it["patient_gender"] as String
//            orderInfo.assayNumber = it["assay_no"]
//
//
////            println("assayNumber : "   +   orderInfo.assayNumber)
////            println("patientGender : " + orderInfo.patientGender)
////            println("patientName : "    +   orderInfo.patientName)
//
//
//            finalList << orderInfo
//
//            orderInfo = null
//        }
//
//
//
//        return finalList
//
//    }

    List<OrderInfo> transferArchitectSampleTests(String machineBrand , String sampleId, String branchId=null) {

        def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
        SessionFactory sessionFactory = ctx.sessionFactory
        def sql = new Sql(sessionFactory.getCurrentSession().connection())

        String query =
                "  select " +
                "  assay_no," +
                "  result1," +
                "  result2," +
                "  result3," +
                "  rule1," +
                "  rule2," +
                "  rule3," +
                "  sample_category_id," +
                "  test_type_id," +
                "  (select sample_code from sample where id='${new Long(sampleId) % 10000000}') as sample_code ," +
                "  (select substr(gender,1,1) from patient  where id=(select patient_id from sample where id='${new Long(sampleId) % 10000000}')) as patient_gender" +
                "  FROM" +
                "  astm_configuration" +
                "  where machine_brand_id=(select id from machine_brand where name='${machineBrand}')" +
                "  and test_type_id in" +
                "  ( SELECT  test_type_id FROM" +
                "  sample_test where sample_id='${new Long(sampleId) % 10000000}' and test_type_status in ('NORMAL','TRANSFERRED') " +
                ((branchId!=null)?(" and  to_branch_id='"+branchId+"'"):"")+ ")" +

                " UNION  " +
                " SELECT " +
                " assay_no1," +
                " result1," +
                " result2," +
                " result3," +
                " rule1," +
                " rule2," +
                " rule3," +
                " sample_category_id," +
                " test_type_id," +
                " (select sample_code from sample where id='${new Long(sampleId) % 10000000}') as sample_code ," +
                " (select substr(gender,1,1) from patient  where id=(select patient_id from sample where id='${new Long(sampleId) % 10000000}')) as patient_gender" +
                " FROM" +
                " astm_configuration" +
                " where assay_no1 is not null and machine_brand_id=(select id from machine_brand where name='${machineBrand}')" +
                " and test_type_id in" +
                " ( SELECT  test_type_id FROM" +
                " sample_test where sample_id='${new Long(sampleId) % 10000000}' and test_type_status in ('NORMAL','TRANSFERRED') " +
                ((branchId!=null)?(" and  to_branch_id='"+branchId+"'"):"")+ ")" +

                " UNION  " +
                " SELECT " +
                " assay_no2, " +
                " result1, " +
                " result2, " +
                " result3, " +
                " rule1, " +
                " rule2, " +
                " rule3, " +
                " sample_category_id, " +
                " test_type_id, " +
                " (select sample_code from sample where id='${new Long(sampleId) % 10000000}') as sample_code ," +
                " (select substr(gender,1,1) from patient  where id=(select patient_id from sample where id='${new Long(sampleId) % 10000000}')) as patient_gender" +
                "  FROM " +
                "  astm_configuration" +
                "  where assay_no2 is not null and machine_brand_id=(select id from machine_brand where name='${machineBrand}')" +
                "  and test_type_id in" +
                "  ( SELECT  test_type_id FROM" +
                "  sample_test where sample_id='${new Long(sampleId) % 10000000}' and test_type_status in ('NORMAL','TRANSFERRED') " +
                   ((branchId!=null)?(" and  to_branch_id='"+branchId+"'"):"")+ ")" +
                " UNION  " +
                " SELECT " +
                " assay_no3," +
                " result1," +
                " result2," +
                " result3," +
                " rule1," +
                " rule2," +
                " rule3," +
                " sample_category_id," +
                " test_type_id," +
                " (select sample_code from sample where id='${new Long(sampleId) % 10000000}') as sample_code ," +
                " (select substr(gender,1,1) from patient  where id=(select patient_id from sample where id='${new Long(sampleId) % 10000000}')) as patient_gender" +
                " FROM" +
                " astm_configuration" +
                " where assay_no3 is not null and machine_brand_id=(select id from machine_brand where name='${machineBrand}')" +
                " and test_type_id in" +
                " ( SELECT  test_type_id FROM" +
                " sample_test where sample_id='${new Long(sampleId) % 10000000}' and test_type_status in ('NORMAL','TRANSFERRED') " +
                 ((branchId!=null)?(" and  to_branch_id='"+branchId+"'"):"")+ ")"


        def data = sql.rows(query)
        def finalList = []
        OrderInfo orderInfo


        data?.each{it ->


            orderInfo = new OrderInfo()

            orderInfo.patientName = it["sample_code"] as String
            orderInfo.patientGender = it["patient_gender"] as String
            orderInfo.assayNumber = it["assay_no"]


//            println("assayNumber : "   +   orderInfo.assayNumber)
//            println("patientGender : " + orderInfo.patientGender)
//            println("patientName : "    +   orderInfo.patientName)


            finalList << orderInfo

            orderInfo = null
        }



        return finalList

    }



    Boolean transferDataFromMachine(List<TestInfo>resultList , String machineBrand) {
        try{
            if(resultList?.size()>0){

                def ctx = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext.getAttribute(org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes.APPLICATION_CONTEXT);
                SessionFactory sessionFactory = ctx.sessionFactory
                def sql = new Sql(sessionFactory.getCurrentSession().connection())


                String query = "Insert into category_value_from_machine " +
                        " (category_value, original_value, date_created, read_number, sample_id, test_type_id, sample_category_id, sample_barcode, Machine_id , machine_brand , assay_number , machine_test_name , machine_unit , machine_result) " +
                        " Values " +
                        " (?,?, sysdate ,0,?,?,0,?,?,?,?,?,?,?)"
                int insertedData =0

                resultList?.each {
                    if (it.sampleId && it.sampleId.isNumber()) {
                        insertedData = sql.executeUpdate(query,[it.testValue,it.testValue,(new Long(it.sampleId)%10000000),it.testCode,it.sampleId,new Long(it.machineId),machineBrand ,(machineBrand == "Mindray-BC20" ? it.testName : it.testCode), it.testName , it.testUnit , it.testValue])
                        if(insertedData<1){
                            throw new Exception("Error in save data ...")
                        }
                    }
                }
                return true
            }else{
                return false
            }
        }catch(Exception e){
            e.printStackTrace()
            return false
        }
    }
}