pipeline {
    agent { label "${params.LABEL}" }

    options { skipDefaultCheckout() }

    parameters {
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
        string(name: 'MECHEL_TEST_INFOBASE', defaultValue: '', description: 'Имя тестовой ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        choice(name: 'MECHEL_SOURCE', choices: ['agent', 'checkout'],
               description: 'agent — C:\\OneScript_2; checkout — исходники из SCM')
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
                    def testBase = params.MECHEL_TEST_INFOBASE?.trim()
                    if (!testBase) {
                        error('Заполните MECHEL_TEST_INFOBASE.')
                    }
                    if (!params.TOKEN) {
                        error('Выберите Secret text в параметре TOKEN.')
                    }

                    def engine = 'C:\\OneScript_2\\bin\\oscript.exe'
                    def scriptPath = 'C:\\OneScript_2\\api\\ПроверитьАПИ.os'
                    def sourceMode = params.MECHEL_SOURCE ?: 'agent'
                    if (sourceMode == 'checkout') {
                        checkout scm
                        scriptPath = 'НоваяАвтоматизация\\Скрипты\\Примеры\\ПроверитьАПИ.os'
                        if (!fileExists(scriptPath)) {
                            scriptPath = 'Скрипты\\' + scriptPath
                        }
                    }
                    if (!fileExists(engine)) {
                        error("OneScript не найден по пути ${engine}.")
                    }
                    if (!fileExists(scriptPath)) {
                        error("Скрипт не найден по пути ${scriptPath}. Проверьте MECHEL_SOURCE и размещение файлов.")
                    }
                    echo "Режим ${sourceMode}. Скрипт ${scriptPath}."

                    withEnv(['MECHEL_TEST_INFOBASE=' + testBase, 'MECHEL_TEST_SCRIPT=' + scriptPath,
                             'MECHEL_OSCRIPT=' + engine]) {
                        withCredentials([string(credentialsId: params.TOKEN, variable: 'MECHEL_API_TOKEN')]) {
                            bat encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_TEST_SCRIPT%"
'''
                        }
                    }
                }
            }
        }
    }
}
