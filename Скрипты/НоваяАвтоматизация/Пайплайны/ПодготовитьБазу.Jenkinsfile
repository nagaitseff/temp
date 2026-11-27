def runRestoreScript(String action, String title) {
    withEnv(['MECHEL_RESTORE_ACTION=' + action]) {
        withCredentials([string(credentialsId: params.TOKEN, variable: 'MECHEL_API_TOKEN')]) {
            bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_RESTORE_SCRIPT%" "%MECHEL_RESTORE_ACTION%"
'''
        }
    }
}

pipeline {
    agent { label "${params.LABEL}" }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        disableRestartFromStage()
    }

    parameters {
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
        credentials(
            name: 'TOKEN',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-restore-api-token',
            required: true,
            description: 'Secret Text с токеном оркестратора'
        )
        choice(name: 'RESTORE_MODE', choices: ['test', 'restore', 'wait'],
               description: 'test — эмуляция; restore — перезаписать приёмник; wait — опросить существующий GUID')
        string(name: 'SOURCE_INFOBASE', defaultValue: '', description: 'Источник, имя ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        string(name: 'TARGET_INFOBASE', defaultValue: '', description: 'Существующий приёмник, имя ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        booleanParam(name: 'MAKE_COPY', defaultValue: false, description: 'Создать свежую копию источника перед восстановлением')
        string(name: 'RESTORE_USER', defaultValue: '', description: 'Существующий пользователь ИБ для настройки доступа при восстановлении, необязательно')
        string(name: 'RESTORE_OSNAME', defaultValue: '', description: 'Учётная запись ОС для RESTORE_USER, необязательно')
        string(name: 'RESTORE_GUID', defaultValue: '', description: 'GUID ранее созданной заявки для режима wait')
        string(name: 'WAIT_TIMEOUT_MINUTES', defaultValue: '120', description: 'Максимальное время опроса в минутах')
    }

    environment {
        MECHEL_OSCRIPT = 'C:\\OneScript_2\\bin\\oscript.exe'
        MECHEL_RESTORE_SCRIPT = 'C:\\OneScript_2\\api\\ВосстановитьИБ.os'
    }

    stages {
        stage('Проверить параметры') {
            steps {
                script {
                    def mode = params.RESTORE_MODE ?: 'test'
                    def guid = params.RESTORE_GUID?.trim() ?: ''
                    def waitMinutes = params.WAIT_TIMEOUT_MINUTES?.trim()
                    if (!params.TOKEN) {
                        error('Выберите Secret Text в параметре TOKEN.')
                    }
                    if (!waitMinutes || !(waitMinutes ==~ /[1-9][0-9]*/) || !waitMinutes.isInteger()) {
                        error('WAIT_TIMEOUT_MINUTES должен быть положительным целым числом.')
                    }
                    if (!fileExists(env.MECHEL_OSCRIPT) || !fileExists(env.MECHEL_RESTORE_SCRIPT)) {
                        error('Проверьте наличие OneScript и ВосстановитьИБ.os на агенте в C:\\OneScript_2.')
                    }

                    env.MECHEL_RESTORE_MODE = mode
                    env.MECHEL_RESTORE_WAIT_MINUTES = waitMinutes
                    if (mode == 'wait') {
                        if (!(guid ==~ /(?i)[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}/)) {
                            error('В режиме wait укажите GUID существующей заявки в RESTORE_GUID.')
                        }
                        currentBuild.description = "Опрос заявки ${guid}"
                    } else {
                        if (!(mode in ['test', 'restore']) || guid) {
                            error('Для новой заявки выберите test или restore и оставьте RESTORE_GUID пустым.')
                        }
                        def source = params.SOURCE_INFOBASE?.trim()
                        def target = params.TARGET_INFOBASE?.trim()
                        def ibUser = params.RESTORE_USER?.trim() ?: ''
                        def osUser = params.RESTORE_OSNAME?.trim() ?: ''
                        if (!source || !target || source.equalsIgnoreCase(target)) {
                            error('Укажите разные SOURCE_INFOBASE и TARGET_INFOBASE.')
                        }
                        if (osUser && !ibUser) {
                            error('При заданном RESTORE_OSNAME заполните RESTORE_USER.')
                        }
                        env.MECHEL_RESTORE_SOURCE = source
                        env.MECHEL_RESTORE_TARGET = target
                        env.MECHEL_RESTORE_MAKE_COPY = params.MAKE_COPY ? '1' : '0'
                        env.MECHEL_RESTORE_USER = ibUser
                        env.MECHEL_RESTORE_OSNAME = osUser
                        env.MECHEL_RESTORE_GUID_FILE = "mechel-restore/${env.BUILD_NUMBER}/restore-guid.txt"
                        currentBuild.description = "${mode}: ${source} → ${target}"
                    }
                    env.MECHEL_RESTORE_GUID = guid
                    echo "Режим ${mode}. Скрипт ${env.MECHEL_RESTORE_SCRIPT}."
                }
            }
        }

        stage('Создать заявку') {
            when {
                expression { env.MECHEL_RESTORE_MODE != 'wait' }
            }
            steps {
                script {
                    runRestoreScript('create', 'Отправить заявку в оркестратор')
                    env.MECHEL_RESTORE_GUID = readFile(file: env.MECHEL_RESTORE_GUID_FILE, encoding: 'UTF-8').trim()
                    currentBuild.description += " | ${env.MECHEL_RESTORE_GUID}"
                }
            }
        }

        stage('Дождаться результата') {
            steps {
                script {
                    runRestoreScript('wait', 'Опросить статус восстановления')
                }
            }
        }
    }

    post {
        always {
            script {
                def guidFile = env.MECHEL_RESTORE_GUID_FILE
                if (guidFile && fileExists(guidFile)) {
                    archiveArtifacts artifacts: guidFile
                }
            }
        }
        unsuccessful {
            script {
                if (env.MECHEL_RESTORE_GUID) {
                    echo "Проверить заявку можно в режиме wait с RESTORE_GUID=${env.MECHEL_RESTORE_GUID}."
                }
            }
        }
    }
}
