package io.libs

import java.text.SimpleDateFormat
import java.util.Calendar
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field String API_URL = "https://ukm-web/ad/IT/IT_TechAdmin/hs/loading1c"
@Field String API_URL_Objects1C = "https://ukm-web/ad/IT/IT_TechAdmin/hs/Objects1C"

def newLoading1C(token, sourceBase, destinationBase, makecopy, comment = "Jenkins loading1C") {

    def safeComment = comment
        .replace("'", "''")
        .replace("\n", " ")
        .replace("\r", " ")

    // Формируем PowerShell-хэш-таблицу как строку (без преобразования в JSON)
    def psHashTable = "@{sourceinfobase = '${sourceBase}'; targetinfobase = '${destinationBase}'; token = '${token}'; makecopy = '${makecopy}'; comment = '${safeComment}'}"

    echo "PowerShell Hash Table: ${psHashTable}"
    echo "API URL: ${API_URL}/new"
    
    def responseCreate = bat(
        script: """
        @echo off
        chcp 65001 > nul
        powershell -Command "\$body = ${psHashTable} | ConvertTo-Json; \$bytes = [System.Text.Encoding]::UTF8.GetBytes(\$body); \$Response = Invoke-WebRequest -Method POST -Uri '${API_URL}/new' -UseDefaultCredentials -Body \$bytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing"; \$Encoding = [System.Text.Encoding]::GetEncoding('utf-8'); \$Encoding.GetString(\$Response.Content)"
        """,
        returnStdout: true
    ).trim()
        
    responseCreate = responseCreate.replace('\uFEFF', '')
    
    echo responseCreate

    def jsonCreate = new groovy.json.JsonSlurper().parseText(responseCreate)

    if (jsonCreate.code != "00.00") {
        error "Ошибка создания заявки: ${jsonCreate.code} ${jsonCreate.description}"
    }

    return jsonCreate.result.toString()
}

def statusLoading1C(token, guid) {

    def responseStatus = bat(
        script: """
        @echo off
        chcp 65001 > nul
        powershell -Command "\$Response = Invoke-WebRequest -Method GET -Uri '${API_URL}/status?guid=${guid}&token=${token}' -UseDefaultCredentials -UseBasicParsing; \$Encoding = [System.Text.Encoding]::GetEncoding('utf-8'); \$Encoding.GetString(\$Response.Content)"
        """,
        returnStdout: true
    ).trim()

    responseStatus = responseStatus.replace('\uFEFF', '').trim()

    echo responseStatus

    def jsonStatus = new groovy.json.JsonSlurper().parseText(responseStatus)

    if (jsonStatus.code != "00.00") {
        error "Ошибка статуса: ${jsonStatus.code} ${jsonStatus.description}"
    }

    return jsonStatus.result as Integer
}

def setInfobaseBlock(
    token,
    infobase,
    sessionsLockEnabled = null,
    lockBeginTime = null,
    lockEndTime = null,
    sessionStartPermissionCode = null,
    lockMessage = null,
    lockParameter = null,
    lockScheduledJobs = null
) {

    def params = []

    params << "token = '${token}'"
    params << "infobase = '${infobase}'"

    if (sessionsLockEnabled != null)
        params << "SessionsLockEnabled = '${sessionsLockEnabled}'"

    if (lockBeginTime)
        params << "LockBeginTime = '${lockBeginTime}'"

    if (lockEndTime)
        params << "LockEndTime = '${lockEndTime}'"

    if (sessionStartPermissionCode)
        params << "SessionStartPermissionCode = '${sessionStartPermissionCode}'"

    if (lockMessage)
        params << "LockMessage = '${lockMessage.replace("'", "''")}'"

    if (lockParameter)
        params << "LockParameter = '${lockParameter.replace("'", "''")}'"

    if (lockScheduledJobs != null)
        params << "LockScheduledJobs = '${lockScheduledJobs}'"

    def psHashTable = "@{${params.join("; ")}}"

    echo "PowerShell Hash Table: ${psHashTable}"
    echo "API URL: ${API_URL_Objects1C}/SetInfobaseBlock"

    def response = bat(
        script: """
        @echo off
        chcp 65001 > nul
        powershell -Command "\$body = ${psHashTable} | ConvertTo-Json; \$bytes = [System.Text.Encoding]::UTF8.GetBytes(\$body); \$Response = Invoke-WebRequest -Method post -Uri '${API_URL_Objects1C}/SetInfobaseBlock' -UseDefaultCredentials -Body \$bytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing; \$Encoding = [System.Text.Encoding]::GetEncoding('utf-8'); \$Encoding.GetString(\$Response.Content)"
        """,
        returnStdout: true
    ).trim()

    response = response.replace('\uFEFF', '').trim()

    echo response

    def json = new groovy.json.JsonSlurper().parseText(response)

    def code = json.code.toString()
    if (code != "0" && code != "00.00") {
        error "Ошибка установки блокировки ИБ: ${code} ${json.description}"
    }

    return new HashMap(json.result)
}

def getInfobaseBlock(token, infobase) {

    def response = bat(
        script: """
        @echo off
        chcp 65001 > nul
        powershell -Command "\$Response = Invoke-WebRequest -Method get -Uri '${API_URL_Objects1C}/GetInfobaseBlock?token=${token}&infobase=${infobase}' -UseDefaultCredentials -UseBasicParsing; \$Encoding = [System.Text.Encoding]::GetEncoding('utf-8'); \$Encoding.GetString(\$Response.Content)"
        """,
        returnStdout: true
    ).trim()

    response = response.replace('\uFEFF', '').trim()

    echo response

    def json = new groovy.json.JsonSlurper().parseText(response)

    def code = json.code.toString()
    if (code != "0" && code != "00:00") {
        error "Ошибка получения блокировок ИБ: ${code} ${json.description}"
    }

    return new HashMap(json.result)
}
