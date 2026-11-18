package io.libs

def cmdutf8_output(command) {
	def output = bat(returnStdout:true, script: "@chcp 65001 > nul \n @${command}")
	return output
}

def webRequest(mapParam) {
    def url = mapParam['url'] + mapParam['secretAddr']
    def commandString = "curl.exe --header \"X-Vault-Token: ${mapParam.token}\" --request ${mapParam.method} ${url}"
	println "curl.exe --header \"X-Vault-Token: hiddentoken\" --request ${mapParam.method} ${url}"
    def response = cmdutf8_output(commandString)
    def responseJson = new groovy.json.JsonSlurper().parseText(response)
    return responseJson
}
		
def getVaultCluster1cAuth(String cluster1c, url, token) {
	def serverName = cluster1c.split(':')[0]
	def serverPort = cluster1c.split(':')[1]
	def serverDigitSuffix = (serverName =~ /\d.*$/)[0] //regex ищет цифры в имени сервера
	
	def mapParam = [:]
	mapParam['url'] = url
	mapParam['secretAddr'] = '/v1/clusters1c/metadata'
	mapParam['method'] = 'LIST'
	mapParam['token'] = token
	def response
	response = webRequest(mapParam) //получение списка серверов
	if (response.data.keys.contains(serverName)) {
		mapParam.put('secretAddr', "/v1/clusters1c/data/${serverName}")
		mapParam.put('method', 'GET')
		response = null
		response = webRequest(mapParam) //секрет по имени серверов
		if (response.data.data.keySet().contains(serverPort)) {
			return ["${serverDigitSuffix}_clust_admin", response.data.data."$serverPort"]
		}
	}
	
	def noAuthKey = 'noauth' //key списка кластеров без авторизации
	mapParam.put('secretAddr','/v1/clusters1c/data/default')
	mapParam.put('method', 'GET')
	response = null
	response = webRequest(mapParam)
	if (response.data.metadata.custom_metadata.keySet().contains(noAuthKey)) { //строка-список кластеров без авторизации
		def listNoAuthClusters = response.data.metadata.custom_metadata."$noAuthKey".split(',').collect{it.trim()}
		if (listNoAuthClusters.contains(cluster1c)) {
			return ['', '']
		}
	}
	return ["${serverDigitSuffix}_clust_admin", response.data.data."_clust_admin"] //дефолтный секрет
}

def getVaultInfobaseAuth(String infobaseName, url, token) {
	def mapParam = [:]
	mapParam['url'] = url
	mapParam['secretAddr'] = '/v1/infobases/metadata'
	mapParam['method'] = 'LIST'
	mapParam['token'] = token
	def keyName
	def response
	response = webRequest(mapParam)
	if (response.data.keys.contains(infobaseName)) {
		mapParam.put('secretAddr', "/v1/infobases/data/${infobaseName}")
		mapParam.put('method', 'GET')
		response = null
		response = webRequest(mapParam)
		keyName = response.data.data.keySet()[0]
		return [keyName, response.data.data[keyName]]
	}

	mapParam.put('secretAddr','/v1/infobases/data/default')
	mapParam.put('method', 'GET')
	response = null
	response = webRequest(mapParam)
	keyName = response.data.data.keySet()[0]
	return [keyName, response.data.data[keyName]]
}