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

def runEnterpriseScript(String action, String title, String logFile = 'fm-release.log') {
    def outputDir = pwd() + '/' + env.MECHEL_RUN_DIR
    withEnv(['MECHEL_ENTERPRISE_ACTION=' + action, 'MECHEL_CONFIG_DIR=' + outputDir,
             'MECHEL_ENTERPRISE_LOG=' + outputDir + '/' + logFile]) {
        def run = {
            dir(env.MECHEL_ERP_DIR) {
                bat label: title, encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
"%MECHEL_OSCRIPT%" -encoding=utf-8 "%MECHEL_ENTERPRISE_SCRIPT%" "%MECHEL_ENTERPRISE_ACTION%"
'''
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

def runDesignerStep(String action, String title, String logFile) {
    try {
        runEnterpriseScript(action, title, logFile)
    } finally {
        runEnterpriseScript('cleanup', 'Удаление временных файлов конфигуратора', logFile)
    }
}

def updateErpCheckout() {
    def branch = env.MECHEL_ERP_BRANCH
    def remoteRef = 'refs/remotes/origin/' + branch
    def previousGit = pwd() + '\\erp-source\\.git'
    if (fileExists(previousGit + '\\HEAD') && !fileExists(env.MECHEL_ERP_DIR + '/.git')) {
        withEnv(['MECHEL_GIT_SEED=' + previousGit, 'MECHEL_GIT_CACHE=' + env.MECHEL_ERP_DIR.replace('/', '\\')]) {
            bat label: 'Переиспользовать Git-объекты из прежнего клона', encoding: 'UTF-8', script: '''@echo off
chcp 65001 >nul
robocopy "%MECHEL_GIT_SEED%" "%MECHEL_GIT_CACHE%\\.git-seed" /E /XJ /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
if errorlevel 8 exit /b 1
if not exist "%MECHEL_GIT_CACHE%\\.git-seed\\HEAD" exit /b 1
attrib -h -s "%MECHEL_GIT_CACHE%\\.git-seed"
if errorlevel 1 exit /b 1
ren "%MECHEL_GIT_CACHE%\\.git-seed" ".git"
'''
        }
    }
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
        string(name: 'BRANCH_NAME', defaultValue: '', description: 'Ветка ERP для сборки и установки CF и Mechel. Пусто — восстановление и запуск EPF из develop')
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
        MECHEL_LOCK_SCRIPT = 'C:\\OneScript_2\\api\\БлокировкаИБ.os'
        MECHEL_BUILD_SCRIPT = 'C:\\OneScript_2\\api\\СобратьКонфигурациюИБ.os'
        MECHEL_ENTERPRISE_SCRIPT = 'C:\\OneScript_2\\api\\ЗапуститьОбработкуИБ.os'
        MECHEL_1C_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\1cv8.exe'
        MECHEL_IBCMD_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\ibcmd.exe'
        MECHEL_EPF_FILE = 'tools/OS/fm_release.epf'
        MECHEL_SESSION_PERMISSION_CODE = '789789789'
        GIT_CREDENTIALS_ID = 'JenkinsReader'
    }

    stages {
        stage('Проверка параметров') {
            steps {
                script {
                    def mode = params.RESTORE_MODE ?: 'restore'
                    def guid = params.RESTORE_GUID?.trim() ?: ''
                    def waitMinutes = params.WAIT_TIMEOUT_MINUTES?.trim()
                    env.MECHEL_SESSION_LOCK_ATTEMPTED = '0'
                    env.MECHEL_BUILD_CONFIGURATION = '0'
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
                        def requestedBranch = params.BRANCH_NAME?.trim()
                        def branch = requestedBranch ?: 'develop'
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
                        if (!fileExists(env.MECHEL_ENTERPRISE_SCRIPT) || !fileExists(env.MECHEL_LOCK_SCRIPT)) {
                            error('Проверьте ЗапуститьОбработкуИБ.os и БлокировкаИБ.os в C:\\OneScript_2\\api на агенте.')
                        }
                        if (requestedBranch && (!fileExists(env.MECHEL_BUILD_SCRIPT) || !fileExists(env.MECHEL_IBCMD_EXE))) {
                            error('Для сборки нужны СобратьКонфигурациюИБ.os и ibcmd.exe 8.3.27.2342 на агенте.')
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
                        def workspacePath = pwd().replace('\\', '/')
                        def separator = workspacePath.lastIndexOf('/')
                        env.MECHEL_ERP_DIR = workspacePath.substring(0, separator) + '/.mechel-erp/' +
                            workspacePath.substring(separator + 1)
                        env.MECHEL_BUILD_CONFIGURATION = requestedBranch ? '1' : '0'
                        env.MECHEL_RUN_DIR = "mechel-restore/${env.BUILD_NUMBER}"
                        env.MECHEL_RESTORE_GUID_FILE = "${env.MECHEL_RUN_DIR}/restore-guid.txt"
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
                    updateErpCheckout()
                    runEnterpriseScript('check', 'Проверка адреса ИБ, платформы 1С, обработки и параметров входа')
                }
            }
        }

        stage('Сборка 1Cv8.cf и Mechel.cfe') {
            when {
                expression { env.MECHEL_BUILD_CONFIGURATION == '1' }
            }
            steps {
                script {
                    runBuildScript('build', 'Собрать основную конфигурацию и расширение из XML')
                    archiveArtifacts artifacts: "${env.MECHEL_RUN_DIR}/1Cv8.cf,${env.MECHEL_RUN_DIR}/Mechel.cfe"
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
            steps {
                script {
                    runRestoreScript('wait', 'Опросить статус восстановления')
                    env.MECHEL_RESTORE_COMPLETE = '1'
                }
            }
        }

        stage('Подготовка восстановленной базы') {
            when {
                expression { env.MECHEL_RESTORE_MODE == 'restore' }
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
                            echo 'Восстановление завершено. Проверьте запуск 1С:Предприятия и лог fm-release.log.'
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
                        expression { env.MECHEL_BUILD_CONFIGURATION == '1' }
                    }
                    steps {
                        script {
                            runDesignerStep('apply-cf', 'Загрузить 1Cv8.cf и монопольно обновить конфигурацию базы', 'apply-cf.log')
                        }
                    }
                }

                stage('Установка расширения Mechel') {
                    when {
                        expression { env.MECHEL_BUILD_CONFIGURATION == '1' }
                    }
                    steps {
                        script {
                            runDesignerStep('load-cfe', 'Загрузить Mechel.cfe', 'load-mechel.log')
                            runDesignerStep('check-cfe', 'Проверить применимость Mechel', 'check-mechel.log')
                            runDesignerStep('apply-cfe', 'Применить расширение Mechel', 'apply-mechel.log')
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
                if (env.MECHEL_SESSION_LOCK_ATTEMPTED == '1') {
                    runLockScript('unlock', 'Снять блокировку базы-приёмника')
                }
            }
        }
    }
}
