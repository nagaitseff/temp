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

def runEnterpriseScript(String action, String title) {
    def logPath = pwd() + '/' + env.MECHEL_FIRST_START_LOG
    withEnv(['MECHEL_ENTERPRISE_ACTION=' + action, 'MECHEL_ENTERPRISE_LOG=' + logPath]) {
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

boolean isBranchNameValid(String branch) {
    branch != 'HEAD' && !branch.startsWith('-') && !branch.endsWith('.') && !branch.contains('..') &&
        !branch.contains('@{') && !(branch =~ /[\x00-\x20\x7f$~^:?*\[\\]/) &&
        branch.split('/', -1).every { part ->
            part && !part.startsWith('.') && !part.endsWith('.lock')
        }
}

pipeline {
    agent { label "${params.LABEL ?: 'erp_nightly'}" }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        disableRestartFromStage()
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'develop', description: 'Ветка ERP с обработкой fm_release.epf')
        string(name: 'TARGET_INFOBASE', defaultValue: '', description: 'Уже восстановленная тестовая ИБ. Формат СЕРВЕР:ПОРТ|ИмяИБ')
        booleanParam(name: 'CONFIRM_TARGET', defaultValue: false, description: 'Подтверждаю блокировку и запуск обработки в указанной базе')
        string(name: 'ENTERPRISE_USER', defaultValue: 'DataExchange', description: 'Пользователь для входа в указанную ИБ')
        credentials(
            name: 'ENTERPRISE_PASSWORD',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-dataexchange-password',
            required: true,
            description: 'Secret Text с действующим паролем пользователя в указанной ИБ'
        )
        booleanParam(name: 'VERBOSE_LOG', defaultValue: true, description: 'Вывести подробный лог Mechel')
        credentials(
            name: 'TOKEN',
            credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl',
            defaultValue: 'erp-restore-api-token',
            required: true,
            description: 'Secret Text с токеном оркестратора'
        )
        string(name: 'LABEL', defaultValue: 'erp_nightly', description: 'Windows-агент Jenkins')
    }

    environment {
        MECHEL_OSCRIPT = 'C:\\OneScript_2\\bin\\oscript.exe'
        MECHEL_LOCK_SCRIPT = 'C:\\OneScript_2\\api\\БлокировкаИБ.os'
        MECHEL_ENTERPRISE_SCRIPT = 'C:\\OneScript_2\\api\\ЗапуститьОбработкуИБ.os'
        MECHEL_1C_EXE = 'C:\\Program Files\\1cv8\\8.3.27.2342\\bin\\1cv8.exe'
        MECHEL_EPF_FILE = 'tools/OS/fm_release.epf'
        MECHEL_SESSION_PERMISSION_CODE = '789789789'
        MECHEL_ERP_DIR = 'erp-source'
        GIT_CREDENTIALS_ID = 'JenkinsReader'
    }

    stages {
        stage('Проверка параметров и адреса базы') {
            steps {
                script {
                    env.MECHEL_SESSION_LOCK_ATTEMPTED = '0'
                    def target = params.TARGET_INFOBASE?.trim()
                    def branch = params.BRANCH_NAME?.trim() ?: 'develop'
                    def user = params.ENTERPRISE_USER?.trim()
                    if (!target || !params.CONFIRM_TARGET) {
                        error('Укажите тестовую TARGET_INFOBASE и отметьте CONFIRM_TARGET.')
                    }
                    if (!isBranchNameValid(branch)) {
                        error('Укажите корректное имя ветки в BRANCH_NAME.')
                    }
                    if (!user || !params.TOKEN || !params.ENTERPRISE_PASSWORD) {
                        error('Укажите ENTERPRISE_USER и выберите Secret Text в TOKEN и ENTERPRISE_PASSWORD.')
                    }
                    if (!fileExists(env.MECHEL_OSCRIPT) || !fileExists(env.MECHEL_LOCK_SCRIPT) ||
                        !fileExists(env.MECHEL_ENTERPRISE_SCRIPT) || !fileExists('C:\\OneScript_2\\api\\ЗащитаПриемника.os')) {
                        error('Проверьте OneScript и наличие БлокировкаИБ.os, ЗапуститьОбработкуИБ.os и ЗащитаПриемника.os на агенте.')
                    }
                    env.MECHEL_RESTORE_TARGET = target
                    env.MECHEL_ENTERPRISE_USER = user
                    env.MECHEL_LOG_LEVEL = params.VERBOSE_LOG ? 'DEBUG' : 'INFO'
                    env.MECHEL_ERP_BRANCH = branch
                    env.MECHEL_FIRST_START_LOG = "mechel-epf/${env.BUILD_NUMBER}/fm-release.log"
                    currentBuild.description = "Проверка EPF в ${target}"
                }
            }
        }

        stage('Получение обработки из репозитория') {
            steps {
                script {
                    def branch = env.MECHEL_ERP_BRANCH
                    def branchRef = 'refs/heads/' + branch
                    dir(env.MECHEL_ERP_DIR) {
                        def revision = checkout(
                            changelog: false,
                            poll: false,
                            scm: [
                                $class: 'GitSCM',
                                branches: [[name: branchRef]],
                                extensions: [
                                    [$class: 'CloneOption', shallow: true, depth: 1, noTags: true, honorRefspec: true],
                                    [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: env.MECHEL_EPF_FILE]]]
                                ],
                                userRemoteConfigs: [[
                                    name: 'origin',
                                    url: 'http://UKM-SAPP115/mechel/erp.git',
                                    credentialsId: env.GIT_CREDENTIALS_ID,
                                    refspec: '+' + branchRef + ':refs/remotes/origin/' + branch
                                ]]
                            ]
                        )
                        echo "Обработка ${env.MECHEL_EPF_FILE} из ветки ${branch}, коммит ${revision.GIT_COMMIT}."
                    }
                    runEnterpriseScript('check', 'Проверить адрес базы, клиент 1С и файл EPF')
                }
            }
        }

        stage('Блокировка базы') {
            steps {
                script {
                    def start = new Date()
                    def end = new Date(start.time + 30 * 60 * 1000)
                    def format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                    withEnv(['MECHEL_SESSION_LOCK_BEGIN=' + format.format(start), 'MECHEL_SESSION_LOCK_END=' + format.format(end)]) {
                        env.MECHEL_SESSION_LOCK_ATTEMPTED = '1'
                        runLockScript('lock', 'Запретить новые сеансы и установить код разрешения входа')
                    }
                }
            }
        }

        stage('Запуск обработки fm_release.epf') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    runEnterpriseScript('run', 'Выполнить обработку в 1С:Предприятии')
                }
            }
            post {
                cleanup {
                    script {
                        runEnterpriseScript('cleanup', 'Удалить временную копию EPF и файлы запуска 1С')
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (env.MECHEL_FIRST_START_LOG && fileExists(env.MECHEL_FIRST_START_LOG)) {
                    archiveArtifacts artifacts: env.MECHEL_FIRST_START_LOG
                }
            }
        }
        cleanup {
            script {
                if (env.MECHEL_SESSION_LOCK_ATTEMPTED == '1') {
                    runLockScript('unlock', 'Снять блокировку базы')
                }
            }
        }
    }
}
