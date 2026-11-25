pipeline {
    agent { label "${params.LABEL}" }

    parameters {
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
        string(name: 'MECHEL_TEST_INFOBASE', defaultValue: '', description: 'Имя тестовой ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        credentials(
            name: 'TOKEN',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-restore-api-token',
            required: true,
            description: 'API-токен для блокировки информационной базы'
        )
    }

    stages {
        stage('Проверка API оркестратора') {
            steps {
                script {
                    def testBase = params.MECHEL_TEST_INFOBASE.trim()
                    if (!testBase) {
                        error('Заполните MECHEL_TEST_INFOBASE.')
                    }
                    dir('НоваяАвтоматизация') {
                        withCredentials([string(credentialsId: params.TOKEN, variable: 'MECHEL_API_TOKEN')]) {
                            withEnv(['MECHEL_TEST_INFOBASE=' + testBase]) {
                                bat '@chcp 65001 >nul\n@"C:\\OneScript_2\\bin\\oscript.exe" "C:\\OneScript_2\\api\\ПроверитьАПИ.os"'
                            }
                        }
                    }
                }
            }
        }
    }
}
