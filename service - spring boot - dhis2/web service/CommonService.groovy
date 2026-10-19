package com.ubs.portal.common

import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import com.ubs.portal.enums.FileDirectory
import grails.transaction.Transactional
import org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.mock.web.MockHttpServletRequest

import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import grails.orm.PagedResultList
import org.springframework.context.i18n.LocaleContextHolder as LCH
import grails.converters.JSON
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import javax.servlet.http.HttpSession
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.context.NoSuchMessageException
import org.springframework.context.MessageSource

import java.util.concurrent.TimeUnit
import java.nio.file.Files

@Transactional
class CommonService {

    static transactional = false
    def grailsApplication
    MessageSource messageSource
    def springSecurityService

    Date parseDate(String strDate) {
        if (strDate) {
            return DateUtils.parseDate(strDate)
        }
        return null
    }

    Class getDomainClassByServiceName(serviceName) {
        String className = serviceName.replace("Service", "")
        if (className) {
            return grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz
        }
    }

    Class getDomainClassByControllerName(controllerName) {
        String className = controllerName.replace("Controller", "")
        if (className) {
            return grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz
        }
    }

    Class getDomainClassByDomainClassName(domainName) {
        String className = domainName
        if (className) {
            return grailsApplication.domainClasses.find { it.clazz.simpleName.equalsIgnoreCase(className) }.clazz
        }
    }

    Class getServiceNameByDomainClass(String domainClassName) {
        if (domainClassName) {
            def className = domainClassName.substring(0, 1) + domainClassName.substring(1) + "Service";
            def classObject = grailsApplication.serviceClasses.find { it.clazz.simpleName == className }.clazz
            return classObject
        }
    }

    String getBaseUrl(request) {
        return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath()
    }

    public String replaceSpecialChar(String stringName) {
        if (stringName) {
            stringName = "%" + stringName?.trim()
            stringName = stringName.replace("ة", "_");
            stringName = stringName.replace("ه", "_");
            stringName = stringName.replace("أ", "_");
            stringName = stringName.replace("إ", "_");
            stringName = stringName.replace("آ", "_");
            stringName = stringName.replace("ا", "_");
            stringName = stringName.replace(" ", "%");
            stringName = stringName + "%"
            return stringName
        }
        return null;
    }

    public String replaceSpecialCharForRemoting(String stringName) {
        if (stringName) {
            stringName = stringName.replace("ة", "_");
            stringName = stringName.replace("ه", "_");
            stringName = stringName.replace("أ", "_");
            stringName = stringName.replace("إ", "_");
            stringName = stringName.replace("آ", "_");
            stringName = stringName.replace("ا", "_");
            stringName = stringName.replace(" ", "%");
        }

        return stringName;
    }

    public String replaceSpecialCharRegularExpression(String stringName) {
        stringName = ".*" + stringName.trim()
        stringName = stringName.replace("ة", ".");
        stringName = stringName.replace("ه", ".");
        stringName = stringName.replace("أ", ".");
        stringName = stringName.replace("إ", ".");
        stringName = stringName.replace("آ", ".");
        stringName = stringName.replace("ا", ".");
        stringName = stringName.replace(" ", "\\s");
        stringName = stringName + ".*"
        return stringName;
    }

    public Map convertParamsToMap(GrailsParameterMap params) {

        def keySet = params.keySet()
        def newMap = [:]

        keySet.each {
            def itemToGet = params.get(it)
            if (!(itemToGet instanceof GrailsParameterMap)) {
                newMap.put(it, params.get(it))
            }
        }
        return newMap;
    }

    public GrailsParameterMap convertMapToParams(Map map) {

        GrailsParameterMap searchParams = new GrailsParameterMap(map, new MockHttpServletRequest())

        return searchParams;
    }

    /**
     *
     * @param source file to be copied
     * @param destination file name
     */
    def copyFile(File source, File destination) {
        def reader = source.newReader()
        if (source.exists() && source.canRead()) {
            if (!destination.exists()) {
                destination.withWriter('UTF-8') { writer ->
                    writer << reader
                }
            }

        }

        reader.close()
    }

    def getMessage(String messageCode, Locale locale = null) {
        if(!locale)
            locale = LocaleContextHolder.locale
        messageSource.getMessage(messageCode, null, locale)
    }
    /**
     * @param array : the source array to partition
     * @param size : number of partitions
     * @return an array split to the number of partitions
     */
    def partition(array, size) {
        def partitions = []
        int partitionCount = array.size() / size

        partitionCount.times { partitionNumber ->
            def start = partitionNumber * size
            def end = start + size - 1
            partitions << array[start..end]
        }

        if (array.size() % size) partitions << array[partitionCount * size..-1]
        return partitions
    }

    public mapDTOInstance(obj, ctx) {

        // Get the appropriate domain class for this DTO instance.
        def dtoClass = obj.getClass()
        def dtoClassName = dtoClass.name
        def domainClassName = dtoClassName.substring(0, dtoClassName.lastIndexOf('DTO'))
        def domainClass = dtoClass.classLoader.loadClass(domainClassName)

        // Now convert the DTO instance to a domain object.
        def mapper = ctx.getBean('dozerMapper')
        def toReturnObject = mapper.map(obj, domainClass)
        return toReturnObject
    }

    def String getDefaultLanguageCode() {
        def languageWithLocale = grailsApplication.config.default_locale //Ex. ar_PS
        return languageWithLocale.split("_")[0]
    }

    def String getCurrentLanguageCode(session) {
        def locale = session.'org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE'
        if (!locale) {
            locale = new Locale('ar', 'PS', 'Palestine')
        }
        return locale.language
    }

    /// this to determine Employee age
    public Integer getEmployeeAge(Date dateOfBirth) {

        int age = 0;
        Calendar born = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        if (dateOfBirth != null) {
            now.setTime(new Date());
            born.setTime(dateOfBirth);


            if (born.after(now)) {
                // throw new IllegalArgumentException("Can't be born in the future");
            }
            age = now.get(Calendar.YEAR) - born.get(Calendar.YEAR);


            if (now.get(Calendar.MONTH) < born.get(Calendar.MONTH)) {
                age -= 1;
            }
        }
        return age;


    }
/**
 *
 * @param dateString : String pattern
 * @return date object with dd/MM/yyyy HH:mm pattern (i.e. 12/04/2001 16:33)
 */
    def Date manageDates(dateString) {

        def val = null

        if (!dateString || dateString.length() == 0 || dateString.length() == 1) {
            val = null

        } else {
            val = manageDates(dateString, "dd/MM/yyyy HH:mm")
        }
        return val
    }

    /**
     *
     * @param dateString : String pattern
     * @param pattern : mask of the date as a pattern (i.e dd/MM/yyyy)
     * @return date object
     */
    def Date manageDates(String dateString, String pattern) {

        Date dateParsed

        if (!dateString || dateString.length() == 0 || dateString.length() == 1)
            return

        // Making sure that every thing is just fine
        try {
            def formatter = new SimpleDateFormat(pattern);
            dateParsed = formatter.parse(dateString);
        } catch (Exception e) {
            e.printStackTrace()
            dateParsed = null
        }
        return dateParsed

    }

    /**
     *
     * @param domainInstanceToClone
     * @return
     */
    def deepDomainClassCloneSimple(domainInstanceToClone) {
        try {
            def result = domainInstanceToClone.getClass().newInstance()
            domainInstanceToClone?.persistentProperties.each { prop ->


            }
            result.properties = domainInstanceToClone.properties
            return result
        } catch (Exception e) {
        }
    }

    /**
     * @desc : checks the incoming attribute and converts
     * a java.sql.timestamp object to a String date formatted with the
     * provided pattern. A java.sql.timestamp object.toString() method always returns the format of
     * dd-MM-yyyy HH:mm:ss , so this method is useful to reformat the object again
     * to the mentioned pattern.
     * @param timeStamp : the object to test
     * @param pattern : the date pattern , default is "dd/MM/yyyy"
     * @return a string date formatted by the provided pattern , or the object.toString() in-case not
     * a java.sql.timestamp
     */
    public String convertSQLTimeStampToDate(timeStamp, String pattern = "dd/MM/yyyy") {

        if (timeStamp instanceof java.sql.Timestamp) {
            return timeStamp.format(pattern)
        }

        return timeStamp
    }


    public List convertCommaSeparatedStringToList(String str) {
        //http://stackoverflow.com/questions/7488643/java-how-to-convert-comma-separated-string-to-arraylist
        if (!str)
            return []
        return Arrays.asList(str.split("\\s*,\\s*"));
//        StringTokenizer st = new StringTokenizer(str, ",")
//        def resultList = []
//
//        while (st.hasMoreTokens()) {
//            def token = st?.nextToken()?.toString()?.trim();
//            if (token != null && token.trim().length() > 0 && token != ',')
//                resultList <<  new Long(token)
//        }
//        return resultList
    }

    /**
     * This will try to return a new date object based on
     * the date supplied and supplied pattern
     * @param dateString
     * @return dateParsed
     */
    def String getFormattedDate(String pattern, date) {
        try {
            if (pattern && pattern.size() > 0)
                return new SimpleDateFormat(pattern).format(date);
            else
                return new SimpleDateFormat("HH:mm dd/MM/yyyy").format(date);
        }
        catch (e) {
            return "";
        }
    }

    /**
     * This will return a map of column and value(data) object based on   searchResult
     * the date supplied and supplied pattern
     * @param dateString
     * @return dateParsed
     */
    public Map generateRecordToMap(def objectInstance, boolean convertLists = true) {
        def mapToRender = [:]
        try {
            def filtered = ['class', 'active', 'metaClass', 'errors', 'constraints', 'log', 'springSecurityService']
            def props = objectInstance.properties.collect { it }.findAll { !filtered.contains(it.key) }
            def value
            props.each {
                value = it?.value
                if (value instanceof Set && convertLists) {
                    value = value?.toString()
                    mapToRender.put(it?.key, value.substring(1, value?.length() - 1))
                } else {
                    mapToRender.put(it?.key, it?.value)
                }
                value = null
            }
        }
        catch (e) {
            e.printStackTrace()
        }
        return mapToRender
    }

    /**
     * This will return a map of column and value(data) object based on   searchResult
     * the date supplied and supplied pattern
     * @param dateString
     * @return dateParsed
     */

    public Map generateDataToRender(List dtpColumns, PagedResultList searchResult, GrailsParameterMap params) {
        int total = searchResult ? searchResult.totalCount : 0
        return generateDataToRender(dtpColumns, searchResult, params, total)
    }

    public Map generateDataToRender(List dtpColumns, List searchResult, GrailsParameterMap params, int total) {
        def dataToRender = [:]
        try {
            def rowsToExclude = params.list("rowsToExclude")?.findAll { it != null }?.collect { it as long }
            if (rowsToExclude)
                total = ((total - rowsToExclude.size()) >= 0) ? (total - rowsToExclude.size()) : 0
            dataToRender.put('sEcho', params.sEcho)
            dataToRender.put('iTotalRecords', total)
            dataToRender.put('iTotalDisplayRecords', total)
            dataToRender.aaData = []
            def columnName = ""
            def columnType = ""
            def columnSource = ""
            def messagePrefix = ""
            def classPath = ""
            searchResult?.each { rec ->
//                if (!rowsToExclude?.contains(rec.id)) {
                    def record = [:]
                    for (int i = 0; dtpColumns != null && i < dtpColumns.size(); i++) {
                        columnName = dtpColumns[i].name
                        columnType = dtpColumns[i].type
                        columnSource = dtpColumns[i].source
                        messagePrefix = dtpColumns[i]?.messagePrefix
                        classPath = dtpColumns[i]?.classPath
                        if (columnSource == "domain") {
                            def fields = columnName.toString().split("\\.")
                            switch (fields.length) {
                                case 2:   // two levels
                                    def secondLevel = [:]

                                    secondLevel[fields[1]] = rec[fields[0]] ? getFormat(rec[fields[0]][fields[1]], columnType, messagePrefix, classPath) : ""
                                    if (record[fields[0]])
                                        record[fields[0]][fields[1]] = secondLevel[fields[1]]
                                    else
                                        record[fields[0]] = secondLevel
                                    break;
                                case 3:  // three levels
                                    def secondLevel = [:]
                                    def thirdLevel = [:]
                                    thirdLevel[fields[2]] = rec[fields[0]] && rec[fields[0]][fields[1]] ? getFormat(rec[fields[0]][fields[1]][fields[2]], columnType, messagePrefix, classPath) : ""
                                    secondLevel[fields[1]] = thirdLevel
                                    if (record[fields[0]] && record[fields[0]][fields[1]])
                                        record[fields[0]][fields[1]][fields[2]] = secondLevel[fields[1]][fields[2]]
                                    else if (record[fields[0]])
                                        record[fields[0]][fields[1]] = secondLevel[fields[1]]
                                    else
                                        record[fields[0]] = secondLevel
                                    break;
                                case 4:  // four levels
                                    def secondLevel = [:]
                                    def thirdLevel = [:]
                                    def fourthLevel = [:]
                                    fourthLevel[fields[3]] = rec[fields[0]] && rec[fields[0]][fields[1]] && rec[fields[0]][fields[1]][fields[2]] ? getFormat(rec[fields[0]][fields[1]][fields[2]][fields[3]], columnType, messagePrefix, classPath) : ""
                                    thirdLevel[fields[2]] = fourthLevel
                                    secondLevel[fields[1]] = thirdLevel
                                    if (record[fields[0]] && record[fields[0]][fields[1]][fields[2]])
                                        record[fields[0]][fields[1]][fields[2]][fields[3]] = secondLevel[fields[1]][fields[2]][fields[3]]
                                    else if (record[fields[0]][fields[1]][fields[2]])
                                        record[fields[0]][fields[1]][fields[2]] = secondLevel[fields[1]][fields[2]]
                                    else if (record[fields[0]])
                                        record[fields[0]][fields[1]] = secondLevel[fields[1]]
                                    else
                                        record[fields[0]] = secondLevel
                                    break;
                                default: // one level
                                    //in case of custom formats a closure could be passed in type variable
                                    //containing formatter
                                    if (columnType instanceof Closure)
                                        record[columnName] = columnType(this, rec, rec.properties.containsKey(columnName) ? rec[columnName] : null, params)
                                    else
                                        record[columnName] = getFormat(rec[columnName], columnType, messagePrefix, classPath)
                                    break;
                            }
                        } else {
                            record[columnName] = 'remoting value'
                        }
                    }
                    dataToRender.aaData.push(record)
//                }
            }
        }
        catch (e) {
            e.printStackTrace()
        }
        return dataToRender
    }

    public String getFormat(Object object, String type, String messagePrefix, String classPath = null) {
        def val
        switch (type) {
            case "boolean":
                if (object)
                    return messageSource.getMessage("default.boolean.true", null, "default.boolean.true", LCH.getLocale())
                else
                    return messageSource.getMessage("default.boolean.false", null, "default.boolean.false", LCH.getLocale())
                break;
            case "date":
                return object?.format("dd/MM/yyyy")
                break;
            case "dateTime":
                return object?.format("dd/MM/yyyy HH:mm")
                break;
            case "enum":
                if (object) {
                    if (messagePrefix) {
                        return messageSource.getMessage("${messagePrefix}.${object}", null, "${messagePrefix}.${object}", LCH.getLocale())
                    } else {
                        return "Unknow messagePrefix"
                    }

                } else {
                    return object?.toString()
                }
                break;
            case "enumLong":
                if (object) {
                    if (messagePrefix) {
                        if (classPath) {
                            def myClass = java.lang.Class.forName(classPath, false, grailsApplication.classLoader)
                            if (myClass?.isEnum()) {
                                object = myClass.values().find { it.value() == object }
//                            def object2 = myClass.values().find {it.value() == object}
                                return messageSource.getMessage("${messagePrefix}.${object}", null, "${messagePrefix}.${object}", LCH.getLocale())
                            } else {
                                return "${object} of ${messagePrefix} is not Enum or not found"
                            }
                        } else {
                            return "Unknow classPath"
                        }
                    } else {
                        return "Unknow messagePrefix"
                    }

                } else {
                    return object?.toString()
                }
                break;
            default:
                return object?.toString()
                break;
        }
    }

    def getEnumValues(Class enumClass, String messagePrefix = null) {
        def result = []

        if (!enumClass)
            throw new Exception("commonService.getEnumValues is missing required attribute enumClass.")
        if (!messagePrefix)
            messagePrefix = enumClass.simpleName[0].toLowerCase() + enumClass.simpleName.substring(1)
        if (enumClass.isEnum()) {
            enumClass.values().each {
                result <<
                        [
                                id  : it,
                                name: messageSource.getMessage("${messagePrefix}.${it}", null, "${it}", LCH.getLocale())
                        ]
            }
        } else {
            throw new Exception("${enumClass} is not an Enum")
        }
        return result?.sort { it?.name }
    }

    public Object transferErrorsFromDomainToAnother(String sourcePrefix, Object source, Object destination) {

        def globalErrors = source?.errors?.globalErrors
        def fieldErrors = source?.errors?.fieldErrors

        if (fieldErrors) {
            def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
            def d = new DefaultGrailsDomainClass(source.class)
            def props = d.persistentProperties
            fieldErrors.each {
                if (props.find { it2 -> it2.name == it.field }) {
                    destination?.errors?.rejectValue(sourcePrefix + "" + it?.field, g.fieldError(field: it?.field, bean: source).toString())

                } else {
                    destination?.errors?.rejectValue(sourcePrefix + "" + it?.field, it.code)

                }
            }
        }
        globalErrors.each {
            destination?.errors?.reject(it.code)

        }
        return destination
    }

    public Map getDomainClassValidationErrors(String action, Object instance, String successMessage, String failMessage = null) {
        if (!instance)
            throw new Exception("getDomainClassValidationErros is missing required attribute [instance]")
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def defaultFailMessage
        def defaultSuccessMessage
        switch (action) {
            case "select":
                defaultFailMessage = messageSource.getMessage("default.select.error", null, "default.select.error", LCH.getLocale())

                break;
            case "create":
                defaultFailMessage = messageSource.getMessage("default.create.error", null, "default.create.error", LCH.getLocale())

                break;
            case "update":
                defaultFailMessage = messageSource.getMessage("default.update.error", null, "default.update.error", LCH.getLocale())

                break;
            case "delete":
                defaultFailMessage = messageSource.getMessage("default.delete.error", null, "default.delete.error", LCH.getLocale())

                break;
            default:
                defaultFailMessage = messageSource.getMessage("default.create.error", null, "default.create.error", LCH.getLocale())

                break;

        }
        def globalErrors = instance?.errors?.globalErrors
        def fieldErrors = instance?.errors?.fieldErrors
        def errorsMap = []
        if (fieldErrors) {
            def d = new DefaultGrailsDomainClass(instance.class)
            def props = d.persistentProperties

            fieldErrors.each {
                def prop = props.find { it2 -> it2.name == it.field }
                if (prop) {
                    errorsMap.push(field: it?.field, message: g.fieldError(field: it?.field, bean: instance).toString())
                    props = props - prop
                } else {
                    errorsMap.push(field: it?.field, message: it.code)
                }
            }
        }
        globalErrors.each {
//            if(it.code == "default.internal.server.error"){
//                println it
//                errorsMap.push(field: "global", message:  messageSource.getMessage("default.internal.server.error", null, "default.internal.server.error", LCH.getLocale()))
//            }     else
            errorsMap.push(field: "global", message: messageSource.getMessage(it.code, null, it.code, LCH.getLocale()))
        }
        def json = [:]
        json.success = instance && !instance?.hasErrors();
        json.genMessage = json.success ? (successMessage ?: defaultSuccessMessage) : (failMessage ?: defaultFailMessage)
        json.message = json.success ? (successMessage ?: defaultSuccessMessage) : errorsMap;
        json.data = json.success ? instance.toString() : null
        return [text: (json as JSON), contentType: "application/json"]
    }

    public Map getMessageSourceMessage(String fieldName, String msgCode, ArrayList args) {
        def resultMsg = "MESSAGE-NOt-FOUND"

        def argsMessages = []
        args.each {
            try {
                argsMessages << messageSource.getMessage(it, null, LCH.getLocale())
            } catch (Exception e) {
                argsMessages << it
            }
        }
        try {
            if (!messageSource)
                return [:]
            resultMsg = messageSource.getMessage(msgCode, argsMessages as Object[], LCH.getLocale())
        } catch (NoSuchMessageException e) {
            resultMsg = msgCode
        }

        def resultMap = [:]
        resultMap.fieldName = fieldName
        resultMap.message = resultMsg
        return resultMap

    }

    GrailsParameterMap trimSpacesFromParams(GrailsParameterMap params) {
        params.each { k, v ->
//            println " K : "+ " v :"+v
            if (v instanceof String) {
                params[k] = v.trim()
            }
        }
        return params
    }

    public HttpSession getSession() {
        return RequestContextHolder.currentRequestAttributes().getSession()
    }

    def sessionFactory
    def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

    def cleanUpGorm() {
        def session = sessionFactory.currentSession
        session.flush()
        session.clear()
        propertyInstanceMap?.get()?.clear()
    }

    /**
     * this will remove onToMany objects from given instance properties if these properties
     * are not provided in params when assigning params to instance properties
     * @param params , instance
     * @return instance
     */
    def removeEmptyLists(params, instance) {
        new DefaultGrailsDomainClass(instance.class).persistentProperties.each { property ->
            if (!params[property.name + ".id"] && property?.oneToMany) {
                instance[property.name].each {
                    instance."removeFrom${property.name[0].toUpperCase() + property.name.substring(1)}"(it)
                    it.delete()
                }
            }
        }
        instance.validate()
        return instance
    }
    /**
     * this trim the spaces from parameters .
     * this will prevent saving empty spaces for the domain classes that does not contain validations
     * @param A Map of parameters [Key , Value]
     * @return A Trimmed Map of parameters [Key , Value]
     */
    Map trimSpacesFromParams(Map params) {
        params.each { k, v ->
            if (v instanceof String) {
                params[k] = v.trim()
            }
        }
        return params
    }


    public List<Map> getDomainClassErrors(Object instance, String languageCode = null) {
        if (!instance)
            throw new Exception("getDomainClassErrors is missing required attribute [instance]")

        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        def defaultFailMessage
        def defaultSuccessMessage
        def globalErrors = instance?.errors?.globalErrors
        def fieldErrors = instance?.errors?.fieldErrors
        def localeInstance = LCH.getLocale()
        if (languageCode) {
            localeInstance = new Locale(languageCode, languageCode)
        }
        def errorsMap = []
        if (fieldErrors) {
            def d = new DefaultGrailsDomainClass(instance.class)
            def props = d.persistentProperties
            fieldErrors.each {
                def prop = props.find { it2 -> it2.name == it.field }
                if (prop) {
                    def msg = g.fieldError(field: it?.field, bean: instance)//.toString()
                    errorsMap.push(field: it?.field, message: msg)
                    props = props - prop
                } else {
                    errorsMap.push(field: it?.field, message: g.message(code: it.code, default: it.code))
                }
            }
        }

        globalErrors.each {
            errorsMap.push(field: "global", message: messageSource.getMessage(it.code, null, it.code, localeInstance))
        }
        return errorsMap
    }

    /**
     * @desc : This method is used to generate sequence number
     *
     */
    public String generateSystemSequenceNumber(String domainClassName, String source, int stringLength) {
        int length = stringLength - (source.length());
        def domainClass = grailsApplication.getDomainClass(domainClassName).clazz
        def maxNumber = domainClass.createCriteria().get { projections { max('id') }; }
        def number = source;
        if (maxNumber) {
            maxNumber++;
        } else {
            maxNumber = 1;
        }
        for (int i = 0; i < length - (maxNumber.toString().length()); i++) {
            number += "0"
        }
        number += maxNumber.toString();
        return number
    }


    /**
     * @desc : This method is used to copy the same attribute from object to another object
     *
     */
    def copyProperties(def sourceObject, def targetObject, def excludeList) {
        try {
            targetObject.properties.each { key, value ->
                if (sourceObject.hasProperty(key) && !(key in [
                        'class',
                        'metaClass',
                        'belongsTo',
                        'hasMany',
                        'serialVersionUID'
                ]) && excludeList.contains(key) == false)
                    targetObject[key] = sourceObject[key]
            }
        }
        catch (e) {
            sourceObject.properties.each { key, value ->
                if (targetObject.hasProperty(key) && !(key in [
                        'class',
                        'metaClass',
                        'belongsTo',
                        'hasMany',
                        'serialVersionUID'
                ]) && excludeList.contains(key) == false)
                    targetObject[key] = value
            }
        }
    }

    String formatIslamicDate(String date, String patternParse = null, String patternView = null) {
        if (!patternParse) patternParse = "d/M/y"
        if (!patternView) patternView = "EEEE d MMMM, y"
        Locale locale = LocaleContextHolder.getLocale();
        UmmalquraCalendar uCal = new UmmalquraCalendar(locale);
        SimpleDateFormat dateFormat = new SimpleDateFormat("", locale);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormat.setCalendar(uCal);
        Calendar cal = new UmmalquraCalendar(locale);
        dateFormat.applyPattern(patternParse);
        cal.setTime(dateFormat.parse(date));
        dateFormat.applyPattern(patternView);
        String finalDate = dateFormat.format(uCal.getTime())
        return finalDate
    }

    Integer getDuringInteger(int period, int interval) {
        if (interval && period) {
            Integer minutes
            if (period == 1) {
                minutes = (interval * 24 * 60)
            } else if (period == 2) {
                minutes = (interval * 60)
            } else if (period == 3) {
                minutes = interval
            } else {
                minutes = 0
            }
            return minutes
        }
        return null
    }
    Date getDuringDate(int period, int interval) {
        if (interval&&period) {
            Integer minutes = getDuringInteger(period,interval)
            Date currentDate= new Date()
            Calendar cal = Calendar.getInstance();
            cal.setTime(currentDate);
            cal.add(Calendar.MINUTE, minutes);
            Date newDate = cal.getTime();
            return newDate
        }

        return null
    }


    public Integer CalculateAgeByYear(Date dateOfBirth) {
        int age = 0;
        Calendar born = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        if (dateOfBirth != null) {
            now.setTime(new Date());
            born.setTime(dateOfBirth);
            age = now.get(Calendar.YEAR) - born.get(Calendar.YEAR);
            if (now.get(Calendar.MONTH) < born.get(Calendar.MONTH)) {
                age -= 1;
            }


        }
        return age;
    }

    public Integer calculateAgeByDays(Date dateOfBirth) {
        int days = 0;
        Calendar born = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        if (dateOfBirth != null) {
            now.setTime(new Date());
            born.setTime(dateOfBirth);
            days = now - born
        }
        return days;
    }


    def calculatePeriod(Date fromDate, Date toDate) {
        ValidationTagLib validationTagLib = new ValidationTagLib()
        Float dateInterval = DateUtils.calculateDateInterval(toDate, fromDate, TimeUnit.MILLISECONDS)

        long diffSeconds = dateInterval / 1000 % 60;
        long diffMinutes = dateInterval / (60 * 1000) % 60;
        long diffHours = dateInterval / (60 * 60 * 1000) % 24;
        long diffDays = dateInterval / (24 * 60 * 60 * 1000);

        def result = ""

        def separator = validationTagLib.message(code: 'seperator.and') + " "


        if (diffDays > 0)
            result = separator + diffDays + " " + validationTagLib.message(code: 'total.days') + " "

        if (diffHours > 0)
            result += separator + diffHours + " " + validationTagLib.message(code: 'total.hours') + " "

        if (diffMinutes > 0)
            result += separator + diffMinutes + " " + validationTagLib.message(code: 'total.minutes') + " "

        if (diffSeconds > 0)
            result += separator + diffSeconds + " " + validationTagLib.message(code: 'total.seconds') + " "

        if (result?.length() > 0)
            result = result.substring(1, result.length())

        return result;

    }

    static String truncate(String s, int length = 30) {
        if (s) {
            length = Math.min(s.length(), length) // adjust to string end
            def buf = s.substring(0, length)
            return buf + (buf.length() < s.length() ? " ..." : "")
        }
        return ""
    }



    private static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        // Get the size of the file
        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            // File is too large
            return null
        }
        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) length];
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }
        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file " + file.getName());
        }
        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    def getAge(dateCreated){
        use(groovy.time.TimeCategory) {
            def duration = new Date() - dateCreated
            if(duration.days>0){
                return duration.days + " " + messageSource.getMessage("home.age.day.label", null, "Days", LCH.getLocale())

            }else{
                duration.hours + " " + messageSource.getMessage("home.age.hour.label", null, "Hours", LCH.getLocale())
            }

        }
    }

    public
    synchronized File getFile(FileDirectory fileDirectory, String name) {
        File file;
        try {
            String directory = this.getDirectoryPath(fileDirectory) + File.separator + name;
            file = new File(directory)
            if(file && file.exists()){
                return file
            }
        } catch (Exception e) {
            file = null
        }
        return file
    }

    String getDirectoryPath(FileDirectory directory) {
        def parentDirectory = grailsApplication.config.systemFilePath.directory
        def parentFileDirectory = grailsApplication.config.systemFilePath.parentFileDirectory
        switch (directory) {
            case FileDirectory.TEST:
                def testDirectory = grailsApplication.config.systemFilePath.testFileDirectory
                def fullDirectory = parentDirectory + File.separator + parentFileDirectory + File.separator + testDirectory
                createDirectory(fullDirectory)
                return fullDirectory
                break;
            case FileDirectory.SAMPLE:
                def sampleDirectory = grailsApplication.config.systemFilePath.sampleFileDirectory
                def fullDirectory = parentDirectory + File.separator + parentFileDirectory + File.separator + sampleDirectory
                createDirectory(fullDirectory)
                return fullDirectory
                break;
            case FileDirectory.ARCHIVE_RESULT:
                def archiveResultsFileDirectory = grailsApplication.config.systemFilePath.archiveResultsFileDirectory
                def fullDirectory = parentDirectory + File.separator + parentFileDirectory + File.separator + archiveResultsFileDirectory
                createDirectory(fullDirectory)
                return fullDirectory
                break;
            default:
                return null
                break;
        }
    }


    void createDirectory(String path) {
        try {
            File file = new File(path)
            if (!file.exists())
                file.mkdir()
        } catch (Exception e) {
           e.printStackTrace()
        }
    }

    String passwordGenerator(String terminalSerial, String terminalId,
                                           String userName, long requestedTime, String timeZone) {
        if (terminalSerial != null
                && terminalSerial.trim().length() >= 4
                && terminalId != null
                && terminalId.trim().length() > 0
                && userName != null
                && userName.trim().length() > 0) {


            Calendar cal = null;

            if (timeZone != null)
                cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone), new Locale("en_US"));
            else
                cal = Calendar.getInstance();

            if (requestedTime >= 0) {
                cal.setTimeInMillis(requestedTime);
            }

            int milliSecond = cal.get(Calendar.MILLISECOND);
            int sec = cal.get(Calendar.SECOND);
            int min = cal.get(Calendar.MINUTE);
            int hours = cal.get(Calendar.HOUR_OF_DAY);
            int dayOfYeer = cal.get(Calendar.DAY_OF_YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int year = cal.get(Calendar.YEAR);

            if (month == 0 || year == 0 || dayOfYeer == 0) {
                return UUID.randomUUID().toString();
            }

            long eq1 = Long.parseLong(milliSecond + "" + sec + "" + hours + "" + min + "" + dayOfYeer + "" + month + "" + year) * cal.get(Calendar.DAY_OF_WEEK) / month;
            long eq2 = cal.get(Calendar.DAY_OF_MONTH) % month + Integer.parseInt(terminalId);

            String val = (terminalSerial.trim().substring(0, 4) + eq1 + eq2 + userName)
            val = springSecurityService.encodePassword(val, null)
            return val;
        }
        return null;
    }
}

