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
            if (env.MECHEL_RESTORE_MODE == 'restore' && (action == 'create' || action == 'wait')) {
                bindings.add(string(credentialsId: params.ENTERPRISE_PASSWORD, variable: 'MECHEL_ENTERPRISE_PASSWORD'))
            }
            withCredentials(bindings) {
                run()
            }
        }
    }
}

def runLockScript(String action, String title) {
    withEnv(['MECHEL_LOCK_ACTION=' + action]) {
        withCredentials([string(credentialsId: params.TOKEN, variable: 'MECHEL_API_TOKEN')]) {
            bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_LOCK_SCRIPT%" "%MECHEL_LOCK_ACTION%"
'''
        }
    }
}

def runBuildScript(String action, String title) {
    def outputDir = pwd() + '/' + env.MECHEL_RUN_DIR
    withEnv(['MECHEL_BUILD_ACTION=' + action, 'MECHEL_CONFIG_DIR=' + outputDir]) {
        dir(env.MECHEL_ERP_DIR) {
            bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_BUILD_SCRIPT%" "%MECHEL_BUILD_ACTION%"
'''
        }
    }
}

def removeBuildFiles() {
    def runDir = env.MECHEL_RUN_DIR
    if (!runDir || !env.MECHEL_BUILD_SELECTION || !fileExists(runDir)) {
        return
    }
    dir(runDir) {
        bat label: 'Удалить файлы сборки 1С', encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
setlocal
set "cleanupFailed=0"
for %%F in (1Cv8.cf Mechel.cfe) do (
    if exist "%%F" (
        if not exist "%%F\\" del /f /q "%%F"
        if exist "%%F" (
            echo Не удалось удалить %%F
            set "cleanupFailed=1"
        ) else (
            echo Удалён %%F
        )
    )
)
exit /b %cleanupFailed%
'''
    }
}

def runEnterpriseScript(String action, String title, String logFile = 'fm-release.log') {
    def outputDir = pwd() + '/' + env.MECHEL_RUN_DIR
    withEnv(['MECHEL_ENTERPRISE_ACTION=' + action, 'MECHEL_CONFIG_DIR=' + outputDir,
             'MECHEL_ENTERPRISE_LOG=' + outputDir + '/' + logFile]) {
        def run = {
            def invoke = {
                bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_ENTERPRISE_SCRIPT%" "%MECHEL_ENTERPRISE_ACTION%"
'''
            }
            if (action == 'check' || action == 'run') {
                dir(env.MECHEL_ERP_DIR) { invoke() }
            } else {
                invoke()
            }
        }
        if (action == 'cleanup') {
            run()
        } else {
            withCredentials([string(credentialsId: params.ENTERPRISE_PASSWORD, variable: 'MECHEL_ENTERPRISE_PASSWORD')]) {
                run()
            }
        }
    }
}

def runClientStep(String action, String title, String logFile) {
    try {
        runEnterpriseScript(action, title, logFile)
    } finally {
        runEnterpriseScript('cleanup', 'Удаление временных файлов запуска 1С', logFile)
    }
}

def resolveToolkitFiles() {
    def repositoryDir = ['plugins', 'НоваяАвтоматизация/plugins'].find {
        fileExists(it + '/НастроитьInfostartToolkit.epf')
    }
    def toolkitDir = repositoryDir ? pwd() + '/' + repositoryDir : 'C:\\OneScript_2\\api\\plugins'
    env.MECHEL_TOOLKIT_EPF = toolkitDir + '/НастроитьInfostartToolkit.epf'
    env.MECHEL_TOOLKIT_CFE = toolkitDir + '/InfostartToolkitCORP.cfe'
    if (!fileExists(env.MECHEL_TOOLKIT_EPF)) {
        error('Для настройки Toolkit положите НастроитьInfostartToolkit.epf и InfostartToolkitCORP.cfe в C:\\OneScript_2\\api\\plugins.')
    }
}

def updateErpCheckout() {
    def branch = env.MECHEL_ERP_BRANCH
    def remoteRef = 'refs/remotes/origin/' + branch
    dir(env.MECHEL_ERP_DIR) {
        def operation = fileExists('.git') ? 'Обновление сохранённого клона' : 'Первичное получение репозитория'
        echo "${operation}. Ветка ${branch}, каталог ${pwd()}."
        def revision = checkout(
            changelog: false,
            poll: false,
            scm: [
                $class: 'GitSCM',
                branches: [[name: remoteRef]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [
                    [$class: 'CloneOption', timeout: 5400, noTags: true, honorRefspec: true],
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
        string(name: 'BRANCH_NAME', defaultValue: 'develop',
               description: 'Ветка ERP, из которой берутся fm_release.epf и исходники для выбранной сборки. Укажите, например, develop или hotfix/2.5.27.107. Пустое поле означает develop.')
        booleanParam(name: 'RESTORE_COPY', defaultValue: false,
                     description: 'Включите, чтобы перед подготовкой заменить содержимое TARGET_INFOBASE копией SOURCE_INFOBASE. Для работы с уже существующей базой оставьте флажок снятым.')
        string(name: 'SOURCE_INFOBASE', defaultValue: '',
               description: 'Источник данных при RESTORE_COPY. Например, UKM-SAPP150:1541|ERP_KGOK. Однозначное имя ERP_KGOK тоже допустимо. Для подготовки без восстановления оставьте поле пустым.')
        booleanParam(name: 'DIFF_COPY', defaultValue: false,
                     description: 'При включённом RESTORE_COPY запросить свежую копию SOURCE_INFOBASE на момент выполнения заявки. С продуктивной базы оркестратор снимет временную дифференциальную копию, с других источников полную. При снятом DIFF_COPY используется уже доступная копия.')
        string(name: 'TARGET_INFOBASE', defaultValue: '',
               description: 'Тестовая база, в которой выполняются выбранные действия. При RESTORE_COPY её содержимое будет заменено. Укажите полный адрес, например UKM-SAPP145:6541|ERP_DEV_NAGAYTSEV.')
        booleanParam(name: 'CONFIRM_TARGET', defaultValue: false,
                     description: 'Обязательно при RESTORE_COPY. Сверьте SOURCE_INFOBASE и TARGET_INFOBASE и отметьте флажок, чтобы подтвердить замену содержимого тестовой базы.')
        booleanParam(name: 'INSTALL_CF', defaultValue: true,
                     description: 'Собрать 1Cv8.cf из выбранной ветки и обновить основную конфигурацию TARGET_INFOBASE. Снимите, если нужна только установка расширения Mechel.')
        booleanParam(name: 'INSTALL_MECHEL', defaultValue: true,
                     description: 'Собрать Mechel.cfe из выбранной ветки, загрузить расширение и применить его после проверки. Снимите, если нужно установить только 1Cv8.cf.')
        booleanParam(name: 'SETUP_TOOLKIT', defaultValue: true,
                     description: 'Установить Infostart Toolkit при его отсутствии и назначить роль «Полные права» Toolkit пользователям DataExchange, Разработчик и Аналитик. Уже установленная версия используется повторно.')
        booleanParam(name: 'RUN_MONOPOLY_UPDATE', defaultValue: true,
                     description: 'После выбранных установок запустить в 1С:Предприятии штатное монопольное обновление данных ERP. Например, включите после установки CF и Mechel. Снимите, если хотите выполнить обновление отдельно.')
        string(name: 'ENTERPRISE_USER', defaultValue: 'DataExchange',
               description: 'Имя пользователя внутри TARGET_INFOBASE с правами на выбранные операции. Например, DataExchange. При RESTORE_COPY оркестратор установит пароль этому пользователю.')
        credentials(
            name: 'ENTERPRISE_PASSWORD',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-dataexchange-password',
            required: false,
            description: 'Выберите Jenkins Secret Text с паролем ENTERPRISE_USER, например erp-dataexchange-password. При RESTORE_COPY этот пароль устанавливается оркестратором. Для существующей базы нужен действующий пароль. В режиме wait поле не требуется.'
        )
        booleanParam(name: 'VERBOSE_LOG', defaultValue: false,
                     description: 'Включите для диагностики сборки и запуска 1С, чтобы видеть подробные сообщения Mechel уровня DEBUG. Для обычного прогона достаточно INFO.')
        credentials(
            name: 'TOKEN',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-restore-api-token',
            required: true,
            description: 'Выберите Jenkins Secret Text с токеном оркестратора. Например, erp-restore-api-token. Он нужен для восстановления, опроса заявки и управления блокировкой тестовой базы.'
        )
        string(name: 'LABEL', defaultValue: 'erp_nightly',
               description: 'Метка Windows-агента Jenkins с установленными OneScript и 1С. Например, erp_nightly. С той же меткой и на том же агенте переиспользуется существующий клон ERP.')
        choice(name: 'RESTORE_MODE', choices: ['prepare', 'wait'],
               description: 'Выберите prepare для подготовки базы с выбранными флажками. Выберите wait, чтобы только дождаться результата уже созданной заявки по RESTORE_GUID.')
        string(name: 'RESTORE_GUID', defaultValue: '',
               description: 'Только для wait. Скопируйте GUID из журнала предыдущего запуска или файла restore-guid.txt. Пример формата 11111111-2222-4333-8444-555555555555. Для prepare оставьте пустым.')
        string(name: 'WAIT_TIMEOUT_MINUTES', defaultValue: '120',
               description: 'Сколько минут Jenkins ждёт результата при RESTORE_COPY или в режиме wait. Укажите целое положительное число, например 120.')
    }

    environment {
        MECHEL_OSCRIPT = 'C:\\OneScript_2\\bin\\oscript.exe'
        MECHEL_RESTORE_SCRIPT = 'C:\\OneScript_2\\api\\ВосстановитьИБ.os'
        MECHEL_LOCK_SCRIPT = 'C:\\OneScript_2\\api\\БлокировкаИБ.os'
        MECHEL_BUILD_SCRIPT = 'C:\\OneScript_2\\api\\СобратьКонфигурациюИБ.os'
        MECHEL_ENTERPRISE_SCRIPT = 'C:\\OneScript_2\\api\\ЗапуститьОбработкуИБ.os'
        MECHEL_1C_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\1cv8.exe'
        MECHEL_IBCMD_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\ibcmd.exe'
        MECHEL_EPF_FILE = 'tools/OS/fm_release.epf'
        MECHEL_UPDATE_EPF = 'C:\\OneScript_2\\lib\\vanessa-runner\\epf\\ЗакрытьПредприятие.epf'
        MECHEL_ERP_DIR = 'erp-source'
        MECHEL_SESSION_PERMISSION_CODE = '789789789'
        GIT_CREDENTIALS_ID = 'JenkinsReader'
    }

    stages {
        stage('Проверка параметров') {
            steps {
                script {
                    // Для настроек, сохранённых прежним Jenkinsfile.
                    def mode = params.RESTORE_MODE == 'restore' ? 'prepare' : (params.RESTORE_MODE ?: 'prepare')
                    def restoreCopy = mode == 'prepare' && params.RESTORE_COPY
                    def guid = params.RESTORE_GUID?.trim() ?: ''
                    def waitMinutes = params.WAIT_TIMEOUT_MINUTES?.trim()
                    env.MECHEL_SESSION_LOCK_ATTEMPTED = '0'
                    env.MECHEL_RESTORE_MODE = restoreCopy ? 'restore' : mode
                    env.MECHEL_LOG_LEVEL = params.VERBOSE_LOG ? 'DEBUG' : 'INFO'
                    if (!params.TOKEN) {
                        error('Выберите Secret Text в параметре TOKEN.')
                    }
                    if ((mode == 'wait' || restoreCopy) &&
                        (!waitMinutes || !(waitMinutes ==~ /[1-9][0-9]*/) || !waitMinutes.isInteger())) {
                        error('WAIT_TIMEOUT_MINUTES должен быть положительным целым числом.')
                    }
                    if (!fileExists(env.MECHEL_OSCRIPT)) {
                        error('Проверьте наличие OneScript на агенте в C:\\OneScript_2.')
                    }
                    if ((mode == 'wait' || restoreCopy) && !fileExists(env.MECHEL_RESTORE_SCRIPT)) {
                        error('Проверьте наличие ВосстановитьИБ.os на агенте.')
                    }

                    env.MECHEL_RESTORE_WAIT_MINUTES = waitMinutes ?: ''
                    env.MECHEL_RESTORE_GUID = guid
                    if (mode == 'wait') {
                        if (!(guid ==~ /(?i)[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}/)) {
                            error('В режиме wait укажите GUID существующей заявки в RESTORE_GUID.')
                        }
                        currentBuild.description = "Опрос заявки ${guid}"
                        return
                    }
                    if (mode != 'prepare' || guid) {
                        error('Для подготовки базы выберите prepare и оставьте RESTORE_GUID пустым.')
                    }

                    def installCF = params.INSTALL_CF != false
                    def installMechel = params.INSTALL_MECHEL != false
                    def setupToolkit = params.SETUP_TOOLKIT != false
                    def update = params.RUN_MONOPOLY_UPDATE != false
                    def build = installCF || installMechel
                    def target = params.TARGET_INFOBASE?.trim()
                    def branch = params.BRANCH_NAME?.trim() ?: 'develop'
                    def enterpriseUser = params.ENTERPRISE_USER?.trim()
                    if (!target) {
                        error('Укажите полный адрес тестовой базы в TARGET_INFOBASE.')
                    }
                    if (restoreCopy && !params.CONFIRM_TARGET) {
                        error('При RESTORE_COPY сверьте SOURCE_INFOBASE и TARGET_INFOBASE и отметьте CONFIRM_TARGET.')
                    }
                    if (!isBranchNameValid(branch)) {
                        error('Укажите корректное имя ветки в BRANCH_NAME, например hotfix/2.5.27.107.')
                    }
                    if (!enterpriseUser || !params.ENTERPRISE_PASSWORD) {
                        error('Укажите ENTERPRISE_USER и выберите Secret Text в ENTERPRISE_PASSWORD.')
                    }
                    if (!fileExists(env.MECHEL_ENTERPRISE_SCRIPT) || !fileExists(env.MECHEL_LOCK_SCRIPT)) {
                        error('Проверьте ЗапуститьОбработкуИБ.os и БлокировкаИБ.os в C:\\OneScript_2\\api на агенте.')
                    }
                    if (build && (!fileExists(env.MECHEL_BUILD_SCRIPT) || !fileExists(env.MECHEL_IBCMD_EXE))) {
                        error('Для сборки нужны СобратьКонфигурациюИБ.os и ibcmd.exe 8.3.27.2342 на агенте.')
                    }
                    if (update && !fileExists(env.MECHEL_UPDATE_EPF)) {
                        error('Для монопольного обновления нужна обработка vanessa-runner ЗакрытьПредприятие.epf на агенте.')
                    }
                    if (setupToolkit) {
                        resolveToolkitFiles()
                    }

                    env.MECHEL_RESTORE_TARGET = target
                    env.MECHEL_ENTERPRISE_USER = enterpriseUser
                    env.MECHEL_ERP_BRANCH = branch
                    env.MECHEL_BUILD_SELECTION = installCF ? (installMechel ? 'both' : 'cf') : (installMechel ? 'cfe' : '')
                    env.MECHEL_RUN_DIR = "mechel-restore/${env.BUILD_NUMBER}"
                    currentBuild.description = "Подготовка ${target}"
                    if (restoreCopy) {
                        def source = params.SOURCE_INFOBASE?.trim()
                        if (!source || source.equalsIgnoreCase(target)) {
                            error('Укажите разные SOURCE_INFOBASE и TARGET_INFOBASE.')
                        }
                        env.MECHEL_RESTORE_SOURCE = source
                        def freshCopy = params.DIFF_COPY == true
                        env.MECHEL_RESTORE_MAKE_COPY = freshCopy ? '1' : '0'
                        env.MECHEL_RESTORE_GUID_FILE = "${env.MECHEL_RUN_DIR}/restore-guid.txt"
                        currentBuild.description = "${source} → ${target}"
                        runRestoreScript('check', 'Проверка источника и защиты базы-приёмника')
                        echo(freshCopy ? 'Для восстановления будет запрошена свежая копия источника.' :
                                         'Для восстановления используется доступная копия источника.')
                    } else {
                        echo "Подготовка существующей базы ${target} без восстановления."
                    }
                    runEnterpriseScript('check-login', 'Проверить адрес базы, клиент 1С и параметры входа')
                }
            }
        }

        stage('Обновление репозитория') {
            when {
                expression { env.MECHEL_RESTORE_MODE != 'wait' }
            }
            steps {
                script {
                    updateErpCheckout()
                    runEnterpriseScript('check', 'Проверить fm_release.epf из выбранной ветки')
                }
            }
        }

        stage('Сборка выбранных файлов 1С') {
            when {
                expression { env.MECHEL_RESTORE_MODE != 'wait' && env.MECHEL_BUILD_SELECTION }
            }
            steps {
                script {
                    runBuildScript('build', 'Собрать выбранные файлы из XML')
                }
            }
            post {
                cleanup {
                    script {
                        runBuildScript('cleanup', 'Удалить временные файловые базы сборки')
                    }
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
            when {
                expression { env.MECHEL_RESTORE_MODE in ['restore', 'wait'] }
            }
            steps {
                script {
                    runRestoreScript('wait', 'Опросить статус восстановления')
                    env.MECHEL_RESTORE_COMPLETE = '1'
                }
            }
        }

        stage('Подготовка базы-приёмника') {
            when {
                expression { env.MECHEL_RESTORE_MODE != 'wait' }
            }
            options {
                timeout(time: 165, unit: 'MINUTES')
            }
            stages {
                stage('Блокировка базы-приёмника') {
                    steps {
                        script {
                            def start = new Date()
                            def end = new Date(start.time + 3 * 60 * 60 * 1000)
                            def format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                            withEnv(['MECHEL_SESSION_LOCK_BEGIN=' + format.format(start),
                                     'MECHEL_SESSION_LOCK_END=' + format.format(end)]) {
                                env.MECHEL_SESSION_LOCK_ATTEMPTED = '1'
                                runLockScript('lock', 'Запретить новые сеансы и установить код разрешения входа')
                            }
                        }
                    }
                }

                stage('Создание пользователей Аналитик и Разработчик') {
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
                            echo 'Проверьте лог выполнения обработки fm-release.log.'
                        }
                        cleanup {
                            script {
                                runEnterpriseScript('cleanup', 'Удалить временную копию EPF и файлы запуска')
                            }
                        }
                    }
                }

                stage('Установка основной конфигурации') {
                    when {
                        expression { env.MECHEL_BUILD_SELECTION in ['cf', 'both'] }
                    }
                    steps {
                        script {
                            runClientStep('apply-cf', 'Загрузить 1Cv8.cf и монопольно обновить конфигурацию базы', 'apply-cf.log')
                        }
                    }
                }

                stage('Установка расширения Mechel') {
                    when {
                        expression { env.MECHEL_BUILD_SELECTION in ['cfe', 'both'] }
                    }
                    steps {
                        script {
                            runClientStep('load-cfe', 'Загрузить Mechel.cfe', 'load-mechel.log')
                            runClientStep('check-cfe', 'Проверить применимость Mechel', 'check-mechel.log')
                            runClientStep('apply-cfe', 'Применить расширение Mechel', 'apply-mechel.log')
                        }
                    }
                }

                stage('Установка Infostart Toolkit') {
                    when {
                        expression { params.SETUP_TOOLKIT != false }
                    }
                    steps {
                        script {
                            runClientStep('install-toolkit', 'Проверить наличие и при необходимости установить Toolkit', 'install-toolkit.log')
                        }
                    }
                }

                stage('Монопольное обновление ERP') {
                    when {
                        expression { params.RUN_MONOPOLY_UPDATE != false }
                    }
                    steps {
                        script {
                            runClientStep('update', 'Запустить обработчики обновления данных ERP', 'update-erp.log')
                        }
                    }
                }

                stage('Права Infostart Toolkit') {
                    when {
                        expression { params.SETUP_TOOLKIT != false }
                    }
                    steps {
                        script {
                            runClientStep('toolkit-roles', 'Назначить полную роль Toolkit указанным пользователям', 'toolkit-roles.log')
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (env.MECHEL_RUN_DIR) {
                    archiveArtifacts artifacts: "${env.MECHEL_RUN_DIR}/restore-guid.txt,${env.MECHEL_RUN_DIR}/*.log",
                                     allowEmptyArchive: true
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
        cleanup {
            script {
                try {
                    removeBuildFiles()
                } finally {
                    if (env.MECHEL_SESSION_LOCK_ATTEMPTED == '1') {
                        runLockScript('unlock', 'Снять блокировку базы-приёмника')
                    }
                }
            }
        }
    }
}
