def runRestoreScript(String action, String title) {
    def restoreUser = action == 'create' ? env.MECHEL_ENTERPRISE_USER : ''
    withEnv(['MECHEL_RESTORE_ACTION=' + action, 'MECHEL_RESTORE_USER=' + restoreUser,
             'MECHEL_RESTORE_OSNAME=', 'MECHEL_ENTERPRISE_PASSWORD=']) {
        def run = {
            bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_RESTORE_SCRIPT%" "%MECHEL_RESTORE_ACTION%"
'''
        }
        if (action == 'check') {
            run()
        } else {
            def bindings = [string(credentialsId: params.TOKEN, variable: 'MECHEL_API_TOKEN')]
            if (env.MECHEL_RESTORE_MODE == 'restore') {
                bindings.add(string(credentialsId: params.ENTERPRISE_PASSWORD, variable: 'MECHEL_ENTERPRISE_PASSWORD'))
            }
            withCredentials(bindings) {
                run()
            }
        }
    }
}

def runEnterpriseScript(String action, String title) {
    def logPath = pwd() + '/' + env.MECHEL_FIRST_START_LOG
    withEnv(['MECHEL_ENTERPRISE_ACTION=' + action, 'MECHEL_ENTERPRISE_LOG=' + logPath]) {
        withCredentials([string(credentialsId: params.ENTERPRISE_PASSWORD, variable: 'MECHEL_ENTERPRISE_PASSWORD')]) {
            dir(env.MECHEL_ERP_DIR) {
                bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_ENTERPRISE_SCRIPT%" "%MECHEL_ENTERPRISE_ACTION%"
'''
            }
        }
    }
}

boolean isBranchNameValid(String branch) {
    branch != 'HEAD' && !branch.startsWith('-') && !branch.endsWith('.') && !branch.contains('..') &&
        !branch.contains('@{') && !(branch =~ /[\x00-\x20\x7f$~^:?*\[\\]/) &&
        branch.split('/', -1).every { part ->
            part && !part.startsWith('.') && !part.endsWith('.lock')
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
        string(name: 'BRANCH_NAME', defaultValue: '', description: 'Ветка ERP, например hotfix/2.5.27.107. Если пусто, используется develop')
        string(name: 'SOURCE_INFOBASE', defaultValue: '', description: 'База-источник. Имя ИБ или СЕРВЕР:ПОРТ|ИмяИБ')
        booleanParam(name: 'MAKE_COPY', defaultValue: false, description: 'Снять актуальную копию источника. Без флажка используется доступная копия')
        string(name: 'TARGET_INFOBASE', defaultValue: '', description: 'ВНИМАНИЕ. Содержимое этой базы будет заменено. Формат СЕРВЕР:ПОРТ|ИмяИБ')
        booleanParam(name: 'CONFIRM_TARGET', defaultValue: false, description: 'Проверили источник и приёмник? Подтверждаю замену содержимого TARGET_INFOBASE')
        string(name: 'ENTERPRISE_USER', defaultValue: 'DataExchange', description: 'Пользователь 1С для запуска обработки. Оркестратор установит ему пароль при восстановлении')
        credentials(
            name: 'ENTERPRISE_PASSWORD',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-dataexchange-password',
            required: false,
            description: 'Secret Text с новым паролем для ENTERPRISE_USER. Нужен в режиме restore'
        )
        booleanParam(name: 'VERBOSE_LOG', defaultValue: false, description: 'Вывести подробный лог Mechel (DEBUG)')
        credentials(
            name: 'TOKEN',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-restore-api-token',
            required: true,
            description: 'Secret Text с токеном оркестратора'
        )
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
        choice(name: 'RESTORE_MODE', choices: ['restore', 'wait'],
               description: 'restore — восстановить копию в приёмник, wait — проверить существующую заявку')
        string(name: 'RESTORE_GUID', defaultValue: '', description: 'GUID ранее созданной заявки для режима wait')
        string(name: 'WAIT_TIMEOUT_MINUTES', defaultValue: '120', description: 'Максимальное время опроса в минутах')
    }

    environment {
        MECHEL_OSCRIPT = 'C:\\OneScript_2\\bin\\oscript.exe'
        MECHEL_RESTORE_SCRIPT = 'C:\\OneScript_2\\api\\ВосстановитьИБ.os'
        MECHEL_ENTERPRISE_SCRIPT = 'C:\\OneScript_2\\api\\ЗапуститьОбработкуИБ.os'
        MECHEL_1C_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\1cv8.exe'
        MECHEL_EPF_FILE = 'tools/OS/fm_release.epf'
        MECHEL_ERP_DIR = 'erp-source'
        GIT_CREDENTIALS_ID = 'JenkinsReader'
    }

    stages {
        stage('Проверка параметров') {
            steps {
                script {
                    def mode = params.RESTORE_MODE ?: 'restore'
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
                    env.MECHEL_LOG_LEVEL = params.VERBOSE_LOG ? 'DEBUG' : 'INFO'
                    if (mode == 'wait') {
                        if (!(guid ==~ /(?i)[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}/)) {
                            error('В режиме wait укажите GUID существующей заявки в RESTORE_GUID.')
                        }
                        currentBuild.description = "Опрос заявки ${guid}"
                    } else {
                        if (mode != 'restore' || guid) {
                            error('Для новой заявки выберите restore и оставьте RESTORE_GUID пустым.')
                        }
                        def source = params.SOURCE_INFOBASE?.trim()
                        def target = params.TARGET_INFOBASE?.trim()
                        def branch = params.BRANCH_NAME?.trim() ?: 'develop'
                        def enterpriseUser = params.ENTERPRISE_USER?.trim()
                        if (!source || !target || source.equalsIgnoreCase(target)) {
                            error('Укажите разные SOURCE_INFOBASE и TARGET_INFOBASE.')
                        }
                        if (!isBranchNameValid(branch)) {
                            error('Укажите корректное имя ветки в BRANCH_NAME, например hotfix/2.5.27.107.')
                        }
                        if (!enterpriseUser || !params.ENTERPRISE_PASSWORD) {
                            error('Укажите ENTERPRISE_USER и выберите Secret Text в ENTERPRISE_PASSWORD.')
                        }
                        if (!fileExists(env.MECHEL_ENTERPRISE_SCRIPT)) {
                            error('Скопируйте ЗапуститьОбработкуИБ.os в C:\\OneScript_2\\api на агенте.')
                        }
                        env.MECHEL_RESTORE_SOURCE = source
                        env.MECHEL_RESTORE_TARGET = target
                        runRestoreScript('check', 'Проверка источника и защиты базы-приёмника')
                        if (!params.CONFIRM_TARGET) {
                            error('Сверьте направление восстановления в логе и отметьте CONFIRM_TARGET.')
                        }
                        env.MECHEL_RESTORE_MAKE_COPY = params.MAKE_COPY ? '1' : '0'
                        env.MECHEL_ENTERPRISE_USER = enterpriseUser
                        env.MECHEL_ERP_BRANCH = branch
                        env.MECHEL_RESTORE_GUID_FILE = "mechel-restore/${env.BUILD_NUMBER}/restore-guid.txt"
                        env.MECHEL_FIRST_START_LOG = "mechel-restore/${env.BUILD_NUMBER}/fm-release.log"
                        currentBuild.description = "${mode}: ${source} → ${target}"
                    }
                    env.MECHEL_RESTORE_GUID = guid
                    echo "Режим ${mode}. Скрипт ${env.MECHEL_RESTORE_SCRIPT}."
                }
            }
        }

        stage('Обновление репозитория') {
            when {
                expression { env.MECHEL_RESTORE_MODE == 'restore' }
            }
            steps {
                script {
                    def branch = env.MECHEL_ERP_BRANCH
                    def remoteRef = 'refs/remotes/origin/' + branch
                    echo "Получаем ветку ERP ${branch} в ${env.MECHEL_ERP_DIR}."
                    dir(env.MECHEL_ERP_DIR) {
                        def revision = checkout(
                            changelog: false,
                            poll: false,
                            scm: [
                                $class: 'GitSCM',
                                branches: [[name: 'refs/heads/' + branch]],
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [
                                    [$class: 'CloneOption', timeout: 5400, honorRefspec: true],
                                    [$class: 'CheckoutOption', timeout: 5400]
                                ],
                                userRemoteConfigs: [[
                                    name: 'origin',
                                    url: 'http://UKM-SAPP115/mechel/erp.git',
                                    credentialsId: env.GIT_CREDENTIALS_ID,
                                    refspec: '+refs/heads/' + branch + ':' + remoteRef
                                ]]
                            ]
                        )
                        env.MECHEL_ERP_COMMIT = revision.GIT_COMMIT
                        echo "Ветка ${branch}, commit ${env.MECHEL_ERP_COMMIT}."
                    }
                    runEnterpriseScript('check', 'Проверка адреса ИБ, платформы 1С, обработки и параметров входа')
                }
            }
        }

        stage('Создание заявки на восстановление базы') {
            when {
                expression { env.MECHEL_RESTORE_MODE == 'restore' }
            }
            steps {
                script {
                    runRestoreScript('create', 'Отправить заявку в оркестратор')
                    env.MECHEL_RESTORE_GUID = readFile(file: env.MECHEL_RESTORE_GUID_FILE, encoding: 'UTF-8').trim()
                    currentBuild.description += " | ${env.MECHEL_RESTORE_GUID}"
                }
            }
        }

        stage('Восстановление базы') {
            steps {
                script {
                    runRestoreScript('wait', 'Опросить статус восстановления')
                    env.MECHEL_RESTORE_COMPLETE = '1'
                }
            }
        }

        stage('Создание пользователей Аналитик и Разработчик') {
            when {
                expression { env.MECHEL_RESTORE_MODE == 'restore' }
            }
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                script {
                    runEnterpriseScript('run', 'Выполнить fm_release.epf в 1С:Предприятии')
                }
            }
            post {
                unsuccessful {
                    echo 'Восстановление завершено. Проверьте запуск 1С:Предприятия и лог fm-release.log.'
                }
            }
        }
    }

    post {
        always {
            script {
                def files = [env.MECHEL_RESTORE_GUID_FILE, env.MECHEL_FIRST_START_LOG].findAll { it && fileExists(it) }
                if (files) {
                    archiveArtifacts artifacts: files.join(',')
                }
            }
        }
        unsuccessful {
            script {
                if (env.MECHEL_RESTORE_GUID && env.MECHEL_RESTORE_COMPLETE != '1') {
                    echo "Проверить заявку можно в режиме wait с RESTORE_GUID=${env.MECHEL_RESTORE_GUID}."
                }
            }
        }
    }
}
