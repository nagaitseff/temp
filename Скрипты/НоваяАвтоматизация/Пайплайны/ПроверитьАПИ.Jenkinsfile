pipeline {
    agent { label "${params.LABEL}" }

    parameters {
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
        string(name: 'MECHEL_TEST_INFOBASE', defaultValue: '', description: 'Имя тестовой ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        string(name: 'TOKEN_CREDENTIAL_ID', defaultValue: 'erp-restore-api-token', description: 'ID Jenkins Secret text')
    }

    stages {
        stage('Проверка API оркестратора') {
            steps {
                script {
                    def testBase = params.MECHEL_TEST_INFOBASE.trim()
                    def credentialId = params.TOKEN_CREDENTIAL_ID.trim()
                    if (!testBase || !credentialId) {
                        error('Заполните MECHEL_TEST_INFOBASE и TOKEN_CREDENTIAL_ID.')
                    }
                    dir('НоваяАвтоматизация') {
                        withCredentials([string(credentialsId: credentialId, variable: 'MECHEL_API_TOKEN')]) {
                            withEnv(['MECHEL_TEST_INFOBASE=' + testBase]) {
                                bat '@chcp 65001 >nul\n@"C:\\OneScript_2\\bin\\oscript.exe" ".\\Скрипты\\Примеры\\ПроверитьАПИ.os"'
                            }
                        }
                    }
                }
            }
        }
    }
}
